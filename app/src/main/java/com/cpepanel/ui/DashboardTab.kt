package com.cpepanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.cpe.Api
import com.cpepanel.cpe.J.b
import com.cpepanel.cpe.J.f
import com.cpepanel.cpe.J.i
import com.cpepanel.cpe.J.l
import com.cpepanel.cpe.J.s
import com.cpepanel.data.CpeState
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Blue
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.Rose
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu
import com.cpepanel.ui.theme.Violet
import org.json.JSONObject

@Composable
fun DashboardTab(
    state: CpeState,
    onLoginClick: () -> Unit,
    onGoSignal: () -> Unit
) {
    val net = state.net
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SignalHeroCard(net) }
        item { MetricGrid(net, state) }
        item { CellCard(net) }
        item { WanCard(state) }
        item { TrafficCard(state) }
        item { WifiCard(state) }
        item { AuthEnhancedCard(state, onLoginClick) }
        item {
            TextButton(onClick = onGoSignal, modifier = Modifier.fillMaxWidth()) {
                Text("查看信号历史曲线 →", color = Cyan, fontSize = 13.sp)
            }
        }
    }
}

// ─────────────────────────── 信号主卡 ───────────────────────────

@Composable
private fun SignalHeroCard(net: JSONObject?) {
    val nrRsrp = net?.i("nr5g_rsrp", 0) ?: 0
    val lteRsrp = net?.i("lte_rsrp", 0) ?: 0
    val rsrp = if (nrRsrp != 0) nrRsrp else lteRsrp
    val isNr = nrRsrp != 0
    val q = rsrpQuality(rsrp)
    val bar = net?.i("signalbar", 0) ?: 0
    val provider = carrierName(
        net?.s("network_provider") ?: "",
        net?.s("network_provider_fullname") ?: ""
    )
    val netType = networkTypeText(net?.s("network_type") ?: "")
    val band = net?.s("wan_active_band").orEmpty().ifBlank { net?.s("nr5g_action_band").orEmpty() }
    val bw = net?.s("nr5g_bandwidth").orEmpty()

    PanelCard(title = if (isNr) "5G NR 信号" else "信号强度", accent = q.color) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadialGauge(
                ratio = rsrpRatio(rsrp),
                color = q.color,
                big = if (rsrp == 0) "—" else "$rsrp",
                bigUnit = "dBm",
                caption = q.label,
                modifier = Modifier.size(134.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    provider,
                    color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isNr) "NR 主小区" else "LTE 主小区",
                    color = TextMu, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (netType.isNotBlank()) Pill(netType, Cyan)
                    if (band.isNotBlank()) Pill(band, Mint)
                }
                Spacer(Modifier.height(12.dp))
                SignalBars(
                    bars = bar,
                    color = q.color,
                    modifier = Modifier
                        .width(86.dp)
                        .height(26.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("信号格 $bar / 5", color = TextMu, fontSize = 11.sp)
            }
        }
        if (bw.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            HintBox("载波带宽 $bw MHz　·　调制与多天线由基站动态调度", TextLo)
        }
    }
}

// ─────────────────────────── 指标网格 ───────────────────────────

