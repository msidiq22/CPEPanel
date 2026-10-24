package com.cpepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.cpe.Api
import com.cpepanel.cpe.J.b
import com.cpepanel.cpe.J.digL
import com.cpepanel.cpe.J.digS
import com.cpepanel.cpe.J.i
import com.cpepanel.cpe.J.l
import com.cpepanel.cpe.J.s
import com.cpepanel.data.CpeState
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Blue
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.Panel
import com.cpepanel.ui.theme.PanelHi
import com.cpepanel.ui.theme.Rose
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu
import com.cpepanel.ui.theme.Violet
import org.json.JSONObject

@Composable
fun DeviceTab(state: CpeState, onSwitchSim: (Int) -> Unit = {}) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { DeviceCard(state) }
        item { ClientsCard(state) }
        item { WifiKeyCard(state) }
        item { SimCard(state, onSwitchSim) }
        item { SmsCard(state) }
        item { RuntimeCard(state.deviceInfo) }
        item { PowerCard(state) }
        item { SystemCard(state) }
        item { AboutModelCard() }
    }
}

// ─────────────────────────── 在线终端 ───────────────────────────

/**
 * 在线终端明细，来自 router_lan_access_list（实测该机型可用）。
 * 字段比 ARP 表丰富得多：主机名、接入方式、DHCP 租约时间、在线时长。
 */
@Composable
private fun ClientsCard(state: CpeState) {
    if (!state.loggedIn) return
    val data = state.authOf(Api.LAN_ACCESS)
    if (data == null) {
        PanelCard(title = "Online clients", accent = Cyan) {
            HintBox(state.authErrors[Api.LAN_ACCESS.key] ?: "Reading client list…", Amber)
        }
        return
    }
    val list = data.optJSONArray("lan_access_list_info")
    val n = list?.length() ?: 0

    PanelCard(
        title = "Online clients",
        accent = Cyan,
        trailing = {
            state.authOf(Api.USER_NUM)?.let {
                Pill("${it.i("access_total_num")} clients", Cyan)
            }
        }
    ) {
        if (n == 0) {
            HintBox("No clients connected.", TextLo)
            return@PanelCard
        }
        for (idx in 0 until n) {
            val d = list?.optJSONObject(idx) ?: continue
            if (idx > 0) {
                Spacer(Modifier.height(10.dp))
                BoxDivider()
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        d.s("hostname").ifBlank { d.s("devtype").ifBlank { "Unknown device" } },
                        color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        d.s("ip_address"),
                        color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Pill(
                    if (d.s("interface_type").contains("Ethernet", true)) "Wired" else "Wireless",
                    if (d.s("interface_type").contains("Ethernet", true)) Blue else Violet
                )
            }
            Spacer(Modifier.height(6.dp))
            KV("MAC address", d.s("mac_address"))
            KV("Connection", "${d.s("interface_type")} / ${d.s("address_source")}")
            val dur = d.l("duration_time")
            KV("Online for", uptimeText(dur), if (dur > 0) Mint else TextHi)
            KV("Connected at", d.s("access_time"))
            val ipv6 = d.s("ipv6_address")
            if (ipv6.isNotBlank()) KV("IPv6", ipv6)
        }
        Spacer(Modifier.height(10.dp))
        HintBox("Client details are merged from the device's DHCP leases and wireless association table; a newly joined device may take tens of seconds to appear.", TextLo)
    }
}

@Composable
private fun BoxDivider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(com.cpepanel.ui.theme.Stroke)
    )
}

// ─────────────────────────── Wi-Fi 密码 ───────────────────────────

/**
 * Wi-Fi 密码查看。
 *
 * 设备把 Wi-Fi 密钥做 AES-256-CBC 加密后下发（key_encode），密钥是登录后通过
 * web_crt_get + web_http_enstr_set 协商出来的会话密钥。默认隐藏，需手动点开。
 */
