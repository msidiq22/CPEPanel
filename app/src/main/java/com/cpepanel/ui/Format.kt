package com.cpepanel.ui

import androidx.compose.ui.graphics.Color
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Blue
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.Rose
import com.cpepanel.ui.theme.TextMu

/** 信号质量评级 */
data class Quality(val label: String, val color: Color, val ratio: Float)

/**
 * 5G NR 的 RSRP 参考档位（3GPP / 运营商常用口径）：
 *  ≥ -80 极佳 / -80~-90 优秀 / -90~-100 良好 / -100~-110 一般 / -110~-120 较差 / < -120 极差
 */
fun rsrpQuality(rsrp: Int): Quality = when {
    rsrp == 0    -> Quality("无信号", TextMu, 0f)
    rsrp >= -80  -> Quality("极佳", Mint, 1.00f)
    rsrp >= -90  -> Quality("优秀", Mint, 0.85f)
    rsrp >= -100 -> Quality("良好", Cyan, 0.68f)
    rsrp >= -110 -> Quality("一般", Amber, 0.48f)
    rsrp >= -120 -> Quality("较差", Rose, 0.28f)
    else         -> Quality("极差", Rose, 0.12f)
}

fun snrQuality(snr: Float): Quality = when {
    snr <= 0f -> Quality("无", TextMu, 0f)
    snr >= 20 -> Quality("优", Mint, 1.00f)
    snr >= 13 -> Quality("良", Cyan, 0.78f)
    snr >= 7  -> Quality("中", Amber, 0.52f)
    else      -> Quality("差", Rose, 0.30f)
}

/** 把 RSRP 归一化到 0..1，用于波形/条形显示 */
fun rsrpRatio(rsrp: Int): Float = when {
    rsrp == 0 -> 0f
    rsrp <= -120 -> 0f
    rsrp >= -70 -> 1f
    else -> ((rsrp + 120) / 50f).coerceIn(0f, 1f)
}

fun snrRatio(snr: Float): Float = (snr / 30f).coerceIn(0f, 1f)

// ───────────────────────── 文案映射 ─────────────────────────

fun carrierName(provider: String, operator: String): String {
    val raw = if (provider.isNotBlank()) provider else operator
    return when (raw.uppercase()) {
        "UNICOM", "CUCC", "CHINA UNICOM" -> "中国联通"
        "MOBILE", "CMCC", "CHINA MOBILE" -> "中国移动"
        "TELECOM", "CTCC", "CHINA TELECOM" -> "中国电信"
        "BROADNET", "CBN" -> "中国广电"
        "" -> "—"
        else -> raw
    }
}

fun networkTypeText(t: String): String = when (t.uppercase()) {
    "SA" -> "5G SA"
    "NSA" -> "5G NSA"
    "LTE", "LTE_FDD" -> "4G LTE"
    "WCDMA" -> "3G WCDMA"
    "GSM" -> "2G GSM"
    "" -> "—"
    else -> t
}

fun wanStatusText(raw: String): String = when {
    raw.contains("connected", true) && raw.contains("ipv4", true) -> "已连接（IPv4/IPv6）"
    raw.contains("connected", true) -> "已连接"
    raw.contains("connecting", true) -> "连接中"
    raw.contains("disconnect", true) -> "未连接"
    raw.isBlank() -> "—"
    else -> raw
}

fun wanModeText(mode: String, auto: String): String = when (mode.uppercase()) {
    "PPP" -> "5G 网关拨号"
    "LTE_BRIDGE" -> "桥接模式"
    "STATIC" -> "静态 IP"
    "DHCP" -> "DHCP"
    "" -> auto.ifBlank { "—" }
    else -> "$mode / ${auto.ifBlank { "-" }}"
}

fun refreshLabel(ms: Long): String = when (ms) {
    0L -> "关闭"
    in 0..1500 -> "1 秒"
    3000L -> "3 秒"
    5000L -> "5 秒"
    10000L -> "10 秒"
    30000L -> "30 秒"
    else -> "${ms / 1000} 秒"
}

// ───────────────────────── 数值格式化 ─────────────────────────

fun uptimeText(seconds: Long): String {
    if (seconds <= 0) return "—"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        d > 0 -> "${d}天 ${h}小时 ${m}分"
        h > 0 -> "${h}小时 ${m}分"
        m > 0 -> "${m}分 ${s}秒"
        else -> "${s}秒"
    }
}

fun kbText(kb: Long): String = when {
    kb <= 0 -> "—"
    kb >= 1024 * 1024 -> String.format("%.2f GB", kb / 1024.0 / 1024.0)
    kb >= 1024 -> String.format("%.1f MB", kb / 1024.0)
    else -> "$kb KB"
}

/** 把进制字节/包数量转成人类可读 */
fun bytesText(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

fun timeText(ts: Long): String {
    if (ts <= 0) return "—"
    val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(ts))
}
