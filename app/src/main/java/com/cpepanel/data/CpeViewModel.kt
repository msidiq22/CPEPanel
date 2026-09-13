package com.cpepanel.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cpepanel.cpe.Api
import com.cpepanel.cpe.J.i
import com.cpepanel.cpe.J.f
import com.cpepanel.cpe.J.s
import com.cpepanel.cpe.J.writeAccepted
import com.cpepanel.cpe.SmsCodec
import com.cpepanel.cpe.UbusClient
import com.cpepanel.cpe.UbusError
import com.cpepanel.cpe.UbusMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ConnStatus { Idle, Connecting, Online, Offline }

data class SignalSample(val t: Long, val rsrp: Int, val snr: Float, val bar: Int)

data class CpeState(
    val baseUrl: String = UbusClient.DEFAULT_BASE,
    val status: ConnStatus = ConnStatus.Idle,
    val latencyMs: Long = -1L,
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    val lastUpdated: Long = 0L,
    val lastError: String? = null,
    val free: Map<String, JSONObject> = emptyMap(),
    val freeErrors: Map<String, String> = emptyMap(),
    val auth: Map<String, JSONObject> = emptyMap(),
    val authErrors: Map<String, String> = emptyMap(),
    val authProbed: Boolean = false,
    val samples: List<SignalSample> = emptyList(),
    val refreshMs: Long = 3000L,
    val negotiateCrypto: Boolean = false,
    /** 会话密钥协商是否就绪（就绪才能解开 Wi-Fi 密钥等敏感字段） */
    val cryptoReady: Boolean = false,
    /** section_name -> 明文 Wi-Fi 密码，例如 main_5g -> ******（仅在内存中，不落盘） */
    val wifiKeys: Map<String, String> = emptyMap(),
    val toast: String? = null
) {
    fun freeOf(m: UbusMethod): JSONObject? = free[m.key]
    fun authOf(m: UbusMethod): JSONObject? = auth[m.key]
    val net: JSONObject? get() = free[Api.NET_INFO.key]
    val routerStatus: JSONObject? get() = free[Api.WAN_STATUS.key]
    val simInfo: JSONObject? get() = free[Api.SIM_BRIEF.key]
    val wlan: JSONObject? get() = free[Api.WLAN_REPORT.key]
    val deviceInfo: JSONObject? get() = free[Api.DEVICE_INFO.key]

    // ── 登录后才有的对象，UI 直接引用，避免散落的字符串 key ──
    val routerStatusAuth: JSONObject? get() = auth[Api.ROUTER_STATUS.key]
    val wanIface: JSONObject? get() = auth[Api.WAN_IFACE.key]
    val wanModePara: JSONObject? get() = auth[Api.WAN_MODE_PARA.key]
    val trafficRt: JSONObject? get() = auth[Api.TRAFFIC_RT.key]
    val trafficMonth: JSONObject? get() = auth[Api.TRAFFIC_MONTH.key]
    val trafficLimit: JSONObject? get() = auth[Api.TRAFFIC_LIMIT.key]
    val trafficClearDay: JSONObject? get() = auth[Api.TRAFFIC_CLEARDAY.key]
    val userNum: JSONObject? get() = auth[Api.USER_NUM.key]
    val lanAccess: JSONObject? get() = auth[Api.LAN_ACCESS.key]
    val arpTable: JSONObject? get() = auth[Api.ARP_TABLE.key]
    val lanHostname: JSONObject? get() = auth[Api.LAN_HOSTNAME.key]
    val macPctrl: JSONObject? get() = auth[Api.MAC_PCTRL.key]
    val secondAuthSucc: JSONObject? get() = auth[Api.SECOND_AUTH_SUCC.key]
    val secondAuthFail: JSONObject? get() = auth[Api.SECOND_AUTH_FAIL.key]
    val ddns: JSONObject? get() = auth[Api.DDNS.key]
    val tunnel: JSONObject? get() = auth[Api.TUNNEL.key]

    val apnMode: JSONObject? get() = auth[Api.APN_MODE.key]
    val apnAuto: JSONObject? get() = auth[Api.APN_AUTO.key]
    val apnManu: JSONObject? get() = auth[Api.APN_MANU.key]
    val apnAtCid: JSONObject? get() = auth[Api.APN_AT_CID.key]

    val wlanIface: JSONObject? get() = auth[Api.WLAN_IFACE.key]
    val wlanAgents: JSONObject? get() = auth[Api.WLAN_AGENTS.key]
    val wlanWps: JSONObject? get() = auth[Api.WLAN_WPS.key]
    val wlanGuestTime: JSONObject? get() = auth[Api.WLAN_GUEST_TIME.key]
    val wlanIsolate: JSONObject? get() = auth[Api.WLAN_ISOLATE.key]

    val simDetail: JSONObject? get() = auth[Api.SIM_DETAIL.key]
    val simlockTrials: JSONObject? get() = auth[Api.SIMLOCK_TRIALS.key]

    val sysTime: JSONObject? get() = auth[Api.SYS_TIME.key]
    val sntpMode: JSONObject? get() = auth[Api.SNTP_MODE.key]
    val sntpSync: JSONObject? get() = auth[Api.SNTP_SYNC.key]
    val thermal: JSONObject? get() = auth[Api.THERMAL.key]
    val nightMode: JSONObject? get() = auth[Api.NIGHT_MODE.key]
    val sleepPara: JSONObject? get() = auth[Api.SLEEP_PARA.key]
    val smsCapacity: JSONObject? get() = auth[Api.SMS_CAPACITY.key]
    val smsParam: JSONObject? get() = auth[Api.SMS_PARAM.key]
    val webInfo: JSONObject? get() = auth[Api.WEB_INFO.key]
}

