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
    PanelCard(title = "网络详情", accent = Amber) {
        HintBox(
            "这一页的拨号参数、APN、DDNS、VPN、终端统计等都属于设备的管理面数据，" +
                "需要先登录管理后台才能读取。未登录时只影响显示，不影响上面的实时监控。",
            TextLo
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onLoginClick) {
            Text("立即登录", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────── 拨号与 WAN ───────────────────────────

@Composable
private fun WanDialCard(state: CpeState) {
    val para = state.wanModePara
    val iface = state.wanIface

    if (para == null && iface == null) {
        PanelCard(title = "拨号与 WAN 链路", accent = Cyan) {
            HintBox(
                state.authErrors[Api.WAN_IFACE.key]
                    ?: state.authErrors[Api.WAN_MODE_PARA.key]
                    ?: "正在读取拨号参数…",
                Amber
            )
        }
        return
    }

    val connStatus = iface?.s("connect_status") ?: ""
    PanelCard(
        title = "拨号与 WAN 链路",
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
            KV("拨号模式", wanModeText(p.s("opms_wan_mode"), p.s("opms_wan_auto_mode")))
            val err = p.s("error_code")
            if (err.isNotBlank()) KV("拨号错误码", err, Rose)

            val pppoeUser = p.digS("pppoe_wan_mode_info.username")
            if (pppoeUser.isNotBlank()) KV("PPPoE 账号", pppoeUser)
            val keepalive = p.digS("pppoe_wan_mode_info.keepalive")
            if (keepalive.isNotBlank()) KV("PPPoE keepalive", keepalive)

            val sIp = p.digS("static_wan_mode_info.ipaddr")
            if (sIp.isNotBlank() && sIp != "0.0.0.0") {
                KV("静态 IP", sIp)
                KV("静态网关", p.digS("static_wan_mode_info.gateway"))
            }
            val mvStatus = p.digS("pppoe_move_info.pppoe_move_status")
            if (mvStatus.isNotBlank()) KV("拨号迁移状态", mvStatus)
            val passMac = p.digS("bridge_wan_mode_info.wan_ippass_device_mac")
            if (passMac.isNotBlank()) KV("IP 穿透设备", passMac)
        }

        iface?.let { f ->
            Spacer(Modifier.height(6.dp))
            KV("连接方式", connectModeText(f.i("connect_mode")))
            KV("链路状态", f.s("roll_connect_status"))
            val fail = f.i("connect_fail_count")
            KV("重连次数", fail.toString(), if (fail > 3) Amber else TextHi)
            KV("漫游", if (f.i("roam_enable") == 1) "已开启" else "已关闭")

            Spacer(Modifier.height(6.dp))
            val v4 = f.s("ipv4_address")
            KV("IPv4 地址", v4, if (v4.isBlank()) TextHi else Cyan)
            KV("IPv4 网关", f.s("ipv4_gateway"))
            KV("子网掩码", f.s("ipv4_netmask"))
            KV("首选 DNS", f.s("ipv4_dns_prefer"))
            KV("备用 DNS", f.s("ipv4_dns_standby"))

            val v6 = f.s("ipv6_address")
            if (v6.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                KV("IPv6 地址", v6, Cyan)
                KV("IPv6 网关", f.s("ipv6_gateway"))
                KV("IPv6 DNS", f.s("ipv6_dns_prefer"))
            }

            Spacer(Modifier.height(6.dp))
            KV("QCI 等级", f.i("qci").toString())
            val dl = f.l("dl_ambr")
            val ul = f.l("ul_ambr")
            if (dl > 0) KV("签约下行", rateText(dl * 1000L))
            if (ul > 0) KV("签约上行", rateText(ul * 1000L))
        }
    }
}

// ─────────────────────────── 终端统计 ───────────────────────────

@Composable
private fun ClientStatCard(state: CpeState) {
    val n = state.userNum
    if (n == null) {
        PanelCard(title = "终端统计", accent = Violet) {
            HintBox(state.authErrors[Api.USER_NUM.key] ?: "正在统计在线终端…", Amber)
        }
        return
    }
    PanelCard(title = "终端统计", accent = Violet) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("在线总数", n.i("access_total_num").toString(), "台", Cyan, Modifier.weight(1f))
            MetricTile("有线", n.i("lan_num").toString(), "台", Blue, Modifier.weight(1f))
            MetricTile("无线", n.i("wireless_num").toString(), "台", Violet, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("离线", n.i("offline_num").toString(), "台", TextMu, Modifier.weight(1f))
            MetricTile("访客 2.4G", n.i("guest_num_24g").toString(), "台", Amber, Modifier.weight(1f))
            MetricTile("访客 5G", n.i("guest_num_5g").toString(), "台", Amber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        HintBox("统计口径来自设备自身的连接跟踪表，与设备页的在线终端明细一一对应。", TextLo)
    }
}

// ─────────────────────────── 流量统计口径 ───────────────────────────

@Composable
private fun TrafficScopeCard(state: CpeState) {
    val cd = state.trafficClearDay
    val lim = state.trafficLimit

    PanelCard(title = "流量统计口径", accent = Mint) {
        if (cd == null && lim == null) {
            HintBox(state.authErrors[Api.TRAFFIC_CLEARDAY.key] ?: "正在读取…", Amber)
            return@PanelCard
        }
        cd?.let {
            val enabled = it.b("enable")
            KV("自动清零", if (enabled) "已开启" else "未开启", if (enabled) Mint else TextLo)
            if (enabled) KV("清零日", "每月 ${it.i("clearday")} 日")
        }
        lim?.let {
            val on = it.b("enable")
            KV("套餐限额", if (on) "已设置" else "未设置", if (on) Mint else TextLo)
            if (on) {
                val v = it.l("value")
                KV("限额数值", bytesText(v))
                KV("计费类型", if (it.i("type") == 2) "按月" else "按次")
                KV("告警阈值", "${it.i("ratio")}%")
                val over = it.i("overflow")
                if (over > 0) KV("超量次数", over.toString(), Amber)
            }
        }
        Spacer(Modifier.height(6.dp))
        val month = state.trafficMonth
        if (month != null) {
            val rx = month.l("month_rx_bytes")
            val tx = month.l("month_tx_bytes")
            if (rx > 0 || tx > 0) KV("本月累计", "↑ ${bytesText(tx)} / ↓ ${bytesText(rx)}")
        }
        Spacer(Modifier.height(10.dp))
        HintBox("「本月累计」由设备自己上报；若这里显示 0 而套餐进度条有数据，说明设备端统计模块未刷新，属设备行为。", TextLo)
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
        PanelCard(title = "APN 接入点", accent = Blue) {
            HintBox(state.authErrors[Api.APN_MODE.key] ?: "正在读取 APN 配置…", Amber)
        }
        return
    }

    PanelCard(
        title = "APN 接入点",
        accent = Blue,
        trailing = {
            val m = mode?.i("apn_mode") ?: 0
            Pill(if (m == 0) "自动" else "手动", if (m == 0) Mint else Amber)
        }
    ) {
        cur?.let { c ->
            KV("配置名", c.s("profilename"))
            KV("APN", c.s("wanapn").ifBlank { "（运营商默认）" }, Cyan)
            KV("PDP 类型", pdpText(c.i("pdpType")))
            KV("鉴权方式", pppAuthText(c.i("pppAuthMode")))
            KV("Profile ID", c.s("profileId"))
            KV("启用状态", if (c.b("isEnable")) "已启用" else "未启用")
        }

        val autoN = auto?.length() ?: 0
        val manuN = manu?.length() ?: 0
        if (autoN > 0 || manuN > 0) {
            Spacer(Modifier.height(8.dp))
            PanelDivider()
            Spacer(Modifier.height(8.dp))
            KV("自动配置条目", autoN.toString())
            KV("手动配置条目", manuN.toString())
        }

        if (autoN > 0) {
            Spacer(Modifier.height(6.dp))
            Text("自动识别到的接入点", color = TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until minOf(autoN, 6)) {
                val o = auto?.optJSONObject(idx) ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.s("profilename").ifBlank { "未命名" },
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
        HintBox("APN 由运营商下发时，设备会自动匹配；手动新增的配置会出现在「手动配置条目」里。", TextLo)
    }
}

// ─────────────────────────── Wi-Fi 网络 ───────────────────────────

@Composable
private fun WifiNetCard(state: CpeState) {
    val ifaces = state.wlanIface?.optJSONArray("ifaces")
    if (ifaces == null) {
        PanelCard(title = "Wi-Fi 网络", accent = Violet) {
            HintBox(state.authErrors[Api.WLAN_IFACE.key] ?: "正在读取 Wi-Fi 接口…", Amber)
        }
        return
    }
    val n = ifaces.length()

    PanelCard(
        title = "Wi-Fi 网络",
        accent = Violet,
        trailing = { Pill("$n 个 SSID", Violet) }
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
                        o.s("ssid").ifBlank { "（未命名）" },
                        color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                Pill(if (disabled) "已关闭" else "已启用", if (disabled) TextMu else Mint)
            }
            Spacer(Modifier.height(6.dp))
            KV("加密方式", encText(o.s("encryption")))
            KV("SSID 广播", if (o.i("hidden") == 1) "已隐藏" else "可见")
            KV("最大终端数", o.i("maxassoc").toString())
        }

        val wps = state.wlanWps?.optJSONArray("wifi_wps_status_info")
        if (wps != null && wps.length() > 0) {
            Spacer(Modifier.height(12.dp))
            PanelDivider()
            Spacer(Modifier.height(10.dp))
            Text("WPS 配对状态", color = TextLo, fontSize = 12.sp)
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
            Text("访客网络剩余时长", color = TextLo, fontSize = 12.sp)
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
        PanelCard(title = "Wi-Fi 隔离与互访", accent = Amber) {
            HintBox(state.authErrors[Api.WLAN_ISOLATE.key] ?: "正在读取隔离设置…", Amber)
        }
        return
    }
    PanelCard(title = "Wi-Fi 隔离与互访", accent = Amber) {
        val items = listOf(
            "wifi_isolate_enable" to "总开关",
            "main_guest_enable" to "主/访客网络互访",
            "lan_wifi_main_enable" to "有线访问主 Wi-Fi",
            "lan_wifi_guest_enable" to "有线访问访客 Wi-Fi",
            "wifimain24_wifimain5_enable" to "主 Wi-Fi 2.4G/5G 互访",
            "wifiguest24_wifiguest5_enable" to "访客 2.4G/5G 互访",
            "guest_access_restrict_enable" to "访客访问限制",
            "main24_ap_isolation_enable" to "2.4G AP 隔离",
            "main5_ap_isolation_enable" to "5G AP 隔离",
            "main24_privacy_separator_enable" to "2.4G 隐私隔离",
            "main5_privacy_separator_enable" to "5G 隐私隔离"
        )
        for ((k, label) in items) {
            val on = iso.i(k) == 1
            KV(label, if (on) "已开启" else "已关闭", if (on) Rose else TextHi)
        }
        Spacer(Modifier.height(10.dp))
        HintBox("「已开启」表示该隔离项生效（设备间互相不可见）。红色代表网络被分隔，属正常安全配置。", TextLo)
    }
}

// ─────────────────────────── DDNS ───────────────────────────

@Composable
private fun DdnsCard(state: CpeState) {
    val d = state.ddns
    if (d == null) {
        PanelCard(title = "DDNS 动态域名", accent = Cyan) {
            HintBox(state.authErrors[Api.DDNS.key] ?: "正在读取 DDNS…", Amber)
        }
        return
    }
    val on = d.i("enable") == 1
    val status = d.s("status")
    PanelCard(
        title = "DDNS 动态域名",
        accent = Cyan,
        trailing = {
            Pill(
                if (on) ddnsStatusText(status) else "未启用",
                when {
                    !on -> TextMu
                    status == "registered" -> Mint
                    status == "not_registered" -> Amber
                    else -> Rose
                }
            )
        }
    ) {
        KV("服务状态", if (on) "已启用" else "未启用")
        KV("更新模式", if (d.s("mode") == "auto") "自动" else "手动")
        val svc = d.s("service")
        if (svc.isNotBlank()) KV("服务商", svc)
        val domain = d.s("domain")
        if (domain.isNotBlank()) KV("域名", domain, Cyan)
        val hash = d.s("hash")
        if (hash.isNotBlank()) KV("主机标识", hash)
        val acc = d.s("accout")
        KV("账号", if (acc.isBlank()) "（未填写）" else "已填写（密文保存）")
        val providers = d.optJSONArray("providers")
        if (providers != null && providers.length() > 0) {
            KV("可选服务商", providers.length().toString() + " 家")
        }
        if (!on) {
            Spacer(Modifier.height(10.dp))
            HintBox("DDNS 用于把动态的公网 IP 绑定到一个固定域名，家庭宽带没有固定 IP 时才需要开启。", TextLo)
        }
    }
}

// ─────────────────────────── VPN 隧道 ───────────────────────────

@Composable
private fun TunnelCard(state: CpeState) {
    val t = state.tunnel
    if (t == null) {
        PanelCard(title = "VPN 隧道", accent = Blue) {
            HintBox(state.authErrors[Api.TUNNEL.key] ?: "正在读取隧道配置…", Amber)
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
        title = "VPN 隧道",
        accent = Blue,
        trailing = { Pill("当前 ${t.s("cur_type").uppercase()}", Blue) }
    ) {
        for ((k, label) in kinds) {
            val sub = t.optJSONObject(k) ?: continue
            val st = sub.s("connect_status")
            val server = sub.s("server_address")
            val running = st.contains("connected", true) && !st.contains("disconnected", true)
            KV(
                label,
                when {
                    st.isBlank() && server.isBlank() -> "未配置"
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
        HintBox("这里只是把设备当前的隧道配置读出来展示，App 不会改动任何 VPN 参数。", TextLo)
    }
}

// ─────────────────────────── 上网认证 ───────────────────────────

@Composable
private fun SecondAuthCard(state: CpeState) {
    val succ = state.secondAuthSucc?.optJSONArray("second_auth_succ_list_info")
    val fail = state.secondAuthFail?.optJSONArray("second_auth_fail_list_info")
    if (succ == null && fail == null) {
        PanelCard(title = "上网认证（二次认证）", accent = Violet) {
            HintBox(state.authErrors[Api.SECOND_AUTH_SUCC.key] ?: "正在读取认证列表…", Amber)
        }
        return
    }
    val nOk = succ?.length() ?: 0
    val nFail = fail?.length() ?: 0

    PanelCard(
        title = "上网认证（二次认证）",
        accent = Violet,
        trailing = { Pill("$nOk 通过 / $nFail 待认证", if (nFail > 0) Amber else Mint) }
    ) {
        if (nOk == 0 && nFail == 0) {
            HintBox("当前没有设备走二次认证（校园网 / 酒店宽带这类需要 Web 认证的场景才会用到）。", TextLo)
            return@PanelCard
        }
        if (nFail > 0) {
            Text("待认证设备", color = Amber, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until nFail) {
                val o = fail?.optJSONObject(idx) ?: continue
                KV(
                    o.s("hostname").ifBlank { "未知设备" },
                    o.s("mac_address"),
                    Rose
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        if (nOk > 0) {
            Text("已认证设备", color = Mint, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until nOk) {
                val o = succ?.optJSONObject(idx) ?: continue
                KV(
                    o.s("hostname").ifBlank { "未知设备" },
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
        PanelCard(title = "LAN 与访问控制", accent = Cyan) {
            HintBox(state.authErrors[Api.LAN_HOSTNAME.key] ?: "正在读取…", Amber)
        }
        return
    }
    PanelCard(title = "LAN 与访问控制", accent = Cyan) {
        val hn = hosts?.length() ?: 0
        KV("自定义主机名", if (hn == 0) "无" else "$hn 台设备已改名", if (hn == 0) TextLo else Mint)
        if (hn > 0) {
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until minOf(hn, 8)) {
                val o = hosts?.optJSONObject(idx) ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.s("hostname").ifBlank { "未命名" },
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
        KV("MAC 访问控制条目", if (mn == 0) "未设置" else "$mn 条", if (mn == 0) TextLo else Amber)
        if (mn > 0) {
            Spacer(Modifier.height(6.dp))
            for (idx in 0 until minOf(mn, 8)) {
                val o = macs?.optJSONObject(idx) ?: continue
                KV(o.s("mac").ifBlank { o.s("mac_address") }, o.s("hostname").ifBlank { o.s("remark") })
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBox("主机名是你在网页后台改过的设备别名；MAC 访问控制是黑白名单里已生效的条目。", TextLo)
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

/** main_2g -> 主 Wi-Fi 2.4G */
private fun sectionLabel(section: String): String = when (section.lowercase()) {
    "main_2g" -> "主 Wi-Fi · 2.4G"
    "main_5g" -> "主 Wi-Fi · 5G"
    "main_6g" -> "主 Wi-Fi · 6G"
    "guest_2g" -> "访客网络 · 2.4G"
    "guest_5g" -> "访客网络 · 5G"
    "guest_6g" -> "访客网络 · 6G"
    "backhaul_5g" -> "Mesh 回程 · 5G"
    "backhaul_2g" -> "Mesh 回程 · 2.4G"
    "backhaul_sta" -> "Mesh 回程（STA）"
    else -> section.ifBlank { "未知接口" }
}

/** 加密方式代号 -> 人话 */
private fun encText(enc: String): String = when (enc.lowercase().trim()) {
    "", "none" -> "开放（无密码）"
    "psk2+ccmp" -> "WPA2-PSK (CCMP)"
    "psk+ccmp" -> "WPA-PSK (CCMP)"
    "psk-mixed+tkip+ccmp" -> "WPA/WPA2 混合"
    "sae" -> "WPA3-SAE"
    "sae-mixed" -> "WPA2/WPA3 混合"
    "owe" -> "WPA3-OWE（增强开放）"
    "wep" -> "WEP（已过时）"
    else -> enc
}

/** 拨号连接方式 */
private fun connectModeText(mode: Int): String = when (mode) {
    1 -> "自动拨号"
    2 -> "手动拨号"
    3 -> "按需拨号"
    else -> "方式 $mode"
}

/** PDP 类型 */
private fun pdpText(t: Int): String = when (t) {
    0 -> "IPv4"
    1 -> "IPv6"
    2 -> "IPv4v6"
    3 -> "IPv4v6（默认）"
    else -> "类型 $t"
}

/** PPP 鉴权方式 */
private fun pppAuthText(a: Int): String = when (a) {
    0 -> "无"
    1 -> "PAP"
    2 -> "CHAP"
    3 -> "PAP/CHAP"
    else -> "方式 $a"
}

private fun wpsText(s: String): String = when (s.lowercase()) {
    "idle", "" -> "空闲"
    "active" -> "配对中"
    "success" -> "配对成功"
    "fail" -> "配对失败"
    else -> s
}

private fun ddnsStatusText(s: String): String = when (s.lowercase()) {
    "registered" -> "已注册"
    "not_registered" -> "未注册"
    "registering" -> "注册中"
    "fail", "failed" -> "注册失败"
    "" -> "未知"
    else -> s
}

private fun tunnelText(s: String): String = when (s.lowercase()) {
    "connected" -> "已连接"
    "disconnected" -> "未连接"
    "connecting" -> "连接中"
    "" -> "未配置"
    else -> s
}
