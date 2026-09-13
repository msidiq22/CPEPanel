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
        PanelCard(title = "在线终端", accent = Cyan) {
            HintBox(state.authErrors[Api.LAN_ACCESS.key] ?: "正在读取终端列表…", Amber)
        }
        return
    }
    val list = data.optJSONArray("lan_access_list_info")
    val n = list?.length() ?: 0

    PanelCard(
        title = "在线终端",
        accent = Cyan,
        trailing = {
            state.authOf(Api.USER_NUM)?.let {
                Pill("${it.i("access_total_num")} 台", Cyan)
            }
        }
    ) {
        if (n == 0) {
            HintBox("当前没有终端接入。", TextLo)
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
                        d.s("hostname").ifBlank { d.s("devtype").ifBlank { "未知设备" } },
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
                    if (d.s("interface_type").contains("Ethernet", true)) "有线" else "无线",
                    if (d.s("interface_type").contains("Ethernet", true)) Blue else Violet
                )
            }
            Spacer(Modifier.height(6.dp))
            KV("MAC 地址", d.s("mac_address"))
            KV("接入方式", "${d.s("interface_type")} / ${d.s("address_source")}")
            val dur = d.l("duration_time")
            KV("在线时长", uptimeText(dur), if (dur > 0) Mint else TextHi)
            KV("接入时间", d.s("access_time"))
            val ipv6 = d.s("ipv6_address")
            if (ipv6.isNotBlank()) KV("IPv6", ipv6)
        }
        Spacer(Modifier.height(10.dp))
        HintBox("终端信息由设备 DHCP 租约与无线关联表汇总，刚接入的设备可能要几十秒才会出现。", TextLo)
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
        Triple("main_2g", "主 Wi-Fi 2.4G", Cyan),
        Triple("main_5g", "主 Wi-Fi 5G", Mint),
        Triple("guest_2g", "访客 Wi-Fi 2.4G", Violet),
        Triple("guest_5g", "访客 Wi-Fi 5G", Violet),
        Triple("backhaul_5g", "Mesh 回程 5G", Amber),
        Triple("backhaul_sta", "Mesh 上行 STA", Amber)
    )

    PanelCard(
        title = "Wi-Fi 密码",
        accent = Rose,
        trailing = {
            TextButton(onClick = { revealed = !revealed }) {
                Text(if (revealed) "隐藏" else "显示", color = Rose, fontSize = 12.sp)
            }
        }
    ) {
        if (keys.isEmpty()) {
            HintBox(
                if (!state.cryptoReady) {
                    "会话密钥尚未协商成功，无法解密密钥字段。请退出后重新登录。"
                } else {
                    state.authErrors[Api.WLAN_KEY.key] ?: "正在读取密钥…"
                },
                Amber
            )
            return@PanelCard
        }
        sections.forEach { (section, label, color) ->
            val plain = keys[section]
            if (plain == null) {
                KV(label, "无此网络", TextMu)
            } else if (plain.isEmpty()) {
                KV(label, "未配置密码", TextMu)
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
            "密码在手机本地解密，不会上传到任何服务器。\n" +
                "出于安全考虑，本页面不提供修改密码的功能。",
            TextLo
        )
    }
}

