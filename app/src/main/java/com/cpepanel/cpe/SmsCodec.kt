package com.cpepanel.cpe

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 短信编解码工具 —— 逐行复刻设备网页版前端 `js/util.js` 的实现。
 *
 * 为什么必须自己实现：设备要求短信正文以 **UTF-16BE 的十六进制串** 提交，
 * 并由 `encode_type` 告诉调制解调器该按哪种编码打包（GSM7 还是 UCS2）。
 * 只把明文丢过去，设备会返回 success 但短信内容乱码 —— 或者干脆发不出去。
 *
 * 与前端一一对应的函数：
 *   encodeType()        ← getEncodeType()     判定 GSM7_default / UNICODE
 *   encodeBody()        ← encodeMessage()     文本 → UTF-16BE 十六进制
 *   decodeBody()        ← decodeMessage()     十六进制 → 文本（读短信用）
 *   currentTimeString() ← getCurrentTimeString()  生成 sms_time 字段
 *   smsCount()          ← getSmsCount()       计算分条数
 */
object SmsCodec {

    /** 纯 GSM7 字符集时使用的编码类型标识（设备端据此选择 DCS） */
    const val ENC_GSM7 = "GSM7_default"

    /** 含 GSM7 表外字符（中文、emoji 等）时使用 */
    const val ENC_UNICODE = "UNICODE"

    /** GSM7 单条上限 / 分条时每条的可用长度 */
    const val GSM7_SINGLE = 160
    const val GSM7_MULTI = 153

    /** UCS2（UNICODE）单条上限 / 分条时每条的可用长度 */
    const val UCS2_SINGLE = 70
    const val UCS2_MULTI = 67

    /** GSM7 基本字符集（等价前端 GSM7_Table，值为 UTF-16 码元的 4 位大写十六进制） */
    private val GSM7_BASIC = setOf(
        "000A",
        "000C",
        "000D",
        "0020",
        "0021",
        "0022",
        "0023",
        "0024",
        "0025",
        "0026",
        "0027",
        "0028",
        "0029",
        "002A",
        "002B",
        "002C",
        "002D",
        "002E",
        "002F",
        "0030",
        "0031",
        "0032",
        "0033",
        "0034",
        "0035",
        "0036",
        "0037",
        "0038",
        "0039",
        "003A",
        "003B",
        "003C",
        "003D",
        "003E",
        "003F",
        "0040",
        "0041",
        "0042",
        "0043",
        "0044",
        "0045",
        "0046",
        "0047",
        "0048",
        "0049",
        "004A",
        "004B",
        "004C",
        "004D",
        "004E",
        "004F",
        "0050",
        "0051",
        "0052",
        "0053",
        "0054",
        "0055",
        "0056",
        "0057",
        "0058",
        "0059",
        "005A",
        "005B",
        "005C",
        "005D",
        "005E",
        "005F",
        "0061",
        "0062",
        "0063",
        "0064",
        "0065",
        "0066",
        "0067",
        "0068",
        "0069",
        "006A",
        "006B",
        "006C",
        "006D",
        "006E",
        "006F",
        "0070",
        "0071",
        "0072",
        "0073",
        "0074",
        "0075",
        "0076",
        "0077",
        "0078",
        "0079",
        "007A",
        "007B",
        "007C",
        "007D",
        "007E",
        "00A0",
        "00A1",
        "00A3",
        "00A4",
        "00A5",
        "00A7",
        "00BF",
        "00C4",
        "00C5",
        "00C6",
        "00C7",
        "00C9",
        "00D1",
        "00D6",
        "00D8",
        "00DC",
        "00DF",
        "00E0",
        "00E4",
        "00E5",
        "00E6",
        "00E8",
        "00E9",
        "00EC",
        "00F1",
        "00F2",
        "00F6",
        "00F8",
        "00F9",
        "00FC",
        "0393",
        "0394",
        "0398",
        "039B",
        "039E",
        "03A0",
        "03A3",
        "03A6",
        "03A8",
        "03A9",
        "20AC"
    )

    /** GSM7 扩展字符集（在 GSM7 编码下每个占 2 个字符位） */
    private val GSM7_EXTEND = setOf(
        "000C",
        "005E",
        "007B",
        "007D",
        "005C",
        "005B",
        "007E",
        "005D",
        "007C",
        "20AC"
    )


    /** 单个 UTF-16 码元的 4 位大写十六进制表示（与前端 dec2hex + 补零等价） */
    private fun hex4(code: Int): String = String.format(Locale.US, "%04X", code)

    private fun hexOfChar(c: Char): String = hex4(c.code)

    // ─────────────────────── 编码类型判定 ───────────────────────

    data class EncodeInfo(val encodeType: String, val extendLen: Int)