@Composable
private fun WifiKeyCard(state: CpeState) {
    if (!state.loggedIn) return
    var revealed by remember { mutableStateOf(false) }

    val keys = state.wifiKeys
    val sections = listOf(
        Triple("main_2g", "Main Wi-Fi 2.4G", Cyan),
        Triple("main_5g", "Main Wi-Fi 5G", Mint),
        Triple("guest_2g", "Guest Wi-Fi 2.4G", Violet),
        Triple("guest_5g", "Guest Wi-Fi 5G", Violet),
        Triple("backhaul_5g", "Mesh backhaul 5G", Amber),
        Triple("backhaul_sta", "Mesh uplink STA", Amber)
    )

    PanelCard(
        title = "Wi-Fi passwords",
        accent = Rose,
        trailing = {
            TextButton(onClick = { revealed = !revealed }) {
                Text(if (revealed) "Hide" else "Show", color = Rose, fontSize = 12.sp)
            }
        }
    ) {
        if (keys.isEmpty()) {
            HintBox(
                if (!state.cryptoReady) {
                    "The session key has not been negotiated yet, so key fields cannot be decrypted. Sign out and sign in again."
                } else {
                    state.authErrors[Api.WLAN_KEY.key] ?: "Reading keys…"
                },
                Amber
            )
            return@PanelCard
        }
        sections.forEach { (section, label, color) ->
            val plain = keys[section]
            if (plain == null) {
                KV(label, "Not configured", TextMu)
            } else if (plain.isEmpty()) {
                KV(label, "No password set", TextMu)
            } else {
                KV(
                    label,
                    if (revealed) plain else "•".repeat(plain.length.coerceAtMost(12)),
                    if (revealed) color else TextMu
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBox(
            "Passwords are decrypted locally on your phone and never uploaded anywhere.\n" +
                "For safety, this page does not offer changing them.",
            TextLo
        )
    }
}

@Composable
private fun DeviceCard(state: CpeState) {
    val sim = state.simInfo
    PanelCard(title = "Device", accent = Cyan) {
        KV("Model", "ZTE MC8830 / G5TS (5G CPE)", Cyan)
        KV("Category", "5G indoor CPE (OpenWrt based)")
        KV("Admin address", state.baseUrl, Blue)
        KV("Wi-Fi MAC", sim?.s("wlan_mac_address").orEmpty())
        KV("SIM slot", sim?.s("current_sim_slot").let { if (it.isNullOrBlank()) "—" else "Slot $it" })
    }
}

/**
 * SIM 卡信息 + 卡槽切换。
 *
 * 关于卡槽字段：官方前端读的是 `get_sim_info_before.simcard_active_slot_temp`，
 * 但真机实测该字段【不存在】，实际下发的是 `current_sim_slot`（值 "1"/"2"）。
 * 所以这里两种都读，优先用实际存在的那个。
 *
 * 切换是真实的写操作，必须二次确认 —— 会短暂断网重新注册。
 */
@Composable
private fun SimCard(state: CpeState, onSwitchSim: (Int) -> Unit) {
    val sim = state.simInfo ?: return
    val ready = sim.s("sim_states").contains("ready", true)
    val detail = state.authOf(Api.SIM_DETAIL)
    val curSlot = (detail?.s("current_sim_slot")
        ?: sim.s("current_sim_slot")).ifBlank { "1" }
    val switchStatus = (detail?.s("switch_card_status")
        ?: sim.s("switch_card_status")).ifBlank { "" }
    var confirmSlot by remember { mutableStateOf<Int?>(null) }

    PanelCard(
        title = "SIM card & slots",
        accent = if (ready) Mint else Amber,
        trailing = { Pill(sim.s("sim_states").ifBlank { "Unknown" }, if (ready) Mint else Amber) }
    ) {
        KV("Current slot", "Slot $curSlot", Cyan)
        KV("Switch status", switchStatusText(switchStatus))

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(1, 2).forEach { slot ->
                val active = slot.toString() == curSlot
                val canTap = !active && state.loggedIn
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Cyan.copy(alpha = 0.14f) else PanelHi)
                        .border(
                            1.dp,
                            if (active) Cyan.copy(alpha = 0.4f) else com.cpepanel.ui.theme.Stroke,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = canTap) { confirmSlot = slot }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (active) "Slot $slot (current)" else "Switch to slot $slot",
                        color = when {
                            active -> Cyan
                            canTap -> TextLo
                            else -> TextMu
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        KV("Card state", sim.s("sim_states"))
        KV("Carrier", carrierName(sim.s("Operator"), sim.s("spn_name_data")), TextHi)
        KV("MCC / MNC", "${sim.s("mdm_mcc")} / ${sim.s("mdm_mnc")}")
        KV("Modem state", sim.s("modem_main_state"))
        KV("PIN status", pinText(sim.s("pin_status")))
        if (detail != null) {
            KV("ICCID", detail.s("iccid").ifBlank { detail.s("sim_iccid") })
            KV("IMSI", detail.s("imsi").ifBlank { detail.s("sim_imsi") })
            KV("Phone number", detail.s("msisdn").ifBlank { detail.s("phone_number") })
        }

        Spacer(Modifier.height(10.dp))
        HintBox(
            if (!state.loggedIn) {
                "Slot information is readable without signing in; switching slots requires an admin sign-in."
            } else {
                "After switching, the modem re-registers on the network, so the signal and traffic cards may show no data for tens of seconds. That is normal.\n" +
                    "If only one SIM is inserted, switching to the empty slot will fail — a device-side limitation."
            },
            TextLo
        )
    }

    confirmSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { confirmSlot = null },
            containerColor = Panel,
            title = {
                Text(
                    "Switch to slot $slot?",
                    color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "This is a real device write: the modem re-registers on the network and this phone loses connectivity briefly (tens of seconds). The web UI will show the new slot as well.",
                        color = TextLo, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Current slot: $curSlot   →   target slot: $slot",
                        color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = slot
                    confirmSlot = null
                    onSwitchSim(s)
                }) { Text("Confirm switch", color = Cyan, fontSize = 14.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSlot = null }) {
                    Text("Cancel", color = TextLo, fontSize = 14.sp)
                }
            }
        )
    }
}

/**
 * `switch_card_status` 的取值在固件里没有文档，实测稳定态是 "1"。
 * 这里只对确定的值做翻译，其余原样显示，避免给出错误解释。
 */
private fun switchStatusText(v: String): String = when (v) {
    "1" -> "Ready"
    "" -> "—"
    else -> v
}

private fun pinText(v: String): String = when (v) {
    "0" -> "PIN lock disabled"
    "1" -> "Enabled, awaiting PIN"
    "2" -> "Locked"
    "" -> "—"
    else -> v
}

@Composable
private fun RuntimeCard(dev: JSONObject?) {
    if (dev == null) return
    val memTotal = dev.digL("meminfo.total")
    val memAvail = dev.digL("meminfo.avaliable").takeIf { it > 0 } ?: dev.digL("meminfo.available")
    val usedPct = if (memTotal > 0) ((memTotal - memAvail) * 100 / memTotal) else 0

    PanelCard(title = "Runtime", accent = Violet) {
        KV("Uptime", uptimeText(dev.digL("device_uptime")), TextHi)
        KV("Total memory", kbText(memTotal))
        KV("Available memory", kbText(memAvail))
        KV("Memory used", "$usedPct %", if (usedPct > 85) Amber else Mint)
        KV("Watchdog", if (dev.s("WatchDog_Setting_Option") == "on") "On" else "Off")
        KV("Overtemp throttling", if (dev.b("hightemp_datalimit_status")) "Triggered" else "Normal")
        KV("Scheduled reboot", if (dev.b("reboot_schedule_enable")) "On" else "Off")

        val flash = dev.optJSONArray("flashinfo")
        if (flash != null && flash.length() > 0) {
            Spacer(Modifier.height(10.dp))
            Text("Storage partitions", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            for (idx in 0 until flash.length()) {
                val part = flash.optJSONObject(idx) ?: continue
                KV(
                    part.s("mounted_on").ifBlank { part.s("filesystem") },
                    "${part.s("use")} used · ${part.s("avail")} KB free"
                )
            }
        }
    }
}

@Composable
private fun SystemCard(state: CpeState) {
    PanelCard(title = "System & session", accent = Blue) {
        KV("Sign-in state", if (state.loggedIn) "Signed in (admin)" else "Signed out (read-only)", if (state.loggedIn) Mint else Amber)
        KV(
            "Session key",
            if (state.cryptoReady) "Negotiated" else if (state.loggedIn) "Not negotiated" else "—",
            if (state.cryptoReady) Mint else TextMu
        )
        state.authOf(Api.WEB_INFO)?.let { info ->
            KV("Login user", info.s("login_current_user"))
            KV("Client IP", info.s("login_user_ip"))
            val left = info.l("login_timeout_lefttime")
            KV("Session remaining", if (left > 0) uptimeText(left) else "—")
            val fail = info.i("login_fail_num", -1)
            if (fail >= 0) KV("Attempts left", "$fail", if (fail <= 2) Rose else TextHi)
        }
        state.authOf(Api.SYS_TIME)?.let { KV("Device time", it.s("localtime")) }
        state.authOf(Api.SNTP_MODE)?.let { KV("Time sync mode", it.s("systime_mode")) }
        state.authOf(Api.SNTP_SYNC)?.let {
            val done = it.s("sntp_syn_done") == "1"
            KV("SNTP sync", if (done) "Complete" else "Not synced", if (done) Mint else TextMu)
        }
        state.authOf(Api.SLEEP_PARA)?.let { sp ->
            val sleep = sp.digL("time.sleep")
            KV("Sleep setting", if (sleep > 0) uptimeText(sleep) else "Not enabled")
        }
        state.authOf(Api.THERMAL)?.let { th ->
            val rows = com.cpepanel.cpe.J.flatten(th).take(6)
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Thermal policy", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                rows.forEach { (k, v) -> KV(k.substringAfterLast('.'), v) }
            }
        }
        if (!state.loggedIn) {
            Spacer(Modifier.height(8.dp))
            HintBox("Sign in to read device time, session details, thermal policy, sleep settings and firmware version.", TextLo)
        }
    }
}

/** 短信容量与参数（只读，不含短信正文） */
@Composable
private fun SmsCard(state: CpeState) {
    if (!state.loggedIn) return
    val cap = state.smsCapacity
    val par = state.smsParam
    if (cap == null && par == null) {
        PanelCard(title = "SMS", accent = Blue) {
            HintBox(state.authErrors[Api.SMS_CAPACITY.key] ?: "Reading SMS capacity…", Amber)
        }
        return
    }
    val unread = (cap?.i("sms_dev_unread_num") ?: 0) + (cap?.i("sms_sim_unread_num") ?: 0)

    PanelCard(
        title = "SMS",
        accent = Blue,
        trailing = {
            Pill(if (unread > 0) "$unread unread" else "No unread", if (unread > 0) Amber else Mint)
        }
    ) {
        cap?.let { c ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    "Device", c.i("sms_nvused_total").toString(),
                    "/ ${c.i("sms_nv_total")}", Blue, Modifier.weight(1f)
                )
                MetricTile("Received", c.i("sms_nv_rev_total").toString(), "", Cyan, Modifier.weight(1f))
                MetricTile("Sent", c.i("sms_nv_send_total").toString(), "", Mint, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            KV("SIM capacity", "${c.i("sms_sim_total")}")
            val du = c.i("sms_dev_unread_num")
            val su = c.i("sms_sim_unread_num")
            KV("Unread (device)", du.toString(), if (du > 0) Amber else TextHi)
            KV("Unread (SIM)", su.toString(), if (su > 0) Amber else TextHi)
            KV("Drafts", c.i("sms_nv_draftbox_total").toString())
        }
        par?.let { p ->
            Spacer(Modifier.height(6.dp))
            KV("SMSC", p.s("sca"))
            KV("Validity", "${p.i("tp_validity_period")} min")
            KV("Storage", if (p.i("mem_stroe") == 0) "Device" else "SIM")
            KV("Delivery reports", if (p.b("status_report_on")) "On" else "Off")
        }
        Spacer(Modifier.height(10.dp))
        HintBox(
            "The full message list, conversation view and sending live on the “SMS” tab.\n" +
                "Message bodies arrive from the device as UTF-16BE hex and are decoded on your phone, never through a third-party service.",
            TextLo
        )
    }
}

/** LED 夜间模式 / 定时休眠 / Mesh 灯光 */
@Composable
private fun PowerCard(state: CpeState) {
    if (!state.loggedIn) return
    val nm = state.nightMode
    val sp = state.sleepPara
    val led = state.authOf(Api.LED_MESH)
    if (nm == null && sp == null && led == null) {
        PanelCard(title = "Lights & power saving", accent = Amber) {
            HintBox(state.authErrors[Api.NIGHT_MODE.key] ?: "Reading light and power settings…", Amber)
        }
        return
    }
    PanelCard(title = "Lights & power saving", accent = Amber) {
        nm?.let { n ->
            val on = n.i("switch") == 1
            KV("LED night mode", if (on) "On" else "Off", if (on) Mint else TextLo)
            KV("Night window", "${n.s("starttime")} — ${n.s("endtime")}")
            KV("Night now?", if (n.i("is_nightmode") == 1) "Yes" else "No")
        }
        sp?.let { s ->
            val sleepOn = s.digS("switch.sleep") == "1"
            val wakeOn = s.digS("switch.wakeup") == "1"
            Spacer(Modifier.height(6.dp))
            KV("Scheduled sleep", if (sleepOn) "On" else "Off", if (sleepOn) Mint else TextLo)
            KV("Scheduled wake", if (wakeOn) "On" else "Off", if (wakeOn) Mint else TextLo)
            val st = s.digS("time.sleep")
            val wt = s.digS("time.wakeup")
            if (st.isNotBlank()) KV("Sleep at", st)
            if (wt.isNotBlank()) KV("Wake at", wt)
        }
        led?.let { l ->
            Spacer(Modifier.height(6.dp))
            KV("Mesh light nodes", "${l.i("mesh_count")}")
        }
        Spacer(Modifier.height(10.dp))
        HintBox("Night mode turns the status LEDs off or dims them during the set window; scheduled sleep powers down automatically during long idle periods.", TextLo)
    }
}

@Composable
private fun AboutModelCard() {
    PanelCard(title = "About this device", accent = TextMu) {
        Text(
            "Confirmed by parsing the stock web UI page by page and verifying each call on a real unit:\n" +
                "· the admin UI is ZTE-customised OpenWrt over ubus JSON-RPC\n" +
                "· the front end defines 235 ubus methods across 72 feature pages\n" +
                "· 61 read-only endpoints respond on this unit; this app wires up ${Api.EVERYTHING.size}\n" +
                "· plus ${Api.Write.ALL.size} write endpoints such as SIM slot switching and SMS send/delete\n" +
                "· ${Api.EVERYTHING.size + Api.Write.ALL.size} ubus methods in total\n" +
                "· the device enforces a vendor-level same-origin Referer check on /ubus/\n\n" +
                "This app reproduces the stock call chain natively, so browser CORS and Referer rules do not apply.",
            color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
        )
    }
}
