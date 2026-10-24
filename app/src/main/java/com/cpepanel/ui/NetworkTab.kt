package com.cpepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.cpe.Api
import com.cpepanel.cpe.J.b
import com.cpepanel.cpe.J.digS
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
import com.cpepanel.ui.theme.Bg
import com.cpepanel.ui.theme.Panel
import com.cpepanel.ui.theme.Stroke
import org.json.JSONObject

/**
 * 「网络」页 —— 对齐网页版 CPE 的「网络 / Wi-Fi / 路由设置 / APN」几组页面。
 *
 * 全部接口都是真机实测 status=0 的只读接口，页面本身不做任何写入。
 */
@Composable
fun NetworkTab(state: CpeState, onLoginClick: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!state.loggedIn) {
            item { NeedLoginCard(onLoginClick) }
        } else {
            item { WanDialCard(state) }
            item { ClientStatCard(state) }
            item { TrafficScopeCard(state) }
            item { ApnCard(state) }
            item { WifiNetCard(state) }
            item { WifiIsolateCard(state) }
            item { DdnsCard(state) }
            item { TunnelCard(state) }
            item { SecondAuthCard(state) }
            item { LanCtlCard(state) }
        }
    }
}

// ─────────────────────────── 未登录占位 ───────────────────────────

@Composable
private fun NeedLoginCard(onLoginClick: () -> Unit) {
    PanelCard(title = "Network details", accent = Amber) {
        HintBox(
            "Dial parameters, APN, DDNS, VPN and client statistics on this page are management-plane data " +
                "and require an admin sign-in to read. Without it only this page is hidden; live monitoring above still works.",
            TextLo
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onLoginClick) {
            Text("Sign in now", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────── 拨号与 WAN ───────────────────────────

@Composable
private fun WanDialCard(state: CpeState) {
    val para = state.wanModePara
    val iface = state.wanIface

    if (para == null && iface == null) {
        PanelCard(title = "Dial-up & WAN link", accent = Cyan) {
            HintBox(
                state.authErrors[Api.WAN_IFACE.key]
                    ?: state.authErrors[Api.WAN_MODE_PARA.key]
                    ?: "Reading dial parameters…",
                Amber
            )
        }
        return
    }

    val connStatus = iface?.s("connect_status") ?: ""
    PanelCard(
        title = "Dial-up & WAN link",
        accent = Cyan,
        trailing = {
            if (connStatus.isNotBlank()) {
                Pill(
                    wanStatusText(connStatus),
                    if (connStatus.contains("connected")) Mint else Amber
                )
            }
        }
    ) {
        para?.let { p ->
            KV("Dial mode", wanModeText(p.s("opms_wan_mode"), p.s("opms_wan_auto_mode")))
            val err = p.s("error_code")
            if (err.isNotBlank()) KV("Dial error code", err, Rose)

            val pppoeUser = p.digS("pppoe_wan_mode_info.username")
            if (pppoeUser.isNotBlank()) KV("PPPoE user", pppoeUser)
            val keepalive = p.digS("pppoe_wan_mode_info.keepalive")
            if (keepalive.isNotBlank()) KV("PPPoE keepalive", keepalive)

            val sIp = p.digS("static_wan_mode_info.ipaddr")
            if (sIp.isNotBlank() && sIp != "0.0.0.0") {
                KV("Static IP", sIp)
                KV("Static gateway", p.digS("static_wan_mode_info.gateway"))
            }
            val mvStatus = p.digS("pppoe_move_info.pppoe_move_status")
            if (mvStatus.isNotBlank()) KV("Migration status", mvStatus)
            val passMac = p.digS("bridge_wan_mode_info.wan_ippass_device_mac")
            if (passMac.isNotBlank()) KV("IP passthrough device", passMac)
        }

        iface?.let { f ->
            Spacer(Modifier.height(6.dp))
            KV("Connection mode", connectModeText(f.i("connect_mode")))
            KV("Link state", f.s("roll_connect_status"))
            val fail = f.i("connect_fail_count")
            KV("Reconnects", fail.toString(), if (fail > 3) Amber else TextHi)
            KV("Roaming", if (f.i("roam_enable") == 1) "On" else "Off")

            Spacer(Modifier.height(6.dp))
            val v4 = f.s("ipv4_address")
            KV("IPv4 address", v4, if (v4.isBlank()) TextHi else Cyan)
            KV("IPv4 gateway", f.s("ipv4_gateway"))
            KV("Subnet mask", f.s("ipv4_netmask"))
            KV("Primary DNS", f.s("ipv4_dns_prefer"))
            KV("Secondary DNS", f.s("ipv4_dns_standby"))

            val v6 = f.s("ipv6_address")
            if (v6.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                KV("IPv6 address", v6, Cyan)
                KV("IPv6 gateway", f.s("ipv6_gateway"))
                KV("IPv6 DNS", f.s("ipv6_dns_prefer"))
            }

            Spacer(Modifier.height(6.dp))
            KV("QCI", f.i("qci").toString())
            val dl = f.l("dl_ambr")
            val ul = f.l("ul_ambr")
            if (dl > 0) KV("Downlink (contract)", rateText(dl * 1000L))
            if (ul > 0) KV("Uplink (contract)", rateText(ul * 1000L))
        }
    }
}

// ─────────────────────────── 终端统计 ───────────────────────────

@Composable
private fun ClientStatCard(state: CpeState) {
    val n = state.userNum
    if (n == null) {
        PanelCard(title = "Client stats", accent = Violet) {
            HintBox(state.authErrors[Api.USER_NUM.key] ?: "Counting clients…", Amber)
        }
        return
    }
    PanelCard(title = "Client stats", accent = Violet) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Online", n.i("access_total_num").toString(), "", Cyan, Modifier.weight(1f))
            MetricTile("Wired", n.i("lan_num").toString(), "", Blue, Modifier.weight(1f))
            MetricTile("Wireless", n.i("wireless_num").toString(), "", Violet, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Offline", n.i("offline_num").toString(), "", TextMu, Modifier.weight(1f))
            MetricTile("Guest 2.4G", n.i("guest_num_24g").toString(), "", Amber, Modifier.weight(1f))
            MetricTile("Guest 5G", n.i("guest_num_5g").toString(), "", Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        HintBox("Counts come from the device's own connection-tracking table and match the client list on the Device tab.", TextLo)
    }
}

// ─────────────────────────── 流量统计口径 ───────────────────────────

@Composable
private fun TrafficScopeCard(state: CpeState) {
    val cd = state.trafficClearDay
    val lim = state.trafficLimit

    PanelCard(title = "Data accounting", accent = Mint) {
        if (cd == null && lim == null) {
            HintBox(state.authErrors[Api.TRAFFIC_CLEARDAY.key] ?: "Reading…", Amber)
            return@PanelCard
        }
        cd?.let {
            val enabled = it.b("enable")
            KV("Auto reset", if (enabled) "On" else "Off", if (enabled) Mint else TextLo)
            if (enabled) KV("Reset day", "Day ${it.i("clearday")} of each month")
        }
        lim?.let {
            val on = it.b("enable")
            KV("Plan quota", if (on) "Set" else "Not set", if (on) Mint else TextLo)
            if (on) {
                val v = it.l("value")
                KV("Quota", bytesText(v))
                KV("Billing period", if (it.i("type") == 2) "Monthly" else "One-off")
                KV("Alert at", "${it.i("ratio")}%")
                val over = it.i("overflow")
                if (over > 0) KV("Over-limit count", over.toString(), Amber)
            }
        }
        Spacer(Modifier.height(6.dp))
        val month = state.trafficMonth
        if (month != null) {
            val rx = month.l("month_rx_bytes")
            val tx = month.l("month_tx_bytes")
            if (rx > 0 || tx > 0) KV("This month", "↑ ${bytesText(tx)} / ↓ ${bytesText(rx)}")
        }
        Spacer(Modifier.height(10.dp))
        HintBox("“This month” is reported by the device itself; if it shows 0 while the plan progress bar has data, the device's accounting module simply hasn't refreshed.", TextLo)
    }
}

// ─────────────────────────── APN ───────────────────────────

@Composable
private fun ApnCard(state: CpeState) {
    val mode = state.apnMode
    val cur = state.apnAtCid
    val auto = state.apnAuto?.optJSONArray("apnListArray")
    val manu = state.apnManu?.optJSONArray("apnListArray")

    if (mode == null && cur == null && auto == null && manu == null) {
        PanelCard(title = "APN", accent = Blue) {
            HintBox(state.authErrors[Api.APN_MODE.key] ?: "Reading APN config…", Amber)
        }
        return
    }

    PanelCard(
        title = "APN",
        accent = Blue,
        trailing = {
            val m = mode?.i("apn_mode") ?: 0
            Pill(if (m == 0) "Auto" else "Manual", if (m == 0) Mint else Amber)
        }
    ) {
        cur?.let { c ->
            KV("Profile", c.s("profilename"))
            KV("APN", c.s("wanapn").ifBlank { "(carrier default)" }, Cyan)
            KV("PDP type", pdpText(c.i("pdpType")))
            KV("Auth", pppAuthText(c.i("pppAuthMode")))
            KV("Profile ID", c.s("profileId"))
            KV("Enabled", if (c.b("isEnable")) "Yes" else "No")
        }

        val autoN = auto?.length() ?: 0
        val manuN = manu?.length() ?: 0
        if (autoN > 0 || manuN > 0) {
            Spacer(Modifier.height(8.dp))
            PanelDivider()
            Spacer(Modifier.height(8.dp))
            KV("Auto entries", autoN.toString())
            KV("Manual entries", manuN.toString())
        }

        if (autoN > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Auto-detected access points", color = TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until minOf(autoN, 6)) {
                val o = auto?.optJSONObject(idx) ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.s("profilename").ifBlank { "Unnamed" },
                        color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        o.s("wanapn").ifBlank { "—" },
                        color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBox("When the carrier pushes an APN the device matches it automatically; manually added profiles show up under “Manual entries”.", TextLo)
    }
}

// ─────────────────────────── Wi-Fi 网络 ───────────────────────────

@Composable
private fun WifiNetCard(state: CpeState) {
    val ifaces = state.wlanIface?.optJSONArray("ifaces")
    if (ifaces == null) {
        PanelCard(title = "Wi-Fi networks", accent = Violet) {
            HintBox(state.authErrors[Api.WLAN_IFACE.key] ?: "Reading Wi-Fi interfaces…", Amber)
        }
        return
    }
    val n = ifaces.length()

    PanelCard(
        title = "Wi-Fi networks",
        accent = Violet,
        trailing = { Pill("$n SSIDs", Violet) }
    ) {
        for (idx in 0 until n) {
            val o = ifaces.optJSONObject(idx) ?: continue
            if (idx > 0) {
                Spacer(Modifier.height(10.dp))
                PanelDivider()
                Spacer(Modifier.height(10.dp))
            }
            val section = o.s("section_name")
            val disabled = o.i("disabled") == 1
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        sectionLabel(section),
                        color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        o.s("ssid").ifBlank { "(unnamed)" },
                        color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                Pill(if (disabled) "Off" else "On", if (disabled) TextMu else Mint)
            }
            Spacer(Modifier.height(6.dp))
            KV("Encryption", encText(o.s("encryption")))
            KV("SSID broadcast", if (o.i("hidden") == 1) "Hidden" else "Visible")
            KV("Max clients", o.i("maxassoc").toString())
        }

        val wps = state.wlanWps?.optJSONArray("wifi_wps_status_info")
        if (wps != null && wps.length() > 0) {
            Spacer(Modifier.height(12.dp))
            PanelDivider()
            Spacer(Modifier.height(10.dp))
            Text("WPS status", color = TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until wps.length()) {
                val o = wps.optJSONObject(idx) ?: continue
                val st = o.s("wps_status")
                KV(sectionLabel(o.s("section_name")), wpsText(st), if (st == "idle") TextHi else Amber)
            }
        }

        val guest = state.wlanGuestTime?.optJSONArray("wlan_guest_left_time_info")
        if (guest != null && guest.length() > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Guest network time left", color = TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until guest.length()) {
                val o = guest.optJSONObject(idx) ?: continue
                KV(sectionLabel(o.s("section_name")), o.s("left_time").ifBlank { o.s("time") })
            }
        }
    }
}

// ─────────────────────────── Wi-Fi 隔离 ───────────────────────────

@Composable
private fun WifiIsolateCard(state: CpeState) {
    val iso = state.wlanIsolate
    if (iso == null) {
        PanelCard(title = "Wi-Fi isolation", accent = Amber) {
            HintBox(state.authErrors[Api.WLAN_ISOLATE.key] ?: "Reading isolation settings…", Amber)
        }
        return
    }
    PanelCard(title = "Wi-Fi isolation", accent = Amber) {
        val items = listOf(
            "wifi_isolate_enable" to "Master switch",
            "main_guest_enable" to "Main/guest network access",
            "lan_wifi_main_enable" to "Wired → main Wi-Fi",
            "lan_wifi_guest_enable" to "Wired → guest Wi-Fi",
            "wifimain24_wifimain5_enable" to "Main 2.4G ↔ 5G",
            "wifiguest24_wifiguest5_enable" to "Guest 2.4G ↔ 5G",
            "guest_access_restrict_enable" to "Guest access restriction",
            "main24_ap_isolation_enable" to "2.4G AP isolation",
            "main5_ap_isolation_enable" to "5G AP isolation",
            "main24_privacy_separator_enable" to "2.4G privacy separation",
            "main5_privacy_separator_enable" to "5G privacy separation"
        )
        for ((k, label) in items) {
            val on = iso.i(k) == 1
            KV(label, if (on) "On" else "Off", if (on) Rose else TextHi)
        }
        Spacer(Modifier.height(10.dp))
        HintBox("“On” means that isolation is in effect (clients cannot see each other). Red marks a separated network, which is a normal security setup.", TextLo)
    }
}

// ─────────────────────────── DDNS ───────────────────────────

@Composable
private fun DdnsCard(state: CpeState) {
    val d = state.ddns
    if (d == null) {
        PanelCard(title = "DDNS", accent = Cyan) {
            HintBox(state.authErrors[Api.DDNS.key] ?: "Reading DDNS…", Amber)
        }
        return
    }
    val on = d.i("enable") == 1
    val status = d.s("status")
    PanelCard(
        title = "DDNS",
        accent = Cyan,
        trailing = {
            Pill(
                if (on) ddnsStatusText(status) else "Off",
                when {
                    !on -> TextMu
                    status == "registered" -> Mint
                    status == "not_registered" -> Amber
                    else -> Rose
                }
            )
        }
    ) {
        KV("Service state", if (on) "On" else "Off")
        KV("Update mode", if (d.s("mode") == "auto") "Auto" else "Manual")
        val svc = d.s("service")
        if (svc.isNotBlank()) KV("Provider", svc)
        val domain = d.s("domain")
        if (domain.isNotBlank()) KV("Domain", domain, Cyan)
        val hash = d.s("hash")
        if (hash.isNotBlank()) KV("Host ID", hash)
        val acc = d.s("accout")
        KV("Account", if (acc.isBlank()) "(not set)" else "Set (stored encrypted)")
        val providers = d.optJSONArray("providers")
        if (providers != null && providers.length() > 0) {
            KV("Providers", providers.length().toString())
        }
        if (!on) {
            Spacer(Modifier.height(10.dp))
            HintBox("DDNS binds a dynamic public IP to a fixed domain; you only need it on home broadband without a static IP.", TextLo)
        }
    }
}

// ─────────────────────────── VPN 隧道 ───────────────────────────

@Composable
private fun TunnelCard(state: CpeState) {
    val t = state.tunnel
    if (t == null) {
        PanelCard(title = "VPN tunnels", accent = Blue) {
            HintBox(state.authErrors[Api.TUNNEL.key] ?: "Reading tunnel config…", Amber)
        }
        return
    }
    val kinds = listOf(
        "pptp" to "PPTP",
        "l2tp" to "L2TP",
        "gre" to "GRE",
        "ipsec" to "IPSec",
        "vxlan" to "VXLAN",
        "openvpn" to "OpenVPN",
        "wireguard" to "WireGuard",
        "l2tpv3" to "L2TPv3"
    )
    PanelCard(
        title = "VPN tunnels",
        accent = Blue,
        trailing = { Pill("Current ${t.s("cur_type").uppercase()}", Blue) }
    ) {
        for ((k, label) in kinds) {
            val sub = t.optJSONObject(k) ?: continue
            val st = sub.s("connect_status")
            val server = sub.s("server_address")
            val running = st.contains("connected", true) && !st.contains("disconnected", true)
            KV(
                label,
                when {
                    st.isBlank() && server.isBlank() -> "Not configured"
                    st.isBlank() -> server
                    else -> "${tunnelText(st)}${if (server.isBlank()) "" else " · $server"}"
                },
                when {
                    st.isBlank() -> TextMu
                    running -> Mint
                    else -> TextHi
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        HintBox("This only displays the device's current tunnel configuration; the app never changes VPN settings.", TextLo)
    }
}

// ─────────────────────────── 上网认证 ───────────────────────────

@Composable
private fun SecondAuthCard(state: CpeState) {
    val succ = state.secondAuthSucc?.optJSONArray("second_auth_succ_list_info")
    val fail = state.secondAuthFail?.optJSONArray("second_auth_fail_list_info")
    if (succ == null && fail == null) {
        PanelCard(title = "Web authentication", accent = Violet) {
            HintBox(state.authErrors[Api.SECOND_AUTH_SUCC.key] ?: "Reading auth list…", Amber)
        }
        return
    }
    val nOk = succ?.length() ?: 0
    val nFail = fail?.length() ?: 0

    PanelCard(
        title = "Web authentication",
        accent = Violet,
        trailing = { Pill("$nOk passed / $nFail pending", if (nFail > 0) Amber else Mint) }
    ) {
        if (nOk == 0 && nFail == 0) {
            HintBox("No client is using web authentication right now (only campus or hotel broadband portals need it).", TextLo)
            return@PanelCard
        }
        if (nFail > 0) {
            Text("Pending clients", color = Amber, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until nFail) {
                val o = fail?.optJSONObject(idx) ?: continue
                KV(
                    o.s("hostname").ifBlank { "Unknown device" },
                    o.s("mac_address"),
                    Rose
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        if (nOk > 0) {
            Text("Authenticated clients", color = Mint, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until nOk) {
                val o = succ?.optJSONObject(idx) ?: continue
                KV(
                    o.s("hostname").ifBlank { "Unknown device" },
                    o.s("mac_address"),
                    Mint
                )
            }
        }
    }
}

// ─────────────────────────── LAN 主机名 / MAC 控制 ───────────────────────────

@Composable
private fun LanCtlCard(state: CpeState) {
    val hosts = state.lanHostname?.optJSONArray("devices")
    val macs = state.macPctrl?.optJSONArray("macs")
    if (hosts == null && macs == null) {
        PanelCard(title = "LAN & access control", accent = Cyan) {
            HintBox(state.authErrors[Api.LAN_HOSTNAME.key] ?: "Reading…", Amber)
        }
        return
    }
    PanelCard(title = "LAN & access control", accent = Cyan) {
        val hn = hosts?.length() ?: 0
        KV("Custom hostnames", if (hn == 0) "None" else "$hn devices renamed", if (hn == 0) TextLo else Mint)
        if (hn > 0) {
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until minOf(hn, 8)) {
                val o = hosts?.optJSONObject(idx) ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.s("hostname").ifBlank { "Unnamed" },
                        color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1
                    )
                    Text(o.s("mac"), color = TextMu, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PanelDivider()
        Spacer(Modifier.height(10.dp))
        val mn = macs?.length() ?: 0
        KV("MAC access entries", if (mn == 0) "Not set" else "$mn", if (mn == 0) TextLo else Amber)
        if (mn > 0) {
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until minOf(mn, 8)) {
                val o = macs?.optJSONObject(idx) ?: continue
                KV(o.s("mac").ifBlank { o.s("mac_address") }, o.s("hostname").ifBlank { o.s("remark") })
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBox("Hostnames are the aliases you set in the web UI; MAC access entries are the active allow/deny list rules.", TextLo)
    }
}

// ─────────────────────────── 小工具 ───────────────────────────

@Composable
private fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Stroke)
    )
}

/** bps -> 人类可读速率 */
private fun rateText(bps: Long): String = when {
    bps >= 1_000_000_000L -> "%.2f Gbps".format(bps / 1_000_000_000.0)
    bps >= 1_000_000L -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000L -> "%.0f Kbps".format(bps / 1_000.0)
    else -> "$bps bps"
}

/** main_2g -> Main Wi-Fi 2.4G */
private fun sectionLabel(section: String): String = when (section.lowercase()) {
    "main_2g" -> "Main Wi-Fi · 2.4G"
    "main_5g" -> "Main Wi-Fi · 5G"
    "main_6g" -> "Main Wi-Fi · 6G"
    "guest_2g" -> "Guest network · 2.4G"
    "guest_5g" -> "Guest network · 5G"
    "guest_6g" -> "Guest network · 6G"
    "backhaul_5g" -> "Mesh backhaul · 5G"
    "backhaul_2g" -> "Mesh backhaul · 2.4G"
    "backhaul_sta" -> "Mesh backhaul (STA)"
    else -> section.ifBlank { "Unknown interface" }
}

/** 加密方式代号 -> 人话 */
private fun encText(enc: String): String = when (enc.lowercase().trim()) {
    "", "none" -> "Open (no password)"
    "psk2+ccmp" -> "WPA2-PSK (CCMP)"
    "psk+ccmp" -> "WPA-PSK (CCMP)"
    "psk-mixed+tkip+ccmp" -> "WPA/WPA2 mixed"
    "sae" -> "WPA3-SAE"
    "sae-mixed" -> "WPA2/WPA3 mixed"
    "owe" -> "WPA3-OWE (enhanced open)"
    "wep" -> "WEP (legacy)"
    else -> enc
}

/** 拨号连接方式 */
private fun connectModeText(mode: Int): String = when (mode) {
    1 -> "Auto"
    2 -> "Manual"
    3 -> "On demand"
    else -> "Mode $mode"
}

/** PDP 类型 */
private fun pdpText(t: Int): String = when (t) {
    0 -> "IPv4"
    1 -> "IPv6"
    2 -> "IPv4v6"
    3 -> "IPv4v6 (default)"
    else -> "Type $t"
}

/** PPP 鉴权方式 */
private fun pppAuthText(a: Int): String = when (a) {
    0 -> "None"
    1 -> "PAP"
    2 -> "CHAP"
    3 -> "PAP/CHAP"
    else -> "Mode $a"
}

private fun wpsText(s: String): String = when (s.lowercase()) {
    "idle", "" -> "Idle"
    "active" -> "Pairing"
    "success" -> "Paired"
    "fail" -> "Failed"
    else -> s
}

private fun ddnsStatusText(s: String): String = when (s.lowercase()) {
    "registered" -> "Registered"
    "not_registered" -> "Not registered"
    "registering" -> "Registering"
    "fail", "failed" -> "Registration failed"
    "" -> "Unknown"
    else -> s
}

private fun tunnelText(s: String): String = when (s.lowercase()) {
    "connected" -> "Connected"
    "disconnected" -> "Disconnected"
    "connecting" -> "Connecting"
    "" -> "Not configured"
    else -> s
}
