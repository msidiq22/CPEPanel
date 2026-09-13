package com.cpepanel.cpe

import com.cpepanel.cpe.J.i
import com.cpepanel.cpe.J.s
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class UbusError(
    val kind: Kind,
    val code: Int,
    override val message: String
) : Exception(message) {
    enum class Kind { TRANSPORT, DENIED, NOT_SUPPORTED, SERVER, PARSE }

    val needsLogin: Boolean get() = kind == Kind.DENIED
}

/**
 * 中兴 CPE 的 ubus JSON-RPC 客户端。
 *
 * 三个必须照做的细节（缺一不可，否则设备直接 307 拒绝）：
 *  1. 请求头 Referer 必须以 http(s)://<设备地址> 开头 —— 这是厂商级的同源校验；
 *  2. body 必须是【数组包裹】的单元素 JSON-RPC： [{...}]；
 *  3. session 字段：未登录用 32 个 0，登录后用 web_login 下发的 ubus_rpc_session。
 *
 * Z-Mode / Z-Tag 设备不强制校验，但为了和官方前端一致仍然带上。
 */
class UbusClient(baseUrl: String = DEFAULT_BASE) {

    companion object {
        const val DEFAULT_BASE = "http://192.168.0.1"
        const val ZERO_SESSION = "00000000000000000000000000000000"
        private const val UA = "Mozilla/5.0 (Linux; Android 14) CpePanel/1.0"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    @Volatile
    var baseUrl: String = normalize(baseUrl)
        private set

    @Volatile
    var session: String = ZERO_SESSION
        private set

    @Volatile
    var isLoggedIn: Boolean = false
        private set

    /** 会话 AES 密钥（仅在开启会话加密协商后存在） */
    @Volatile
    var aesKeyHex: String? = null
        private set

    /** 是否在登录后做 web_crt_get + web_http_enstr_set 协商 */
    @Volatile
    var negotiateSessionCrypto: Boolean = false

    private val callId = AtomicInteger(1)

    /**
     * 设备在 `web_login_info` / `web_login` 的响应里会下发 `webtoken` Cookie（HttpOnly）。
     *
     * 【这是整个登录链路最容易被忽略的一环】：
     * 只带 `params[0]` 的 session id 是不够的，后续所有鉴权接口还必须同时带上这个 Cookie，
     * 否则一律返回 `-32002 Access denied` —— 表现为"登录明明成功了，
     * 但立刻被判定会话失效"。实测对比：
     *   不带 Cookie → web_login 返回 session，但 web_info 立刻 -32002
     *   带   Cookie → web_info status=0
     *
     * OkHttp 默认是 CookieJar.NO_COOKIES，所以必须自己装一个。
     */
    private val cookieStore = HashMap<String, List<Cookie>>()

    /** 拿到（或刷新）webtoken 时回调，便于持久化到本地 */
    @Volatile
    var onWebToken: ((String) -> Unit)? = null

    /**
     * 【全局请求串行锁】—— 这是登录后偶发 -32002 的根治手段。
     *
     * 设备对每个请求都会【轮换】webtoken，并且只认最新那一个。如果两个请求同时在飞，
     * 后发出的那个会作废先发出的那个，先到的那次就被判 -32002。实测：
     *   串行 6 次 → 全部成功；并发 6 次用同一 token → 全部失败。
     *
     * OkHttp 的 CookieJar 只能在「请求发出的那一刻」取到当前最新 cookie，
     * 它无法阻止两个请求同时带着同一个旧 token 起飞。所以必须在这一层
     * 把 in-flight 请求数压到 1 —— 锁覆盖「发请求 → 收响应体」全程，
     * 这样下一次请求一定是在上一个响应（及新 token）落袋之后才构造的。
     */
    private val ioLock = java.util.concurrent.locks.ReentrantLock()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            cookieStore[url.host] = cookies
            cookies.firstOrNull { it.name == "webtoken" }?.let { onWebToken?.invoke(it.value) }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host].orEmpty()
    }

    /** 启动时把上次保存的 webtoken 灌回 CookieJar，这样会话才能跨进程存活 */
    fun seedWebToken(value: String) {
        if (value.isBlank()) return
        val host = baseUrl.toHttpUrlOrNull()?.host ?: return
        cookieStore[host] = listOf(
            Cookie.Builder().name("webtoken").value(value).domain(host).path("/").build()
        )
    }

    /** 从响应头里直接取出当前 webtoken（用于持久化） */
    fun currentWebToken(): String? =
        cookieStore.values.flatten().firstOrNull { it.name == "webtoken" }?.value

    /** 彻底丢弃设备 Cookie。只在「用户主动退出登录」或「切换设备地址」时调用，
     *  日常的 -32002 恢复【不能】清，否则会陷入"刚登录就被判失效"的死循环。 */
    fun clearCookies() {
        cookieStore.clear()
    }

    /** 设备 443 用的是自签名证书，直连时不做链校验（仅用于本地 LAN 设备） */
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun trustAllContext(): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAllManager), null) }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // 必须保存 webtoken Cookie，否则登录后所有鉴权接口都会 -32002
        .cookieJar(cookieJar)
        // 关键：不要跟随重定向，否则 307 会被 OkHttp 悄悄转到 https 端口，掩盖真实错误
        .followRedirects(false)
        .followSslRedirects(false)
        .sslSocketFactory(trustAllContext().socketFactory, trustAllManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    fun setBaseUrl(raw: String) {
        baseUrl = normalize(raw)
        // 换了地址就该丢掉旧设备的 Cookie
        cookieStore.clear()
        resetSession()
    }

    /**
     * 清空会话状态。注意【不】清 Cookie：
     * webtoken 是设备侧认客户端的凭据，会在每次 web_login_info 时刷新，
     * 保留它才能在偶发 -32002 后自动恢复。
     */
    fun resetSession() {
        session = ZERO_SESSION
        isLoggedIn = false
        aesKeyHex = null
    }

    fun restoreSession(token: String) {
        if (token.length == 32) {
            session = token
            isLoggedIn = true
        }
    }

    // ────────────────────────────── 调用 ──────────────────────────────

    /**
     * 调用一个 ubus 方法。成功返回 result[1]（对象），失败抛 [UbusError]。
     */
    fun call(
        obj: String,
        method: String,
        args: JSONObject = JSONObject(),
        withSession: Boolean = true
    ): JSONObject {
        val sid = if (withSession && isLoggedIn) session else ZERO_SESSION

        val frame = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", callId.getAndIncrement())
            .put("method", "call")
            .put("params", JSONArray().put(sid).put(obj).put(method).put(args))

        val body = JSONArray().put(frame).toString()
        val url = "$baseUrl/ubus/?t=${System.currentTimeMillis()}"

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Referer", "$baseUrl/index.html")
            .header("Origin", baseUrl)
            .header("Z-Mode", if (isLoggedIn) "1" else "0")
            .header("Z-Tag", "")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", UA)
            .build()

        val text = try {
            // 串行执行：拿到响应（含新 webtoken）后才允许下一个请求起飞
            ioLock.lock()
            try {
                http.newCall(request).execute().use { resp ->
                    if (resp.code == 307 || resp.code == 301 || resp.code == 302) {
                        throw UbusError(
                            UbusError.Kind.TRANSPORT, resp.code,
                            "设备拒绝了请求（HTTP ${resp.code}）。入口校验未通过：请确认地址就是 http://192.168.0.1，且手机已连上该 CPE 的 Wi-Fi。"
                        )
                    }
                    if (resp.code == 401 || resp.code == 403) {
                        throw UbusError(UbusError.Kind.DENIED, resp.code, "设备拒绝访问（HTTP ${resp.code}）")
                    }
                    resp.body?.string() ?: ""
                }
            } finally {
                ioLock.unlock()
            }
        } catch (e: UbusError) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw UbusError(UbusError.Kind.TRANSPORT, -1, "连接 $baseUrl 超时。请确认手机连的是这台 CPE 的 Wi-Fi，而不是流量或别的网络。")
        } catch (e: IOException) {
            throw UbusError(UbusError.Kind.TRANSPORT, -1, "无法连接 $baseUrl：${e.message ?: "网络错误"}")
        }

        return parse(text)
    }

    /** 调用但吞掉异常，返回 null（用于可选接口探测） */
    fun tryCall(
        obj: String,
        method: String,
        args: JSONObject = JSONObject(),
        withSession: Boolean = true
    ): JSONObject? = try {
        call(obj, method, args, withSession)
    } catch (t: Throwable) {
        null
    }

    private fun parse(text: String): JSONObject {
        if (text.isBlank()) {
            throw UbusError(UbusError.Kind.TRANSPORT, -2, "设备返回了空响应")
        }
        val root = try {
            JSONArray(text)
        } catch (t: Throwable) {
            throw UbusError(UbusError.Kind.PARSE, -3, "响应不是合法 JSON：${text.take(160)}")
        }
        val first = root.optJSONObject(0)
            ?: throw UbusError(UbusError.Kind.PARSE, -4, "响应结构异常（缺少首元素）")

        first.optJSONObject("error")?.let { err ->
            val code = err.optInt("code")
            val msg = err.optString("message", "unknown")
            throw when (code) {
                -32002 -> UbusError(
                    UbusError.Kind.DENIED, code,
                    if (isLoggedIn) "登录状态已失效（-32002），请重新登录" else "该接口需要先登录（-32002）"
                )
                -32000 -> UbusError(UbusError.Kind.NOT_SUPPORTED, code, "本机型不支持该接口（-32000）")
                else -> UbusError(UbusError.Kind.SERVER, code, "设备返回错误 $code：$msg")
            }
        }

        val result = first.optJSONArray("result")
            ?: throw UbusError(UbusError.Kind.PARSE, -5, "响应缺少 result 字段")

        // 设备用的是标准 ubus 状态码，别把 2 误当成"未登录"：
        //   0 OK / 1 INVALID_COMMAND / 2 INVALID_ARGUMENT / 3 METHOD_NOT_FOUND
        //   4 NOT_FOUND / 5 NO_DATA / 6 PERMISSION_DENIED
        // 实测 zwrt_data.get_wwandst 少了 source_module/cid/type 就会返回 2，
        // 那是参数问题，跟登录状态无关。
        when (val status = result.optInt(0, 0)) {
            0 -> Unit
            2 -> throw UbusError(UbusError.Kind.SERVER, status, "设备不接受该请求参数（status 2 INVALID_ARGUMENT）")
            3 -> throw UbusError(UbusError.Kind.NOT_SUPPORTED, status, "本机型没有该接口（status 3 METHOD_NOT_FOUND）")
            4, 5 -> throw UbusError(UbusError.Kind.SERVER, status, "设备暂无该数据（status $status）")
            6 -> throw UbusError(UbusError.Kind.DENIED, status, "设备拒绝：权限不足（status 6 PERMISSION_DENIED）")
            else -> throw UbusError(UbusError.Kind.SERVER, status, "设备返回 ubus 状态码 $status")
        }

        return when (val data = result.opt(1)) {
            is JSONObject -> data
            null -> JSONObject()
            else -> JSONObject().put("value", data)
        }
    }

    // ────────────────────────────── 登录 ──────────────────────────────

    data class LoginResult(
        val ok: Boolean,
        val message: String,
        val failLeft: Int = -1,
        val session: String = ""
    )

    /**
     * 完整登录流程：
     *   1. web_login_info  → 取本次登录的盐值 sault
     *   2. 计算 SHA256(SHA256(pwd) + sault)（大写）
     *   3. web_login       → 拿 ubus_rpc_session
     *   4. （可选）web_crt_get + web_http_enstr_set 协商会话加密
     */
    fun login(plainPassword: String): LoginResult {
        val info = call("zwrt_web", "web_login_info", JSONObject(), withSession = false)
        val sault = info.s("zte_web_sault")
        val failLeft = info.i("login_fail_num", -1)
        val lockLeft = info.i("login_fail_lock_lefttime", 0)

        if (sault.isBlank()) {
            return LoginResult(false, "设备没有返回登录盐值（zte_web_sault），无法计算口令")
        }
        if (lockLeft > 0) {
            return LoginResult(false, "设备当前锁定登录，还需等待 $lockLeft 秒", failLeft)
        }

        val hash = Crypto.loginHash(plainPassword, sault)
        val res = call("zwrt_web", "web_login", JSONObject().put("password", hash), withSession = false)

        val code = res.opt("result")?.toString() ?: ""
        val sid = res.s("ubus_rpc_session")
        android.util.Log.i(
            "CpePanel",
            "login: sault=$sault hash=$hash resp.result=$code sid=$sid " +
                "webToken=${currentWebToken()?.take(12)}… lock=$lockLeft fail=$failLeft"
        )

        if (code != "0" || sid.length != 32) {
            val left = if (failLeft > 0) failLeft - 1 else failLeft
            val extra = when {
                lockLeft > 0 -> "，设备已锁定"
                left > 0 -> "（设备侧剩余尝试次数约 $left）"
                else -> ""
            }
            return LoginResult(false, "密码不正确$extra", left)
        }

        session = sid
        isLoggedIn = true

        if (negotiateSessionCrypto) {
            // 协商失败不阻断登录：只读接口用不到会话加密
            try {
                val crt = call("zwrt_web", "web_crt_get").s("result")
                if (crt.startsWith("-----BEGIN")) {
                    val key = Crypto.randomHexLower(32)
                    call(
                        "zwrt_web", "web_http_enstr_set",
                        JSONObject().put("web_enstr", Crypto.rsaEncryptPem(crt, key))
                    )
                    aesKeyHex = key
                }
            } catch (t: Throwable) {
                aesKeyHex = null
            }
        }

        return LoginResult(true, "登录成功", failLeft, sid)
    }

    fun logout() {
        try {
            call("zwrt_web", "web_logout")
        } catch (t: Throwable) {
            // 忽略：本地状态照样清掉
        }
        resetSession()
    }

    /** 校验当前 token 是否还有效 */
    fun checkSessionAlive(): Boolean = try {
        call("zwrt_web", "web_info"); true
    } catch (t: Throwable) {
        false
    }

    // ────────────────────────── 会话字段解密 ──────────────────────────

    /** 是否已具备解密设备敏感字段的能力（web_crt_get + web_http_enstr_set 协商成功） */
    val sessionCryptoReady: Boolean get() = !aesKeyHex.isNullOrEmpty()

    /**
     * 解密设备返回的敏感字段（Wi-Fi 密钥、APN 口令等）。
     *
     * 设备侧格式为 base64( iv(16B) || AES-256-CBC(明文) )，密钥就是本次协商的会话密钥。
     * 另有一个坑：Wi-Fi 密钥这类字段，设备在加密【之前】又多做了一次 base64，
     * 所以解密后还要再 base64 解码一层才是真正的密码。
     */
    fun decryptField(encoded: String): String? {
        val key = aesKeyHex ?: return null
        if (encoded.isBlank()) return null
        val plain = Crypto.sessionDecrypt(key, encoded) ?: return null
        if (plain.isEmpty()) return null
        val unwrapped = try {
            String(android.util.Base64.decode(plain, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
        return unwrapped?.takeIf { s -> s.isNotEmpty() && s.all { it.code in 32..126 } } ?: plain
    }

    // ────────────────────────────── 工具 ──────────────────────────────

    private fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) s = DEFAULT_BASE
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        while (s.endsWith("/")) s = s.dropLast(1)
        return s
    }
}
