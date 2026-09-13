package com.cpepanel.cpe

import org.json.JSONObject

/** JSON 取值糖，容忍设备返回 string / number / bool 混用 */
object J {
    val TRUE_SET = setOf("1", "true", "on", "yes", "enable", "enabled")

    fun JSONObject.s(key: String, def: String = ""): String {
        val v = optString(key, def)
        return if (v == "null") def else v
    }

    /**
     * 写操作是否被设备接受。
     *
     * 实测契约（真机验证，2026-09-13）：发短信 / 删短信 / 存草稿这类写接口，
     * 成功时返回 `{"result":3}`，业务失败（如空号码、id 不存在）返回 `{"result":2}`，
     * 参数缺失则由传输层抛 status 2 异常（根本走不到这里）。
     * 少数机型/分支会返回 `{"result":"success"}`，所以两种都要认。
     * 另有极少数接口（如 sim_switch_slot）返回体为 null —— 那也算接受，
     * 真实结果要靠读状态接口确认，所以 [defAccept] 默认 false 时按 null 处理为未拒绝。
     *
     * @return true 接受 / false 明确失败 / null 无结论（响应为空）
     */
    fun JSONObject.writeAccepted(): Boolean? {
        if (!has("result") || isNull("result")) return null
        val v = opt("result").toString().trim()
        if (v.equals("success", true)) return true
        return when (v) {
            "3", "3.0", "0" -> true
            "2", "-1" -> false
            else -> null
        }
    }

    fun JSONObject.i(key: String, def: Int = 0): Int = when (val v = opt(key)) {
        null, JSONObject.NULL -> def
        is Number -> v.toInt()
        else -> v.toString().trim().toDoubleOrNull()?.toInt() ?: def
    }

    fun JSONObject.l(key: String, def: Long = 0L): Long = when (val v = opt(key)) {
        null, JSONObject.NULL -> def
        is Number -> v.toLong()
        else -> v.toString().trim().toDoubleOrNull()?.toLong() ?: def
    }

    fun JSONObject.f(key: String, def: Float = 0f): Float = when (val v = opt(key)) {
        null, JSONObject.NULL -> def
        is Number -> v.toFloat()
        else -> v.toString().trim().toFloatOrNull() ?: def
    }

    fun JSONObject.b(key: String, def: Boolean = false): Boolean = when (val v = opt(key)) {
        null, JSONObject.NULL -> def
        is Boolean -> v
        is Number -> v.toInt() != 0
        else -> v.toString().trim().lowercase().let { it in TRUE_SET || if (it.isEmpty()) def else false }
    }

    /** 按 "a.b.c" 路径取嵌套值 */
    fun dig(json: JSONObject?, path: String): Any? {
        var cur: Any? = json
        for (seg in path.split('.')) {
            cur = (cur as? JSONObject)?.opt(seg) ?: return null
        }
        return cur
    }

    fun JSONObject.digL(path: String, def: Long = 0L): Long = when (val v = dig(this, path)) {
        is Number -> v.toLong()
        is String -> v.toDoubleOrNull()?.toLong() ?: def
        else -> def
    }

    fun JSONObject.digS(path: String, def: String = ""): String = when (val v = dig(this, path)) {
        null, JSONObject.NULL -> def
        else -> v.toString()
    }

    /** 把嵌套 JSON 摊平成 "父.子" -> 值，供通用表格渲染 */
    fun flatten(json: JSONObject?, prefix: String = ""): List<Pair<String, String>> {
        if (json == null) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (k in json.keys()) {
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            when (val v = json.opt(k)) {
                is JSONObject -> out += flatten(v, key)
                is org.json.JSONArray -> out += key to "[${v.length()} 项]"
                null, JSONObject.NULL -> out += key to "—"
                else -> out += key to v.toString()
            }
        }
        return out
    }
}