@Composable
private fun MetricGrid(net: JSONObject?, state: CpeState) {
    val snrRaw = net?.s("nr5g_snr").orEmpty()
    if (snrRaw.isBlank() && net?.s("lte_snr").orEmpty().isBlank()) return
    val snr = net?.f("nr5g_snr", net?.f("lte_snr", 0f) ?: 0f) ?: 0f
    val rsrq = net?.i("nr5g_rsrq", net?.i("lte_rsrq", 0) ?: 0) ?: 0
    val rssi = net?.i("nr5g_rssi", net?.i("lte_rssi", 0) ?: 0) ?: 0
    val sq = snrQuality(snr)

    PanelCard(title = "关键指标") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("SINR 信噪比", if (snr == 0f) "—" else String.format("%.1f", snr), "dB", sq.color, Modifier.weight(1f))
            MetricTile("RSRQ 质量", if (rsrq == 0) "—" else "$rsrq", "dB", Blue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("RSSI 强度", if (rssi == 0) "—" else "$rssi", "dBm", Violet, Modifier.weight(1f))
            MetricTile(
                "回程延迟", if (state.latencyMs >= 0) "${state.latencyMs}" else "—", "ms",
                if (state.latencyMs in 0..80) Mint else Amber, Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        HintBox(
            "SINR：${sq.label}　|　" + snrHint(snr) + "\n" + rsrpHint(net?.i("nr5g_rsrp", 0) ?: 0),
            TextLo
        )
    }
}

private fun snrHint(snr: Float): String = when {
    snr >= 20 -> "信号非常干净，可跑满带宽"
    snr >= 13 -> "信号良好，日常使用无压力"
    snr >= 7 -> "有一定干扰，速率可能打折"
    snr > 0 -> "干扰明显，建议调整设备朝向或位置"
    else -> "无法读取信噪比"
}

private fun rsrpHint(rsrp: Int): String = when {
    rsrp == 0 -> "未读取到 5G 主小区电平"
    rsrp >= -90 -> "覆盖良好，靠近窗户或室外更佳位置已达成"
    rsrp >= -100 -> "覆盖尚可，若速率不理想可尝试移近窗口"
    rsrp >= -110 -> "覆盖偏弱，建议更换摆放位置"
    else -> "覆盖很差，建议外接天线或更换位置"
}

// ─────────────────────────── 小区参数 ───────────────────────────

@Composable
private fun CellCard(net: JSONObject?) {
    if (net == null) return
    PanelCard(title = "小区参数", accent = Mint) {
        KV("频段", net.s("wan_active_band").ifBlank { net.s("nr5g_action_band") })
        KV("载波带宽", net.s("nr5g_bandwidth").let { if (it.isBlank()) "—" else "$it MHz" })
        KV("PCI 物理小区号", net.s("nr5g_pci"))
        KV("Cell ID 小区标识", net.s("nr5g_cell_id"))
        KV("信道号 EARFCN", net.s("nr5g_action_channel"))
        KV("TAC / LAC", net.s("lac_code"))
        KV("选网方式", netSelectText(net.s("net_select"), net.s("net_select_mode")))
        KV("漫游状态", if (net.s("simcard_roam") == "Home") "本地网络" else net.s("simcard_roam"))
        val nbr = net.s("nr_neighbor_cell")
        KV("邻区数量", if (nbr.isBlank()) "—" else "${nbr.split(';').count { it.isNotBlank() }} 个")
    }
}

private fun netSelectText(mode: String, sub: String): String = when {
    mode.contains("auto", true) -> "自动选网"
    mode.contains("manual", true) -> "手动选网"
    sub.isNotBlank() -> sub
    mode.isNotBlank() -> mode
    else -> "—"
}

// ─────────────────────────── WAN ───────────────────────────

@Composable
private fun WanCard(state: CpeState) {
    val rs = state.routerStatus ?: return
    val connected = rs.s("current_wan_status").contains("connected", true)
    PanelCard(
        title = "WAN 上行",
        accent = if (connected) Mint else Rose,
        trailing = { Pill(if (connected) "在线" else "离线", if (connected) Mint else Rose) }
    ) {
        KV("连接状态", wanStatusText(rs.s("current_wan_status")), if (connected) Mint else Rose)
        KV("承载方式", wanModeText(rs.s("opms_wan_mode"), rs.s("opms_wan_auto_mode")))
        // 登录后由 get_wwaniface 提供完整地址信息（免登录的 router_get_status_no_auth 不含这些字段）
        state.authOf(Api.WAN_IFACE)?.let { w ->
            KV("WAN IPv4", w.s("ipv4_address"), TextHi)
            KV("WAN IPv6", w.s("ipv6_address"))
            KV("网关", w.s("ipv4_gateway"))
            KV("首选 DNS", w.s("ipv4_dns_prefer"))
            KV("备用 DNS", w.s("ipv4_dns_standby"))
        }
    }
}

// ─────────────────────────── 流量统计 ───────────────────────────

/**
 * 本月流量 / 实时速率 / 套餐用量。
 *
 * 数据来自三个曾经没被正确调用的接口：
 *   get_wwandst{type:2}          -> 本月累计
 *   get_wwandst{type:1}          -> 实时速率 + 本次连接累计
 *   get_wwandst_monthlimit       -> 套餐上限
 * 早期实现漏了 source_module / cid / type 参数，设备一律回 status 2，因此这些卡片一直是空的。
 */
@Composable
private fun TrafficCard(state: CpeState) {
    if (!state.loggedIn) return

    val month = state.authOf(Api.TRAFFIC_MONTH)
    val rt = state.authOf(Api.TRAFFIC_RT)
    val limit = state.authOf(Api.TRAFFIC_LIMIT)

    if (month == null && rt == null) {
        PanelCard(title = "流量统计", accent = Blue) {
            HintBox(
                state.authErrors[Api.TRAFFIC_MONTH.key]
                    ?: state.authErrors[Api.TRAFFIC_RT.key]
                    ?: "正在读取流量数据…",
                Amber
            )
        }
        return
    }

    val monthRx = month?.l("month_rx_bytes") ?: 0L
    val monthTx = month?.l("month_tx_bytes") ?: 0L
    val rtRx = rt?.l("real_rx_speed") ?: 0L
    val rtTx = rt?.l("real_tx_speed") ?: 0L
    val connTime = rt?.l("real_time") ?: 0L

    PanelCard(
        title = "流量统计",
        accent = Blue,
        trailing = {
            if (connTime > 0) Text("本次连接 ${uptimeText(connTime)}", color = TextMu, fontSize = 11.sp)
        }
    ) {
        Text("实时速率", color = TextLo, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("下行", speedText(rtRx), "", Cyan, Modifier.weight(1f))
            MetricTile("上行", speedText(rtTx), "", Violet, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        Text("本月累计", color = TextLo, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("下行", bytesText(monthRx), "", Blue, Modifier.weight(1f))
            MetricTile("上行", bytesText(monthTx), "", Amber, Modifier.weight(1f))
        }

        val total = monthRx + monthTx
        val cap = limit?.s("value")?.toLongOrNull() ?: 0L
        val limitOn = limit?.b("enable") == true && cap > 0
        Spacer(Modifier.height(14.dp))
        if (limitOn) {
            val ratio = (total.toDouble() / cap.toDouble()).toFloat()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("套餐用量", color = TextLo, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${bytesText(total)} / ${bytesText(cap)}　${"%.1f".format(ratio * 100)}%",
                    color = if (ratio >= 0.8f) Amber else Mint,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(ratio = ratio, color = if (ratio >= 0.8f) Amber else Cyan)
            val alert = limit?.i("ratio", 0) ?: 0
            if (alert > 0) {
                Spacer(Modifier.height(8.dp))
                HintBox("套餐达到 $alert% 时设备会提示用量告警。", TextLo)
            }
        } else {
            HintBox("设备未启用套餐上限，或未读取到限额配置。", TextLo)
        }

        val maxTx = rt?.l("real_max_tx_speed") ?: 0L
        val maxRx = rt?.l("real_max_rx_speed") ?: 0L
        if (maxTx > 0 || maxRx > 0) {
            Spacer(Modifier.height(10.dp))
            Text("本次连接峰值", color = TextLo, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            KV("下行峰值", speedText(maxRx))
            KV("上行峰值", speedText(maxTx))
        }

        Spacer(Modifier.height(8.dp))
        HintBox(
            "字节统计由设备侧 wwan 接口计数器提供，与运营商账单口径可能略有差异。",
            TextLo
        )
    }
}

/** 把 B/s 转成人类可读的速率文本 */
private fun speedText(bytesPerSec: Long): String = when {
    bytesPerSec <= 0 -> "0"
    bytesPerSec >= 1024L * 1024 * 1024 -> "%.2f G".format(bytesPerSec / 1024.0 / 1024.0 / 1024.0)
    bytesPerSec >= 1024L * 1024 -> "%.1f M".format(bytesPerSec / 1024.0 / 1024.0)
    bytesPerSec >= 1024L -> "%.0f K".format(bytesPerSec / 1024.0)
    else -> "$bytesPerSec"
}

// ─────────────────────────── Wi-Fi ───────────────────────────

@Composable
private fun WifiCard(state: CpeState) {
    val w = state.wlan ?: return
    val ssid2 = w.s("main2g_ssid")
    val ssid5 = w.s("main5g_ssid")
    val on = !w.b("radio2_disabled") || !w.b("radio5_disabled")
    PanelCard(
        title = "Wi-Fi",
        accent = Blue,
        trailing = { Pill(if (on) "已开启" else "已关闭", if (on) Mint else TextMu) }
    ) {
        KV("2.4G SSID", ssid2)
        KV("5G SSID", ssid5)
        KV("2.4G 加密", authModeText(w.s("main2g_authmode")))
        KV("5G 加密", authModeText(w.s("main5g_authmode")))
        KV("2.4G 射频", if (w.b("radio2_disabled")) "已关闭" else "工作中")
        KV("5G 射频", if (w.b("radio5_disabled")) "已关闭" else "工作中")
    }
}

private fun authModeText(m: String): String = when (m.lowercase()) {
    "sae-mixed" -> "WPA2/WPA3 混合"
    "sae" -> "WPA3-SAE"
    "wpa2psk", "wpa2-psk" -> "WPA2-PSK"
    "wpa3psk" -> "WPA3-PSK"
    "open", "none" -> "开放（无密码）"
    "wpa-psk", "wpa" -> "WPA-PSK"
    else -> m.ifBlank { "—" }
}

// ─────────────────────────── 登录后增强 ───────────────────────────

@Composable
private fun AuthEnhancedCard(state: CpeState, onLoginClick: () -> Unit) {
    if (!state.loggedIn) {
        PanelCard(title = "更多数据", accent = Amber) {
            Text(
                "以下数据需要管理员登录后才能读取：\n" +
                    "· 本月流量统计与用量上限\n" +
                    "· 在线终端列表与设备名\n" +
                    "· 5G 邻区详细信息\n" +
                    "· 系统时间、温控策略、休眠参数\n\n" +
                    "登录只用于读取，本应用目前不提供任何写入操作。",
                color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan)
            ) { Text("登录以解锁", fontSize = 13.sp) }
        }
        return
    }

    val okCount = Api.AUTH.count { state.authOf(it) != null }
    val failures = Api.AUTH.mapNotNull { m -> state.authErrors[m.key]?.let { m to it } }

    PanelCard(
        title = "登录后接口",
        accent = Mint,
        trailing = {
            Pill(
                "$okCount / ${Api.AUTH.size} 可用",
                if (failures.isEmpty()) Mint else Amber
            )
        }
    ) {
        state.authOf(Api.USER_NUM)?.let { n ->
            KV(
                "在线终端",
                "${n.i("access_total_num")} 台　有线 ${n.i("lan_num")} · 无线 ${n.i("wireless_num")}"
            )
        }
        state.authOf(Api.SYS_TIME)?.let { KV("设备时间", it.s("localtime")) }
        state.authOf(Api.APN_MODE)?.let {
            KV("APN 模式", if (it.i("apn_mode") == 0) "自动" else "手动")
        }
        state.authOf(Api.NIGHT_MODE)?.let {
            KV("夜间模式", if (it.b("is_nightmode")) "生效中" else "未生效")
        }
        state.authOf(Api.THERMAL)?.let { KV("温控策略", it.s("current_policy")) }
        state.authOf(Api.NEW_VERSION)?.let {
            val v = it.s("new_version").ifBlank { it.s("version") }
            KV("固件版本", v.ifBlank { "已是最新或未上报" })
        }

        if (failures.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("未取到的接口", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            failures.forEach { (m, msg) ->
                KV("${m.label}（${m.obj}）", msg, Amber, mono = false)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            HintBox("全部 ${Api.AUTH.size} 个登录后接口均读取成功。当前版本仍为只读，不含任何写入操作。", TextLo)
        }
    }
}

private fun prettyKey(k: String): String = k.substringAfterLast('.')

private fun prettifyValue(key: String, value: String): String {
    val lower = key.lowercase()
    return when {
        lower.endsWith("time") && value.toLongOrNull() != null -> uptimeText(value.toLong())
        lower.contains("byte") || lower.contains("flow") || lower.contains("bytes") -> {
            value.toLongOrNull()?.let { bytesText(it) } ?: value
        }
        else -> value
    }
}

@Composable
fun OfflineBanner(state: CpeState, onRetry: () -> Unit) {
    PanelCard(title = "无法连接 CPE", accent = Rose) {
        Text(
            state.lastError ?: "设备无响应",
            color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
        )
        Spacer(Modifier.height(10.dp))
        HintBox(
            "排查清单：\n" +
                "1. 手机是否连到这台 CPE 的 Wi-Fi（不是流量、不是别的路由器）\n" +
                "2. 当前地址：${state.baseUrl}\n" +
                "3. 用手机浏览器打开 $state.baseUrl 能否看到登录页\n" +
                "4. 设备重启后地址可能变化，可在「设置」里修改",
            TextLo
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan)
        ) { Text("重新连接", fontSize = 13.sp) }
    }
}

@Composable
fun LoadingBanner() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text("正在读取设备…", color = TextMu, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