@Composable
private fun DeviceCard(state: CpeState) {
    val sim = state.simInfo
    PanelCard(title = "设备", accent = Cyan) {
        KV("识别机型", "中兴 MC8830 / G5TS（5G CPE）", Cyan)
        KV("产品类别", "5G 室内 CPE（OpenWrt 底层）")
        KV("管理地址", state.baseUrl, Blue)
        KV("Wi-Fi MAC", sim?.s("wlan_mac_address").orEmpty())
        KV("SIM 槽位", sim?.s("current_sim_slot").let { if (it.isNullOrBlank()) "—" else "卡槽 $it" })
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
        title = "SIM 卡与卡槽",
        accent = if (ready) Mint else Amber,
        trailing = { Pill(sim.s("sim_states").ifBlank { "未知" }, if (ready) Mint else Amber) }
    ) {
        KV("当前卡槽", "卡槽 $curSlot", Cyan)
        KV("卡槽状态", switchStatusText(switchStatus))

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
                        if (active) "卡槽 $slot（当前）" else "切换到卡槽 $slot",
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
        KV("卡状态", sim.s("sim_states"))
        KV("运营商", carrierName(sim.s("Operator"), sim.s("spn_name_data")), TextHi)
        KV("MCC / MNC", "${sim.s("mdm_mcc")} / ${sim.s("mdm_mnc")}")
        KV("调制解调器", sim.s("modem_main_state"))
        KV("PIN 状态", pinText(sim.s("pin_status")))
        if (detail != null) {
            KV("ICCID", detail.s("iccid").ifBlank { detail.s("sim_iccid") })
            KV("IMSI", detail.s("imsi").ifBlank { detail.s("sim_imsi") })
            KV("本机号码", detail.s("msisdn").ifBlank { detail.s("phone_number") })
        }

        Spacer(Modifier.height(10.dp))
        HintBox(
            if (!state.loggedIn) {
                "卡槽信息可只读查看；切换卡槽需要先登录管理后台。"
            } else {
                "切换卡槽后调制解调器会重新注册网络，信号与流量卡片可能有几十秒无数据，属正常现象。\n" +
                    "如果设备只插了一张 SIM 卡，切到空卡槽会失败 —— 这是设备侧的限制。"
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
                    "切换到卡槽 $slot？",
                    color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "这是一次真实的设备操作：调制解调器会重新注册网络，" +
                            "期间本机将短暂断网（约几十秒），网页版后台也会同步显示为新卡槽。",
                        color = TextLo, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前卡槽：$curSlot　→　目标卡槽：$slot",
                        color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = slot
                    confirmSlot = null
                    onSwitchSim(s)
                }) { Text("确认切换", color = Cyan, fontSize = 14.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSlot = null }) {
                    Text("取消", color = TextLo, fontSize = 14.sp)
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
    "1" -> "就绪"
    "" -> "—"
    else -> v
}

private fun pinText(v: String): String = when (v) {
    "0" -> "未启用 PIN 锁"
    "1" -> "已启用，待验证"
    "2" -> "已锁定"
    "" -> "—"
    else -> v
}

@Composable
private fun RuntimeCard(dev: JSONObject?) {
    if (dev == null) return
    val memTotal = dev.digL("meminfo.total")
    val memAvail = dev.digL("meminfo.avaliable").takeIf { it > 0 } ?: dev.digL("meminfo.available")
    val usedPct = if (memTotal > 0) ((memTotal - memAvail) * 100 / memTotal) else 0

    PanelCard(title = "运行状态", accent = Violet) {
        KV("已运行", uptimeText(dev.digL("device_uptime")), TextHi)
        KV("内存总量", kbText(memTotal))
        KV("可用内存", kbText(memAvail))
        KV("内存占用", "$usedPct %", if (usedPct > 85) Amber else Mint)
        KV("看门狗", if (dev.s("WatchDog_Setting_Option") == "on") "已开启" else "已关闭")
        KV("高温限速保护", if (dev.b("hightemp_datalimit_status")) "已触发" else "正常")
        KV("定时重启", if (dev.b("reboot_schedule_enable")) "已开启" else "已关闭")

        val flash = dev.optJSONArray("flashinfo")
        if (flash != null && flash.length() > 0) {
            Spacer(Modifier.height(10.dp))
            Text("存储分区", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            for (idx in 0 until flash.length()) {
                val part = flash.optJSONObject(idx) ?: continue
                KV(
                    part.s("mounted_on").ifBlank { part.s("filesystem") },
                    "${part.s("use")} 已用　可用 ${part.s("avail")} KB"
                )
            }
        }
    }
}

@Composable
private fun SystemCard(state: CpeState) {
    PanelCard(title = "系统与会话", accent = Blue) {
        KV("登录状态", if (state.loggedIn) "已登录（管理员）" else "未登录（仅只读）", if (state.loggedIn) Mint else Amber)
        KV(
            "会话密钥",
            if (state.cryptoReady) "已协商" else if (state.loggedIn) "未协商" else "—",
            if (state.cryptoReady) Mint else TextMu
        )
        state.authOf(Api.WEB_INFO)?.let { info ->
            KV("登录用户", info.s("login_current_user"))
            KV("本机 IP", info.s("login_user_ip"))
            val left = info.l("login_timeout_lefttime")
            KV("会话剩余", if (left > 0) uptimeText(left) else "—")
            val fail = info.i("login_fail_num", -1)
            if (fail >= 0) KV("剩余尝试次数", "$fail 次", if (fail <= 2) Rose else TextHi)
        }
        state.authOf(Api.SYS_TIME)?.let { KV("设备时间", it.s("localtime")) }
        state.authOf(Api.SNTP_MODE)?.let { KV("时间同步方式", it.s("systime_mode")) }
        state.authOf(Api.SNTP_SYNC)?.let {
            val done = it.s("sntp_syn_done") == "1"
            KV("SNTP 同步", if (done) "已完成" else "未同步", if (done) Mint else TextMu)
        }
        state.authOf(Api.SLEEP_PARA)?.let { sp ->
            val sleep = sp.digL("time.sleep")
            KV("休眠参数", if (sleep > 0) uptimeText(sleep) else "未启用")
        }
        state.authOf(Api.THERMAL)?.let { th ->
            val rows = com.cpepanel.cpe.J.flatten(th).take(6)
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("温控策略", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                rows.forEach { (k, v) -> KV(k.substringAfterLast('.'), v) }
            }
        }
        if (!state.loggedIn) {
            Spacer(Modifier.height(8.dp))
            HintBox("登录后可读取设备时间、会话信息、温控策略、休眠参数与固件版本信息。", TextLo)
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
        PanelCard(title = "短信", accent = Blue) {
            HintBox(state.authErrors[Api.SMS_CAPACITY.key] ?: "正在读取短信容量…", Amber)
        }
        return
    }
    val unread = (cap?.i("sms_dev_unread_num") ?: 0) + (cap?.i("sms_sim_unread_num") ?: 0)

    PanelCard(
        title = "短信",
        accent = Blue,
        trailing = {
            Pill(if (unread > 0) "$unread 条未读" else "无未读", if (unread > 0) Amber else Mint)
        }
    ) {
        cap?.let { c ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    "设备短信", c.i("sms_nvused_total").toString(),
                    "/ ${c.i("sms_nv_total")}", Blue, Modifier.weight(1f)
                )
                MetricTile("已接收", c.i("sms_nv_rev_total").toString(), "条", Cyan, Modifier.weight(1f))
                MetricTile("已发送", c.i("sms_nv_send_total").toString(), "条", Mint, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            KV("SIM 卡容量", "${c.i("sms_sim_total")} 条")
            val du = c.i("sms_dev_unread_num")
            val su = c.i("sms_sim_unread_num")
            KV("未读（设备）", du.toString(), if (du > 0) Amber else TextHi)
            KV("未读（SIM）", su.toString(), if (su > 0) Amber else TextHi)
            KV("草稿箱", c.i("sms_nv_draftbox_total").toString())
        }
        par?.let { p ->
            Spacer(Modifier.height(6.dp))
            KV("短信中心号", p.s("sca"))
            KV("有效期", "${p.i("tp_validity_period")} 分钟")
            KV("存储位置", if (p.i("mem_stroe") == 0) "设备" else "SIM 卡")
            KV("状态报告", if (p.b("status_report_on")) "已开启" else "已关闭")
        }
        Spacer(Modifier.height(10.dp))
        HintBox(
            "完整的短信列表、会话详情与发送功能在底部「短信」页。\n" +
                "短信正文由设备以 UTF-16BE 十六进制下发，在手机本地解码，不经过任何第三方服务。",
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
        PanelCard(title = "灯光与省电", accent = Amber) {
            HintBox(state.authErrors[Api.NIGHT_MODE.key] ?: "正在读取灯光与省电设置…", Amber)
        }
        return
    }
    PanelCard(title = "灯光与省电", accent = Amber) {
        nm?.let { n ->
            val on = n.i("switch") == 1
            KV("LED 夜间模式", if (on) "已开启" else "已关闭", if (on) Mint else TextLo)
            KV("夜间时段", "${n.s("starttime")} — ${n.s("endtime")}")
            KV("当前是否夜间", if (n.i("is_nightmode") == 1) "是" else "否")
        }
        sp?.let { s ->
            val sleepOn = s.digS("switch.sleep") == "1"
            val wakeOn = s.digS("switch.wakeup") == "1"
            Spacer(Modifier.height(6.dp))
            KV("定时休眠", if (sleepOn) "已开启" else "已关闭", if (sleepOn) Mint else TextLo)
            KV("定时唤醒", if (wakeOn) "已开启" else "已关闭", if (wakeOn) Mint else TextLo)
            val st = s.digS("time.sleep")
            val wt = s.digS("time.wakeup")
            if (st.isNotBlank()) KV("休眠时间", st)
            if (wt.isNotBlank()) KV("唤醒时间", wt)
        }
        led?.let { l ->
            Spacer(Modifier.height(6.dp))
            KV("Mesh 灯光节点", "${l.i("mesh_count")} 个")
        }
        Spacer(Modifier.height(10.dp))
        HintBox("夜间模式会在设定时段内熄灭或调暗指示灯；定时休眠用于长期无人使用时自动断电。", TextLo)
    }
}

@Composable
private fun AboutModelCard() {
    PanelCard(title = "关于这台设备", accent = TextMu) {
        Text(
            "经对网页版前端逐页解析 + 真机逐条实测确认：\n" +
                "· 管理后台是中兴定制的 OpenWrt，接口为 ubus JSON-RPC\n" +
                "· 前端共定义 235 个 ubus 方法、72 个功能页面\n" +
                "· 其中 61 个只读接口在本机实测可用，本应用接入 ${Api.EVERYTHING.size} 个\n" +
                "· 另有 SIM 卡槽切换、短信收发等写操作接口 ${Api.Write.ALL.size} 个\n" +
                "· 合计对接 ${Api.EVERYTHING.size + Api.Write.ALL.size} 个 ubus 方法\n" +
                "· 设备对 /ubus/ 入口做了厂商级 Referer 同源校验\n\n" +
                "本应用在原生层完整复刻了官方前端的调用链路，" +
                "因此不受浏览器跨域与 Referer 限制。",
            color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
        )
    }
}