    /**
     * 与前端 `getEncodeType` 完全一致：
     * 从第一个字符开始扫描，只要出现一个不在 GSM7 基本表里的字符，
     * 整条短信就按 UNICODE(UCS2) 处理，并停止统计扩展字符数。
     *
     * 注意这里是逐 Char（UTF-16 码元）扫描，emoji 会被拆成代理对，
     * 高位代理必然不在表内 —— 所以含 emoji 的短信会自动走 UNICODE，行为正确。
     */
    fun encodeInfo(text: String): EncodeInfo {
        var type = ENC_GSM7
        var extend = 0
        for (c in text) {
            val h = hexOfChar(c)
            if (h in GSM7_EXTEND) extend++
            if (h !in GSM7_BASIC) return EncodeInfo(ENC_UNICODE, 0)
        }
        return EncodeInfo(type, extend)
    }

    fun encodeType(text: String): String = encodeInfo(text).encodeType

    // ─────────────────────── 正文编解码 ───────────────────────

    /**
     * 文本 → UTF-16BE 十六进制（大写）。
     *
     * 基本平面字符固定 4 位；星面字符（代理对）合并成一个码点后按 5~6 位输出，
     * 与前端 `encodeMessage` 的 `dec2hex(65536 + ...)` 行为一致。
     */
    fun encodeBody(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length * 4)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                val cp = Character.toCodePoint(c, text[i + 1])
                sb.append(Integer.toHexString(cp).uppercase(Locale.US))
                i += 2
            } else {
                sb.append(hexOfChar(c))
                i++
            }
        }
        return sb.toString()
    }

    /**
     * UTF-16BE 十六进制 → 文本。设备返回的短信正文就是这种格式。
     *
     * 按 4 位一组解析（设备实际下发的基本平面内容都是 4 位对齐的）；
     * 尾部若有不足 4 位的残余则忽略，与前端正则 `[A-Fa-f0-9]{1,4}` 的容错思路一致。
     */
    fun decodeBody(hex: String): String {
        if (hex.isBlank()) return ""
        val sb = StringBuilder(hex.length / 4)
        var i = 0
        while (i + 4 <= hex.length) {
            val chunk = hex.substring(i, i + 4)
            val v = chunk.toIntOrNull(16)
            if (v == null) {
                i += 4
                continue
            }
            sb.append(v.toChar())
            i += 4
        }
        return sb.toString()
    }

    // ─────────────────────── 时间与计数 ───────────────────────

    /**
     * 生成发送短信所需的 `sms_time`，格式与前端 `getCurrentTimeString` 一致：
     *   `YY;MM;DD;HH;mm;ss;TZ`  例如 `26;09;13;22;55;30;+8`
     *
     * 时区那一项前端用的是"与 UTC 相差的小时数、正数带 + 号"，
     * 这里按 Android 的 offset 换算成同样的写法。
     */
    fun currentTimeString(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val tzMin = TimeZone.getDefault().getOffset(now) / 60000
        val tz = when {
            tzMin > 0 -> "+${tzMin / 60}"
            tzMin < 0 -> "${tzMin / 60}"
            else -> "0"
        }
        return String.format(
            Locale.US, "%02d;%02d;%02d;%02d;%02d;%02d;%s",
            c.get(Calendar.YEAR) % 100,
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND),
            tz
        )
    }

    data class SmsCount(val length: Int, val parts: Int, val perPart: Int, val unicode: Boolean) {
        val single: Boolean get() = parts <= 1
    }

    /** 与前端 `getSmsCount` 一致：GSM7 160/153，UNICODE 70/67 */
    fun smsCount(text: String): SmsCount {
        val info = encodeInfo(text)
        val n = text.length
        val unicode = info.encodeType == ENC_UNICODE
        if (unicode) {
            val parts = if (n > UCS2_SINGLE) (n + UCS2_MULTI - 1) / UCS2_MULTI else 1
            return SmsCount(n, parts, UCS2_MULTI, true)
        }
        val eff = n + info.extendLen
        val parts = if (eff > GSM7_SINGLE) (eff + GSM7_MULTI - 1) / GSM7_MULTI else 1
        return SmsCount(n, parts, GSM7_MULTI, false)
    }

    /**
     * 把设备返回的时间戳转成显示文本。
     * 设备格式形如 `26,09,11,01,21,20,+32`（逗号分隔，末位是 1/4 小时为单位的时区）。
     */
    fun formatDate(raw: String): String {
        if (raw.isBlank()) return ""
        val p = raw.split(",")
        if (p.size < 6) return raw
        fun two(s: String) = s.trim().padStart(2, '0')
        val yy = p[0].trim()
        val mm = two(p[1])
        val dd = two(p[2])
        val hh = two(p[3])
        val mi = two(p[4])
        val ss = two(p[5])
        return "$yy/$mm/$dd $hh:$mi:$ss"
    }

    /** 只要日期部分，用于会话列表的紧凑展示 */
    fun shortDate(raw: String): String {
        val full = formatDate(raw)
        return if (full.length >= 14) full.substring(0, 8) + " " + full.substring(9, 14) else full
    }
}