// ═══════════════════════════ 短信 ═══════════════════════════

/**
 * 设备侧短信 tag 语义（从前端 smslist.js 的分支逻辑反推 + 真机数据比对确认）：
 *   0 已读接收　1 未读接收　2 已发送　3 发送失败　4 草稿
 */
object SmsTag {
    const val IN_READ = 0
    const val IN_UNREAD = 1
    const val OUT_SENT = 2
    const val OUT_FAILED = 3
    const val DRAFT = 4
}

data class SmsMessage(
    val id: Int,
    val number: String,
    /** 已从 UTF-16BE 十六进制解码还原的正文 */
    val content: String,
    val dateRaw: String,
    val tag: Int
) {
    val outgoing: Boolean get() = tag == SmsTag.OUT_SENT || tag == SmsTag.OUT_FAILED
    val failed: Boolean get() = tag == SmsTag.OUT_FAILED
    val draft: Boolean get() = tag == SmsTag.DRAFT
    val unread: Boolean get() = tag == SmsTag.IN_UNREAD
    val timeText: String get() = SmsCodec.formatDate(dateRaw)
    val shortTime: String get() = SmsCodec.shortDate(dateRaw)
}

/** 一个号码下的全部往来短信（会话） */
data class SmsThread(val number: String, val messages: List<SmsMessage>) {
    /** messages 已按 id 倒序，第一条就是最新 */
    val latest: SmsMessage get() = messages.first()
    val count: Int get() = messages.size
    val unread: Int get() = messages.count { it.unread }
    val hasDraft: Boolean get() = messages.any { it.draft }
}

data class SmsUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val threads: List<SmsThread> = emptyList(),
    val capacity: JSONObject? = null,
    /** 短信服务是否就绪（get_cmd_status{sms_cmd:1} 返回 3） */
    val serviceReady: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null,
    val lastLoaded: Long = 0L,
    val toast: String? = null
) {
    val total: Int get() = threads.sumOf { it.count }
    val unread: Int get() = threads.sumOf { it.unread }
    val empty: Boolean get() = loaded && threads.isEmpty()
}

class CpeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    private val client = UbusClient(prefs.baseUrl).apply {
        negotiateSessionCrypto = prefs.negotiateCrypto
    }

    private val _state = MutableStateFlow(
        CpeState(
            baseUrl = prefs.baseUrl,
            refreshMs = prefs.refreshMs,
            negotiateCrypto = prefs.negotiateCrypto,
            loggedIn = prefs.sessionToken.length == 32
        )
    )
    val state: StateFlow<CpeState> = _state.asStateFlow()

    /**
     * 短信单独一条状态流。
     *
     * 为什么不塞进 [CpeState]：短信列表一次要返回上百条、正文几十 KB，
     * 而且它【只在用户打开短信页时才该被拉取】。放进主状态流会诱导它跟着轮询循环走，
     * 那台小 httpd 撑不住（实测把静态资源都打成过 502）。
     */
    private val _sms = MutableStateFlow(SmsUiState())
    val sms: StateFlow<SmsUiState> = _sms.asStateFlow()

    private var pollJob: Job? = null
    private var cycle = 0

    /**
     * 刷新互斥锁。轮询循环、手动刷新、登录后刷新、切换地址后刷新
     * 都可能同时想跑一次 refreshOnce —— 一旦两轮刷新并行，各自的请求会交错着
     * 带着同一个旧 webtoken 发出，被设备轮换机制互相踩掉，表现为登录后偶发 -32002。
     * 这里让刷新周期本身也串行，彻底消除并行窗口。
     */
    private val refreshMutex = Mutex()

    /** web_info 连续被拒的周期数（用于区分「真掉线」和「偶发抖动」） */
    private var webInfoDeniedStrikes = 0

    init {
        // webtoken 必须在任何请求之前灌回去，否则恢复出来的 session 会被设备判为无效
        client.onWebToken = { prefs.webToken = it }
        val wt = prefs.webToken
        if (wt.isNotBlank()) client.seedWebToken(wt)
        val tok = prefs.sessionToken
        if (tok.length == 32) client.restoreSession(tok)
        startPolling()
    }

    // ─────────────────────────── 轮询 ───────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        // refreshMs <= 0 表示「关闭自动刷新」：此时不应再启动轮询循环。
        // 之前这里把间隔无条件 coerceAtLeast(1000)，导致选「关闭」后仍按每秒轮询。
        if (_state.value.refreshMs <= 0L) {
            pollJob = null
            return
        }
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                val ms = _state.value.refreshMs
                if (ms <= 0L) break          // 轮询中被切换为「关闭」
                delay(ms.coerceAtLeast(500L))
            }
        }
    }

    /** 手动刷新一次（不受自动刷新开关影响）。 */
    fun refreshNow() {
        viewModelScope.launch { refreshOnce(forceSlow = true) }
    }

    /**
     * 刷新入口：先取互斥锁，保证任何时刻只有一个刷新周期在跑。
     * 这样请求之间永远不会交错，每个请求发出时带的都是当时最新的 webtoken
     * —— 设备每响应一次就轮换一次 webtoken 且只认最新值，交错发请求会互相踩掉。
     */
    private suspend fun refreshOnce(forceSlow: Boolean = false): Unit =
        refreshMutex.withLock { refreshPass(forceSlow) }

    /**
     * 一次刷新周期，分层轮询（接口总数已扩到 50+，必须分层，否则打挂设备上那台小 httpd）：
     *  - tier0：每周期都打（信号 / WAN / 实时速率 / 终端数）
     *  - tier1：每 T1_EVERY 个周期（本月流量、终端明细、Wi-Fi 接口表…）
     *  - tier2：每 T2_EVERY 个周期（温控 / APN / VPN / DDNS / 短信容量…基本不变）
     * 未到节奏的接口【保留上一轮结果】不清空，UI 不会闪空。
     */
    private suspend fun refreshPass(forceSlow: Boolean) = withContext(Dispatchers.IO) {
        cycle += 1
        val wantT1 = forceSlow || cycle == 1 || cycle % T1_EVERY == 0
        val wantT2 = forceSlow || cycle == 1 || cycle % T2_EVERY == 0
        // 按刷新分层的节奏决定本周期该打哪些接口：
        // tier0 每周期、tier1 每 T1_EVERY 周期、tier2 每 T2_EVERY 周期。
        // 全量接口已有 50+ 个，若每周期都打会把这台小 httpd 打挂。
        fun due(m: UbusMethod) = when (m.tier) {
            0 -> true
            1 -> wantT1
            else -> wantT2
        }
        val prev = _state.value

        val free = LinkedHashMap(prev.free)
        val freeErr = LinkedHashMap(prev.freeErrors)
        var latency = -1L
        var transportFailed = false
        var transportMsg: String? = null

        for (m in Api.FREE) {
            if (!due(m)) continue
            val t0 = System.currentTimeMillis()
            try {
                free[m.key] = client.call(m.obj, m.name, m.args())
                freeErr.remove(m.key)
                if (m.key == Api.NET_INFO.key) latency = System.currentTimeMillis() - t0
            } catch (e: UbusError) {
                if (e.kind == UbusError.Kind.TRANSPORT) {
                    if (m.key == Api.NET_INFO.key) {
                        transportFailed = true
                        transportMsg = e.message
                    }
                } else {
                    free.remove(m.key)
                    freeErr[m.key] = e.message
                }
            } catch (t: Throwable) {
                free.remove(m.key)
                freeErr[m.key] = t.message ?: "未知错误"
            }
        }

        // 网络层面就不通，没必要继续打登录接口
        if (transportFailed && free.isEmpty()) {
            _state.update {
                it.copy(
                    status = ConnStatus.Offline,
                    lastError = transportMsg,
                    latencyMs = -1,
                    busy = false
                )
            }
            return@withContext
        }

        val auth = LinkedHashMap(prev.auth)
        val authErr = LinkedHashMap(prev.authErrors)
        var webInfoDenied = false
        var webInfoRan = false
        var authOkCount = 0
        var authTried = 0

        if (client.isLoggedIn) {
            for (m in Api.AUTH) {
                if (!due(m)) continue          // 本轮节奏之外的接口：保留上一轮结果，不打
                if (m.key == Api.WEB_INFO.key) webInfoRan = true
                authTried += 1
                try {
                    auth[m.key] = client.call(m.obj, m.name, m.args())
                    authErr.remove(m.key)
                    authOkCount += 1
                } catch (e: UbusError) {
                    auth.remove(m.key)
                    authErr[m.key] = e.message
                    android.util.Log.w("CpePanel", "auth FAIL ${m.key} kind=${e.kind} code=${e.code} msg=${e.message}")
                    if (m.key == Api.WEB_INFO.key && e.needsLogin) webInfoDenied = true
                } catch (t: Throwable) {
                    auth.remove(m.key)
                    authErr[m.key] = t.message ?: "未知错误"
                    android.util.Log.w("CpePanel", "auth EXC ${m.key} ${t}")
                }
            }
        }

        /*
         * 会话失效判定要保守，别被一次抖动清掉登录态：
         *  - 必须 web_info 明确返回 -32002；且
         *  - 本轮【其它鉴权接口也全都失败】（只要有一个成功，说明会话还活着，
         *    那就是并发窗口里的偶发 -32002）；且
         *  - 连续 2 个周期都如此，才认为真的掉线。
         */
        // 只有 web_info 本轮【真的打了】才更新 strikes，否则保持上一轮的值。
        // 否则 web_info 每 T1_EVERY 个周期才跑一次，strikes 会被中间那些周期反复清零，
        // 「连续 2 个周期」永远凑不齐。
        if (webInfoRan) {
            if (webInfoDenied && authOkCount == 0) {
                webInfoDeniedStrikes += 1
            } else {
                webInfoDeniedStrikes = 0
            }
        }
        if (webInfoDeniedStrikes >= 2) {
            val n = webInfoDeniedStrikes
            webInfoDeniedStrikes = 0
            android.util.Log.w("CpePanel", "sessionDead -> resetSession（连续 $n 个周期 web_info 判定会话失效，且其它鉴权接口全失败）")
            client.resetSession()
            prefs.sessionToken = ""
        } else if (webInfoDenied) {
            android.util.Log.w(
                "CpePanel",
                "web_info 返回 -32002，但本轮成功 $authOkCount 个鉴权接口 —— 判定为偶发抖动，保留会话（strike=$webInfoDeniedStrikes）"
            )
        }

        // 解开设备返回的 Wi-Fi 密钥密文（依赖登录 + 会话密钥协商两步都成功）
        val wifiKeys = LinkedHashMap<String, String>()
        if (client.isLoggedIn && client.sessionCryptoReady) {
            auth[Api.WLAN_KEY.key]?.optJSONArray("wlan_keys")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val section = o.s("section_name")
                    if (section.isBlank()) continue
                    client.decryptField(o.s("key_encode"))?.let { wifiKeys[section] = it }
                }
            }
        }

        val sample = free[Api.NET_INFO.key]?.let { net ->
            val nrRsrp = net.i("nr5g_rsrp", 0)
            val lteRsrp = net.i("lte_rsrp", 0)
            val rsrp = if (nrRsrp != 0) nrRsrp else lteRsrp
            val snr = net.f("nr5g_snr", 0f)
            val bar = net.i("signalbar", 0)
            if (rsrp == 0 && snr == 0f && bar == 0) null
            else SignalSample(System.currentTimeMillis(), rsrp, snr, bar)
        }

        _state.update {
            it.copy(
                status = if (transportFailed) ConnStatus.Offline else ConnStatus.Online,
                latencyMs = latency,
                lastError = if (transportFailed) transportMsg else null,
                free = free,
                freeErrors = freeErr,
                auth = auth,
                authErrors = authErr,
                authProbed = client.isLoggedIn,
                loggedIn = client.isLoggedIn,
                cryptoReady = client.sessionCryptoReady,
                wifiKeys = wifiKeys,
                samples = if (sample != null) (it.samples + sample).takeLast(240) else it.samples,
                lastUpdated = System.currentTimeMillis(),
                busy = false
            )
        }
    }

    // ─────────────────────────── 登录 ───────────────────────────

    fun login(password: String) {
        if (password.isEmpty()) {
            _state.update { it.copy(toast = "请输入管理密码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val res = withContext(Dispatchers.IO) {
                try {
                    client.login(password)
                } catch (t: Throwable) {
                    UbusClient.LoginResult(false, t.message ?: "登录请求失败")
                }
            }
            if (res.ok) {
                prefs.sessionToken = res.session
                _state.update { it.copy(busy = false, loggedIn = true, toast = "登录成功") }
                refreshNow()
            } else {
                _state.update { it.copy(busy = false, toast = res.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                client.logout()
                client.clearCookies()
            }
            prefs.sessionToken = ""
            prefs.webToken = ""
            webInfoDeniedStrikes = 0
            _state.update {
                it.copy(
                    loggedIn = false,
                    auth = emptyMap(),
                    authErrors = emptyMap(),
                    authProbed = false,
                    cryptoReady = false,
                    wifiKeys = emptyMap(),
                    toast = "已退出登录"
                )
            }
        }
    }

    // ─────────────────────────── 设置 ───────────────────────────

    fun setBaseUrl(raw: String) {
        val normalized = if (raw.isBlank()) UbusClient.DEFAULT_BASE else raw.trim()
        prefs.baseUrl = normalized
        prefs.sessionToken = ""
        prefs.webToken = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) { client.setBaseUrl(normalized) }
            _state.update {
                it.copy(
                    baseUrl = client.baseUrl,
                    loggedIn = false,
                    free = emptyMap(),
                    freeErrors = emptyMap(),
                    auth = emptyMap(),
                    authErrors = emptyMap(),
                    authProbed = false,
                    cryptoReady = false,
                    wifiKeys = emptyMap(),
                    samples = emptyList(),
                    status = ConnStatus.Connecting,
                    toast = "地址已切换为 ${client.baseUrl}"
                )
            }
            refreshOnce(forceSlow = true)
        }
    }

    fun setRefreshMs(ms: Long) {
        prefs.refreshMs = ms
        _state.update { it.copy(refreshMs = ms) }
        startPolling()
    }

    fun setNegotiateCrypto(on: Boolean) {
        prefs.negotiateCrypto = on
        client.negotiateSessionCrypto = on
        _state.update { it.copy(negotiateCrypto = on, toast = if (on) "会话加密协商已开启（下次登录生效）" else "会话加密协商已关闭") }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    // ═══════════════════════════ 短信 ═══════════════════════════

    /**
     * 拉取短信列表与容量。
     *
     * 【只按需调用】：进入短信页、手动刷新、发完/删完后各一次。
     * 绝不能挂到轮询里 —— 一次返回上百条、几十 KB，设备那台小 httpd 扛不住。
     */
    fun loadSms() {
        if (!client.isLoggedIn) {
            _sms.update { it.copy(loading = false, error = "需要先登录管理后台才能读取短信", loaded = true) }
            return
        }
        viewModelScope.launch {
            _sms.update { it.copy(loading = true, error = null) }
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val cap = client.call(Api.SMS_CAPACITY.obj, Api.SMS_CAPACITY.name, Api.SMS_CAPACITY.args())
                    val list = client.call(Api.SMS_LIST.obj, Api.SMS_LIST.name, Api.SMS_LIST.args())
                    val nv = runCatching {
                        client.call(Api.SMS_NVITEMS.obj, Api.SMS_NVITEMS.name, Api.SMS_NVITEMS.args())
                    }.getOrNull()
                    Triple(cap, list, nv)
                }
            }
            res.onSuccess { (cap, list, nv) ->
                val msgs = parseSms(list)
                _sms.update {
                    it.copy(
                        loading = false,
                        loaded = true,
                        error = null,
                        threads = groupThreads(msgs),
                        capacity = cap,
                        // sms_init: 2 表示初始化失败，其余视为可用
                        serviceReady = nv?.opt("sms_init")?.toString() != "2",
                        lastLoaded = System.currentTimeMillis()
                    )
                }
            }.onFailure { t ->
                _sms.update { it.copy(loading = false, loaded = true, error = t.message ?: "读取短信失败") }
            }
        }
    }

    private fun parseSms(list: JSONObject): List<SmsMessage> {
        val arr = list.optJSONArray("messages") ?: return emptyList()
        val out = ArrayList<SmsMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += SmsMessage(
                id = o.i("id"),
                number = o.s("number"),
                content = SmsCodec.decodeBody(o.s("content")),
                dateRaw = o.s("date"),
                tag = o.i("tag")
            )
        }
        return out
    }

    /** 按号码聚成会话，会话之间按最新一条的 id 倒序 */
    private fun groupThreads(msgs: List<SmsMessage>): List<SmsThread> =
        msgs.groupBy { it.number }
            .map { (num, list) -> SmsThread(num, list.sortedByDescending { it.id }) }
            .sortedByDescending { it.latest.id }

    /**
     * 发送短信。完整复刻官方前端的流程：
     *   ① get_cmd_status{sms_cmd:1} 确认短信服务就绪
     *   ② send_sms（正文 UTF-16BE 十六进制 + encode_type）
     *   ③ 轮询 get_cmd_status{sms_cmd:4} 拿到真正的发送结果
     *
     * 刻意【不做自动重试】：重试意味着一次点击可能发出多条短信。
     */
    fun sendSms(number: String, text: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val num = number.trim()
        val body = text.trim()
        if (num.isEmpty()) {
            _sms.update { it.copy(toast = "请填写接收号码") }; onDone(false, "请填写接收号码"); return
        }
        if (body.isEmpty()) {
            _sms.update { it.copy(toast = "短信内容不能为空") }; onDone(false, "短信内容不能为空"); return
        }
        if (!client.isLoggedIn) {
            _sms.update { it.copy(toast = "需要先登录才能发送短信") }; onDone(false, "需要先登录"); return
        }
        viewModelScope.launch {
            _sms.update { it.copy(sending = true) }
            val r = withContext(Dispatchers.IO) { doSend(num, body) }
            _sms.update { it.copy(sending = false, toast = r.second) }
            onDone(r.first, r.second)
            if (r.first) loadSms()
        }
    }

    /** @return (是否成功, 提示语) */
    private suspend fun doSend(number: String, text: String): Pair<Boolean, String> {
        val ready = runCatching {
            client.call(
                Api.Write.SMS_CMD_STATUS.obj, Api.Write.SMS_CMD_STATUS.name,
                JSONObject().put("sms_cmd", 1)
            )
        }.getOrNull()
        when (ready?.opt("sms_cmd_status_result")?.toString()) {
            "3" -> Unit
            "1", "0", null -> return false to "短信服务尚未就绪，请稍后重试"
            else -> return false to "短信服务初始化失败，无法发送"
        }

        val args = JSONObject()
            .put("number", number)
            .put("sms_time", SmsCodec.currentTimeString())
            .put("message_body", SmsCodec.encodeBody(text))
            .put("id", "-1")
            .put("encode_type", SmsCodec.encodeType(text))
        val res = runCatching {
            client.call(Api.Write.SMS_SEND.obj, Api.Write.SMS_SEND.name, args)
        }.getOrElse { return false to (it.message ?: "发送请求失败") }
        // 真机契约：result=3 受理、2 拒绝（空号码/非法号码）。这里只信设备返回值。
        if (res.writeAccepted() == false) return false to "设备拒绝了发送请求，请检查号码"

        return when (pollCmd(4, 12, 900)) {
            true -> true to "短信已发送"
            false -> false to "发送失败：请检查号码或信号后重试"
            else -> true to "已提交发送（设备未及时回执，可在会话里确认）"
        }
    }

    /**
     * 轮询写操作结果。`sms_cmd_status_result`：3 = 成功，2 = 失败，其它 = 仍在进行。
     *
     * @return true 成功 / false 失败 / null 超时
     * 超时【不报失败】—— 设备已经接受了请求，此时谎报失败会让用户重复发送。
     */
    private suspend fun pollCmd(cmd: Int, tries: Int, gapMs: Long): Boolean? {
        repeat(tries) {
            delay(gapMs)
            val st = runCatching {
                client.call(
                    Api.Write.SMS_CMD_STATUS.obj, Api.Write.SMS_CMD_STATUS.name,
                    JSONObject().put("sms_cmd", cmd)
                )
            }.getOrNull()
            when (st?.opt("sms_cmd_status_result")?.toString()) {
                "3" -> return true
                "2" -> return false
            }
        }
        return null
    }

    /**
     * 删除短信。id 串必须拼成 `"3;5;9;"` 这种「分号连接且末尾带分号」的格式
     * —— 这是官方前端原样拼的，实测按此格式才被接受。
     */
    fun deleteSms(ids: List<Int>, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        if (ids.isEmpty()) return
        if (!client.isLoggedIn) { onDone(false, "需要先登录"); return }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val joined = ids.joinToString(";") + ";"
                val res = runCatching {
                    client.call(
                        Api.Write.SMS_DELETE.obj, Api.Write.SMS_DELETE.name,
                        JSONObject().put("id", joined)
                    )
                }.getOrElse { return@withContext false to (it.message ?: "删除失败") }
                if (res.writeAccepted() == false) {
                    return@withContext false to "设备拒绝了删除请求"
                }
                when (pollCmd(6, 8, 700)) {
                    false -> false to "删除失败"
                    null -> true to "已提交删除"
                    else -> true to "已删除"
                }
            }
            _sms.update { it.copy(toast = r.second) }
            onDone(r.first, r.second)
            if (r.first) loadSms()
        }
    }

    fun consumeSmsToast() {
        _sms.update { it.copy(toast = null) }
    }

    // ═══════════════════════════ SIM 卡槽 ═══════════════════════════

    /**
     * 切换 SIM 卡槽。
     *
     * 这是本 App 少数几个会真实改变设备状态的操作之一，所以：
     *  · 二次确认由调用方（界面）负责，这里只执行一次，**绝不自动重试**；
     *  · 切换后 modem 会重新注册网络，期间信号/流量卡片会短暂无数据，
     *    因此等几秒再补一次全量刷新。
     */
    fun switchSimSlot(slot: Int, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!client.isLoggedIn) { onDone(false, "需要先登录"); return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val r = withContext(Dispatchers.IO) {
                val res = runCatching {
                    client.call(
                        Api.Write.SIM_SWITCH.obj, Api.Write.SIM_SWITCH.name,
                        JSONObject().put("slot", slot)
                    )
                }.getOrElse {
                    return@withContext false to (it.message ?: "切换请求失败")
                }
                // 真机实测：切卡成功时响应体是空的（连 result 字段都没有），
                // 只有参数非法才会抛 status 2。所以走到这里就说明设备受理了。
                if (res.writeAccepted() == false) {
                    return@withContext false to "设备拒绝了切换请求"
                }
                // 受理不等于切好了 —— modem 要重新注册网络，轮询确认真实卡槽
                var landed: String? = null
                for (i in 0 until 20) {            // 最多约 20 秒
                    delay(1000)
                    val d = runCatching {
                        client.call(
                            "zwrt_zte_mdm.api", "get_sim_info_before", JSONObject()
                        )
                    }.getOrNull() ?: continue
                    landed = d.s("current_sim_slot").ifBlank { null }
                    if (landed == slot.toString()) break
                }
                when {
                    landed == slot.toString() -> true to "已切换到卡槽 $slot"
                    landed.isNullOrBlank() -> true to "已提交切换（设备暂未回读卡槽，请下拉刷新确认）"
                    else -> true to "切换指令已受理，但设备当前卡槽仍为 $landed"
                }
            }
            _state.update { it.copy(busy = false, toast = r.second) }
            onDone(r.first, r.second)
            delay(1500)
            refreshOnce(forceSlow = true)
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /** tier=1 接口（变化较慢：本月流量、终端明细、Wi-Fi 接口表…）每 N 个周期刷新 */
        const val T1_EVERY = 4
        /** tier=2 接口（基本不变：温控 / APN / VPN / 短信容量 / 夜间模式 / DDNS…）每 N 个周期刷新 */
        const val T2_EVERY = 15
    }
}
