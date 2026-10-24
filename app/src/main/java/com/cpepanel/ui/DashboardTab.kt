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
                Text("View signal history →", color = Cyan, fontSize = 13.sp)
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

    PanelCard(title = if (isNr) "5G NR Signal" else "Signal Strength", accent = q.color) {
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
                    if (isNr) "NR primary cell" else "LTE primary cell",
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
                Text("$bar / 5 bars", color = TextMu, fontSize = 11.sp)
            }
        }
        if (bw.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            HintBox("Carrier bandwidth $bw MHz · modulation and MIMO are scheduled dynamically by the cell", TextLo)
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

    PanelCard(title = "Key metrics") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("SINR", if (snr == 0f) "—" else String.format("%.1f", snr), "dB", sq.color, Modifier.weight(1f))
            MetricTile("RSRQ", if (rsrq == 0) "—" else "$rsrq", "dB", Blue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("RSSI", if (rssi == 0) "—" else "$rssi", "dBm", Violet, Modifier.weight(1f))
            MetricTile(
                "Round-trip latency", if (state.latencyMs >= 0) "${state.latencyMs}" else "—", "ms",
                if (state.latencyMs in 0..80) Mint else Amber, Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        HintBox(
            "SINR: ${sq.label} | " + snrHint(snr) + "\n" + rsrpHint(net?.i("nr5g_rsrp", 0) ?: 0),
            TextLo
        )
    }
}

private fun snrHint(snr: Float): String = when {
    snr >= 20 -> "Very clean signal, full bandwidth available"
    snr >= 13 -> "Good signal, no issues for everyday use"
    snr >= 7 -> "Some interference; throughput may drop"
    snr > 0 -> "Heavy interference; try turning or moving the device"
    else -> "SINR unavailable"
}

private fun rsrpHint(rsrp: Int): String = when {
    rsrp == 0 -> "No 5G primary-cell level reported"
    rsrp >= -90 -> "Good coverage — best placement already achieved"
    rsrp >= -100 -> "Coverage is okay; move closer to a window if speed is low"
    rsrp >= -110 -> "Weak coverage; try a different placement"
    else -> "Very poor coverage; consider an external antenna or another location"
}

// ─────────────────────────── 小区参数 ───────────────────────────

@Composable
private fun CellCard(net: JSONObject?) {
    if (net == null) return
    PanelCard(title = "Cell parameters", accent = Mint) {
        KV("Band", net.s("wan_active_band").ifBlank { net.s("nr5g_action_band") })
        KV("Carrier bandwidth", net.s("nr5g_bandwidth").let { if (it.isBlank()) "—" else "$it MHz" })
        KV("PCI", net.s("nr5g_pci"))
        KV("Cell ID", net.s("nr5g_cell_id"))
        KV("EARFCN", net.s("nr5g_action_channel"))
        KV("TAC / LAC", net.s("lac_code"))
        KV("Network selection", netSelectText(net.s("net_select"), net.s("net_select_mode")))
        KV("Roaming", if (net.s("simcard_roam") == "Home") "Home network" else net.s("simcard_roam"))
        val nbr = net.s("nr_neighbor_cell")
        KV("Neighbor cells", if (nbr.isBlank()) "—" else "${nbr.split(';').count { it.isNotBlank() }}")
    }
}

private fun netSelectText(mode: String, sub: String): String = when {
    mode.contains("auto", true) -> "Automatic"
    mode.contains("manual", true) -> "Manual"
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
        title = "WAN uplink",
        accent = if (connected) Mint else Rose,
        trailing = { Pill(if (connected) "Online" else "Offline", if (connected) Mint else Rose) }
    ) {
        KV("Status", wanStatusText(rs.s("current_wan_status")), if (connected) Mint else Rose)
        KV("Bearer", wanModeText(rs.s("opms_wan_mode"), rs.s("opms_wan_auto_mode")))
        // 登录后由 get_wwaniface 提供完整地址信息（免登录的 router_get_status_no_auth 不含这些字段）
        state.authOf(Api.WAN_IFACE)?.let { w ->
            KV("WAN IPv4", w.s("ipv4_address"), TextHi)
            KV("WAN IPv6", w.s("ipv6_address"))
            KV("Gateway", w.s("ipv4_gateway"))
            KV("Primary DNS", w.s("ipv4_dns_prefer"))
            KV("Secondary DNS", w.s("ipv4_dns_standby"))
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
        PanelCard(title = "Data usage", accent = Blue) {
            HintBox(
                state.authErrors[Api.TRAFFIC_MONTH.key]
                    ?: state.authErrors[Api.TRAFFIC_RT.key]
                    ?: "Reading traffic data…",
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
        title = "Data usage",
        accent = Blue,
        trailing = {
            if (connTime > 0) Text("Connection ${uptimeText(connTime)}", color = TextMu, fontSize = 11.sp)
        }
    ) {
        Text("Real-time speed", color = TextLo, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Down", speedText(rtRx), "", Cyan, Modifier.weight(1f))
            MetricTile("Up", speedText(rtTx), "", Violet, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        Text("This month", color = TextLo, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Down", bytesText(monthRx), "", Blue, Modifier.weight(1f))
            MetricTile("Up", bytesText(monthTx), "", Amber, Modifier.weight(1f))
        }

        val total = monthRx + monthTx
        val cap = limit?.s("value")?.toLongOrNull() ?: 0L
        val limitOn = limit?.b("enable") == true && cap > 0
        Spacer(Modifier.height(14.dp))
        if (limitOn) {
            val ratio = (total.toDouble() / cap.toDouble()).toFloat()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Plan usage", color = TextLo, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${bytesText(total)} / ${bytesText(cap)} · ${"%.1f".format(ratio * 100)}%",
                    color = if (ratio >= 0.8f) Amber else Mint,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(ratio = ratio, color = if (ratio >= 0.8f) Amber else Cyan)
            val alert = limit?.i("ratio", 0) ?: 0
            if (alert > 0) {
                Spacer(Modifier.height(8.dp))
                HintBox("The device raises a usage alert when the plan reaches $alert%.", TextLo)
            }
        } else {
            HintBox("No plan limit is enabled on the device, or the limit was not reported.", TextLo)
        }

        val maxTx = rt?.l("real_max_tx_speed") ?: 0L
        val maxRx = rt?.l("real_max_rx_speed") ?: 0L
        if (maxTx > 0 || maxRx > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Connection peak", color = TextLo, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            KV("Down peak", speedText(maxRx))
            KV("Up peak", speedText(maxTx))
        }

        Spacer(Modifier.height(8.dp))
        HintBox(
            "Byte counters come from the device's wwan interface and may differ slightly from your carrier's billing.",
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
        trailing = { Pill(if (on) "On" else "Off", if (on) Mint else TextMu) }
    ) {
        KV("2.4G SSID", ssid2)
        KV("5G SSID", ssid5)
        KV("2.4G security", authModeText(w.s("main2g_authmode")))
        KV("5G security", authModeText(w.s("main5g_authmode")))
        KV("2.4G radio", if (w.b("radio2_disabled")) "Off" else "Active")
        KV("5G radio", if (w.b("radio5_disabled")) "Off" else "Active")
    }
}

private fun authModeText(m: String): String = when (m.lowercase()) {
    "sae-mixed" -> "WPA2/WPA3 mixed"
    "sae" -> "WPA3-SAE"
    "wpa2psk", "wpa2-psk" -> "WPA2-PSK"
    "wpa3psk" -> "WPA3-PSK"
    "open", "none" -> "Open (no password)"
    "wpa-psk", "wpa" -> "WPA-PSK"
    else -> m.ifBlank { "—" }
}

// ─────────────────────────── 登录后增强 ───────────────────────────

@Composable
private fun AuthEnhancedCard(state: CpeState, onLoginClick: () -> Unit) {
    if (!state.loggedIn) {
        PanelCard(title = "More data", accent = Amber) {
            Text(
                "These values require an admin sign-in:\n" +
                    "· Monthly traffic totals and plan limit\n" +
                    "· Online client list and device names\n" +
                    "· 5G neighbor-cell details\n" +
                    "· System time, thermal policy, sleep settings\n\n" +
                    "Sign-in is mainly for reading. The only writes are SMS send/delete and SIM slot switching; each one asks for confirmation and is never retried automatically.",
                color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan)
            ) { Text("Sign in to unlock", fontSize = 13.sp) }
        }
        return
    }

    val okCount = Api.AUTH.count { state.authOf(it) != null }
    val failures = Api.AUTH.mapNotNull { m -> state.authErrors[m.key]?.let { m to it } }

    PanelCard(
        title = "Authenticated endpoints",
        accent = Mint,
        trailing = {
            Pill(
                "$okCount / ${Api.AUTH.size} available",
                if (failures.isEmpty()) Mint else Amber
            )
        }
    ) {
        state.authOf(Api.USER_NUM)?.let { n ->
            KV(
                "Online clients",
                "${n.i("access_total_num")} total · wired ${n.i("lan_num")} · wireless ${n.i("wireless_num")}"
            )
        }
        state.authOf(Api.SYS_TIME)?.let { KV("Device time", it.s("localtime")) }
        state.authOf(Api.APN_MODE)?.let {
            KV("APN mode", if (it.i("apn_mode") == 0) "Auto" else "Manual")
        }
        state.authOf(Api.NIGHT_MODE)?.let {
            KV("Night mode", if (it.b("is_nightmode")) "Active" else "Inactive")
        }
        state.authOf(Api.THERMAL)?.let { KV("Thermal policy", it.s("current_policy")) }
        state.authOf(Api.NEW_VERSION)?.let {
            val v = it.s("new_version").ifBlank { it.s("version") }
            KV("Firmware", v.ifBlank { "Up to date or not reported" })
        }

        if (failures.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Unavailable endpoints", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            failures.forEach { (m, msg) ->
                KV("${m.label} (${m.obj})", msg, Amber, mono = false)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            HintBox("All ${Api.AUTH.size} authenticated endpoints were read successfully. Writes are limited to SMS send/delete and SIM slot switching, each confirmed and never retried automatically.", TextLo)
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
    PanelCard(title = "Cannot reach CPE", accent = Rose) {
        Text(
            state.lastError ?: "Device not responding",
            color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
        )
        Spacer(Modifier.height(10.dp))
        HintBox(
            "Checklist:\n" +
                "1. Is this phone connected to the CPE's Wi-Fi (not mobile data, not another router)?\n" +
                "2. Current address: ${state.baseUrl}\n" +
                "3. Does $state.baseUrl open the login page in your phone browser?\n" +
                "4. The address may change after a reboot — you can edit it in Settings",
            TextLo
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan)
        ) { Text("Reconnect", fontSize = 13.sp) }
    }
}

@Composable
fun LoadingBanner() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text("Reading device…", color = TextMu, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
