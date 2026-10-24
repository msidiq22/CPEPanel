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
                PanelCard(title = "Signal history", accent = Cyan) {
                    Text(
                        "Collecting samples… (${samples.size} so far)\nThe curve is drawn progressively as data refreshes.",
                        color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
                    )
                }
            } else {
                PanelCard(
                    title = "RSRP trend",
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
                        StatBlock("Now", "${values.last()} dBm", Cyan)
                        StatBlock("Min", "${values.min()} dBm", Amber)
                        StatBlock("Max", "${values.max()} dBm", Mint)
                        StatBlock("Avg", "${values.average().toInt()} dBm", TextHi)
                    }
                }
            }
        }

        item {
            if (samples.size >= 2) {
                PanelCard(
                    title = "SINR trend",
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
                        StatBlock("Now", String.format("%.1f dB", snrs.last()), Mint)
                        StatBlock("Min", String.format("%.1f dB", snrs.min()), Amber)
                        StatBlock("Max", String.format("%.1f dB", snrs.max()), Mint)
                        StatBlock("Avg", String.format("%.1f dB", snrs.average()), TextHi)
                    }
                }
            }
        }

        item { NeighborCard(net) }

        item { BandLockCard(net) }

        item {
            PanelCard(title = "Reading these numbers", accent = Blue) {
                Text(
                    "RSRP (reference signal received power)\n" +
                        "  Coverage strength in dBm — closer to 0 is better.\n" +
                        "  ≥ -90 excellent, -90 to -100 good, -100 to -110 fair, < -110 weak.\n\n" +
                        "SINR (signal-to-noise ratio)\n" +
                        "  How clean the signal is; this decides your top speed.\n" +
                        "  ≥ 20 excellent, 13 to 20 good, 7 to 13 fair, < 7 heavy interference.\n\n" +
                        "Rules of thumb\n" +
                        "  Good RSRP but poor SINR → interference dominates; repositioning or re-aiming usually beats a new antenna.\n" +
                        "  Poor RSRP and poor SINR → weak coverage; consider an external antenna or a spot near a window.",
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
    PanelCard(title = "5G neighbors", accent = Blue) {
        if (nbr.isBlank()) {
            HintBox("No neighbor-cell information reported.", TextLo)
            return@PanelCard
        }
        val cells = nbr.split(';').filter { it.isNotBlank() }
        cells.forEachIndexed { idx, item ->
            val parts = item.split(',')
            val pci = parts.getOrNull(0)?.trim().orEmpty()
            val channel = parts.getOrNull(1)?.trim().orEmpty()
            KV(
                "Neighbor ${idx + 1}",
                "PCI $pci · channel $channel"
            )
        }
        Spacer(Modifier.height(6.dp))
        HintBox("Serving cell PCI ${net.s("nr5g_pci")} · channel ${net.s("nr5g_action_channel")}", TextMu)
    }
}

/**
 * 频段与小区信息 —— 对应网页版「网络 → 网络模式 / 首选位置」页展示的内容。
 * 数据全部来自免登录的 nwinfo_get_netinfo，因此未登录也能看到。
 */
@Composable
private fun BandLockCard(net: JSONObject?) {
    if (net == null) return
    PanelCard(title = "Band & cell", accent = Violet) {
        KV("Network mode", networkTypeText(net.s("network_type")))
        KV("Registration", "${net.s("net_select")} / ${net.s("net_select_mode")}")
        KV("Domain state", net.s("domain_stat"))
        KV("Carrier", carrierName(net.s("network_provider"), net.s("network_provider_fullname")))

        Spacer(Modifier.height(8.dp))
        KV("Current band", net.s("wan_active_band"), Cyan)
        val bw = net.s("nr5g_bandwidth")
        if (bw.isNotBlank()) KV("NR bandwidth", "$bw MHz")
        KV("Cell ID", net.s("nr5g_cell_id"))
        KV("PCI", net.s("nr5g_pci"))
        KV("Channel", net.s("nr5g_action_channel"))
        KV("LAC", net.s("lac_code"))

        Spacer(Modifier.height(8.dp))
        Text("Band capability & locks", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        val lteBand = net.s("lte_band")
        if (lteBand.isNotBlank()) KV("LTE bands", lteBand)
        val nrSa = net.s("nr5g_sa_band_lock")
        if (nrSa.isNotBlank()) KV("NR SA bands", nrSa)
        val gwLock = net.s("gw_band_lock")
        if (gwLock.isNotBlank()) KV("Lock mask (GW)", gwLock)
        val lteLock = net.s("lte_band_lock")
        if (lteLock.isNotBlank()) KV("Lock mask (LTE)", lteLock)
        val nsaLock = net.s("nr5g_nsa_band_lock")
        if (nsaLock.isNotBlank()) KV("Lock mask (NR NSA)", nsaLock)

        val lockNr = net.s("lock_nr_cell")
        val lockLte = net.s("lock_lte_cell")
        if (lockNr.isNotBlank() || lockLte.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            KV("Locked NR cell", lockNr.ifBlank { "None" }, Amber)
            KV("Locked LTE cell", lockLte.ifBlank { "None" }, Amber)
        }

        Spacer(Modifier.height(10.dp))
        HintBox(
            "Band masks are hex bitmaps reported by the device, one bit per band; " +
                "if you locked bands in the web UI, this matches it. The app is read-only here.",
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
        sec >= 60 -> "${sec / 60}m${sec % 60}s"
        else -> "${sec}s"
    }
}
