package com.cpepanel.data

import android.content.Context
import com.cpepanel.cpe.UbusClient

/** 轻量本地存储：地址、会话 token、刷新间隔、加密协商开关 */
class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("cpe_panel", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString(KEY_BASE, UbusClient.DEFAULT_BASE) ?: UbusClient.DEFAULT_BASE
        set(v) = sp.edit().putString(KEY_BASE, v).apply()

    /** 只存会话 token，不存明文密码 */
    var sessionToken: String
        get() = sp.getString(KEY_SESSION, "") ?: ""
        set(v) = sp.edit().putString(KEY_SESSION, v).apply()

    /**
     * 设备下发的 webtoken Cookie。
     * 少了它，登录后所有鉴权接口都会返回 -32002，会话等于没建立。
     */
    var webToken: String
        get() = sp.getString(KEY_WEBTOKEN, "") ?: ""
        set(v) = sp.edit().putString(KEY_WEBTOKEN, v).apply()

    var refreshMs: Long
        get() = sp.getLong(KEY_REFRESH, 3000L)
        set(v) = sp.edit().putLong(KEY_REFRESH, v).apply()

    /**
     * 会话加密协商开关。默认开启 —— 官方前端在登录成功后必定调用
     * web_crt_get + web_http_enstr_set（service_rpc.js 的 he()），
     * 不协商的话设备返回的 Wi-Fi 密钥 / APN 口令等字段是解不开的。
     */
    var negotiateCrypto: Boolean
        get() = sp.getBoolean(KEY_NEGO, true)
        set(v) = sp.edit().putBoolean(KEY_NEGO, v).apply()

    private companion object {
        const val KEY_BASE = "base_url"
        const val KEY_SESSION = "session"
        const val KEY_WEBTOKEN = "webtoken"
        const val KEY_REFRESH = "refresh_ms"
        const val KEY_NEGO = "nego_crypto"
    }
}
