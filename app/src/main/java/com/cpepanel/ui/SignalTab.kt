package com.cpepanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.cpe.J.i
import com.cpepanel.cpe.J.s
import com.cpepanel.data.CpeState
import com.cpepanel.data.SignalSample
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Blue
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu
import com.cpepanel.ui.theme.Violet
import org.json.JSONObject

@Composable
fun SignalTab(state: CpeState) {
    val samples = state.samples
    val net = state.net

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            if (samples.size < 2) {
                PanelCard(title = "信号历史曲线", accent = Cyan) {
                    Text(
                        "正在采集样本…（已采 ${samples.size} 个）\n曲线会随着刷新逐步绘制出来。",
                        color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
                    )
                }
            } else {
                PanelCard(
                    title = "RSRP 电平趋势",
                    accent = Cyan,
                    trailing = { Text(spanLabel(samples), color = TextMu, fontSize = 11.sp) }
                ) {
                    Sparkline(
                        values = samples.map { it.rsrp.toFloat() },
                        color = Cyan,
                        minSpan = 10f,
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    val values = samples.map { it.rsrp }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("当前", "${values.last()} dBm", Cyan)
                        StatBlock("最低", "${values.min()} dBm", Amber)
                        StatBlock("最高", "${values.max()} dBm", Mint)
                        StatBlock("平均", "${values.average().toInt()} dBm", TextHi)
                    }
                }
            }
        }

        item {
            if (samples.size >= 2) {
                PanelCard(
                    title = "SINR 信噪比趋势",
                    accent = Mint,
                    trailing = { Text(spanLabel(samples), color = TextMu, fontSize = 11.sp) }
                ) {
                    Sparkline(
                        values = samples.map { it.snr },
                        color = Mint,
                        minSpan = 6f,
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    val snrs = samples.map { it.snr }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("当前", String.format("%.1f dB", snrs.last()), Mint)
                        StatBlock("最低", String.format("%.1f dB", snrs.min()), Amber)
                        StatBlock("最高", String.format("%.1f dB", snrs.max()), Mint)
                        StatBlock("平均", String.format("%.1f dB", snrs.average()), TextHi)
                    }
                }
            }
        }

        item { NeighborCard(net) }

        item { BandLockCard(net) }

        item {
            PanelCard(title = "怎么读这些数字", accent = Blue) {
                Text(
                    "RSRP（参考信号接收功率）\n" +
                        "  衡量覆盖强度，单位 dBm，越接近 0 越好。\n" +
                        "  ≥ -90 优秀，-90 ~ -100 良好，-100 ~ -110 一般，< -110 偏弱。\n\n" +
                        "SINR（信噪比）\n" +
                        "  衡量信号干净程度，直接决定能跑多快。\n" +
                        "  ≥ 20 优秀，13 ~ 20 良好，7 ~ 13 一般，< 7 干扰明显。\n\n" +
                        "工程经验\n" +
                        "  RSRP 好但 SINR 差 → 干扰为主，换位置/换朝向往往比换天线有效。\n" +
                        "  RSRP 和 SINR 都差 → 覆盖不足，考虑外接天线或靠近窗口。",
                    color = TextLo, fontSize = 12.sp, lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun NeighborCard(net: JSONObject?) {
    if (net == null) return
    val nbr = net.s("nr_neighbor_cell")
    PanelCard(title = "5G 邻区", accent = Blue) {
        if (nbr.isBlank()) {
            HintBox("当前没有读取到邻区信息。", TextLo)
            return@PanelCard
        }
        val cells = nbr.split(';').filter { it.isNotBlank() }
        cells.forEachIndexed { idx, item ->
            val parts = item.split(',')
            val pci = parts.getOrNull(0)?.trim().orEmpty()
            val channel = parts.getOrNull(1)?.trim().orEmpty()
            KV(
                "邻区 ${idx + 1}",
                "PCI $pci　信道 $channel"
            )
        }
        Spacer(Modifier.height(6.dp))
        HintBox("本机主小区 PCI ${net.s("nr5g_pci")}　信道 ${net.s("nr5g_action_channel")}", TextMu)
    }
}

/**
 * 频段与小区信息 —— 对应网页版「网络 → 网络模式 / 首选位置」页展示的内容。
 * 数据全部来自免登录的 nwinfo_get_netinfo，因此未登录也能看到。
 */
@Composable
private fun BandLockCard(net: JSONObject?) {
    if (net == null) return
    PanelCard(title = "频段与小区", accent = Violet) {
        KV("网络制式", networkTypeText(net.s("network_type")))
        KV("驻网方式", "${net.s("net_select")} / ${net.s("net_select_mode")}")
        KV("域状态", net.s("domain_stat"))
        KV("运营商", carrierName(net.s("network_provider"), net.s("network_provider_fullname")))

        Spacer(Modifier.height(8.dp))
        KV("当前频段", net.s("wan_active_band"), Cyan)
        val bw = net.s("nr5g_bandwidth")
        if (bw.isNotBlank()) KV("NR 频宽", "$bw MHz")
        KV("小区 ID", net.s("nr5g_cell_id"))
        KV("PCI", net.s("nr5g_pci"))
        KV("信道号", net.s("nr5g_action_channel"))
        KV("LAC", net.s("lac_code"))

        Spacer(Modifier.height(8.dp))
        Text("频段能力与锁定", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        val lteBand = net.s("lte_band")
        if (lteBand.isNotBlank()) KV("LTE 频段", lteBand)
        val nrSa = net.s("nr5g_sa_band_lock")
        if (nrSa.isNotBlank()) KV("NR SA 频段", nrSa)
        val gwLock = net.s("gw_band_lock")
        if (gwLock.isNotBlank()) KV("锁定掩码 (GW)", gwLock)
        val lteLock = net.s("lte_band_lock")
        if (lteLock.isNotBlank()) KV("锁定掩码 (LTE)", lteLock)
        val nsaLock = net.s("nr5g_nsa_band_lock")
        if (nsaLock.isNotBlank()) KV("锁定掩码 (NR NSA)", nsaLock)

        val lockNr = net.s("lock_nr_cell")
        val lockLte = net.s("lock_lte_cell")
        if (lockNr.isNotBlank() || lockLte.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            KV("锁定的 NR 小区", lockNr.ifBlank { "无" }, Amber)
            KV("锁定的 LTE 小区", lockLte.ifBlank { "无" }, Amber)
        }

        Spacer(Modifier.height(10.dp))
        HintBox(
            "频段掩码是设备上报的十六进制位图，每一位对应一个频段；" +
                "如果你在网页后台锁过频段，这里会与那边一致。App 只读不改。",
            TextLo
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMu, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** 曲线覆盖的时间跨度，让用户知道这张图代表多久的趋势。 */
private fun spanLabel(samples: List<SignalSample>): String {
    if (samples.size < 2) return "--"
    val sec = ((samples.last().t - samples.first().t) / 1000L).coerceAtLeast(0L)
    return when {
        sec >= 3600 -> "${sec / 3600}h${(sec % 3600) / 60}m"
        sec >= 60 -> "${sec / 60}分${sec % 60}秒"
        else -> "${sec}秒"
    }
}
