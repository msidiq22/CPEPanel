package com.cpepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.data.ConnStatus
import com.cpepanel.data.CpeState
import com.cpepanel.data.CpeViewModel
import com.cpepanel.data.SmsUiState
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Bg
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.Panel
import com.cpepanel.ui.theme.Rose
import com.cpepanel.ui.theme.Stroke
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu

private val TAB_TITLES = listOf("Dashboard", "Signal", "Network", "SMS", "Device", "Settings")

@Composable
fun AppRoot(state: CpeState, sms: SmsUiState, vm: CpeViewModel) {
    var tab by remember { mutableStateOf(0) }
    var showLogin by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            PanelHeader(
                state = state,
                onRefresh = vm::refreshNow,
                onLoginClick = { showLogin = true }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                TAB_TITLES.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = {
                            // 未登录时不显示未读数（那时也读不到短信）
                            val unread = if (state.loggedIn) sms.unread else 0
                            Text(
                                if (i == SMS_TAB && unread > 0) "$title·$unread" else title,
                                fontSize = 11.sp
                            )
                        },
                        icon = { TabIcon(i, tab == i) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            unselectedIconColor = TextMu,
                            unselectedTextColor = TextMu,
                            indicatorColor = Cyan.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val showOffline = state.status == ConnStatus.Offline && state.free.isEmpty()
            val showLoading = state.free.isEmpty() && !showOffline

            when {
                showOffline -> OfflineBanner(state) { vm.refreshNow() }
                showLoading -> LoadingBanner()
                else -> when (tab) {
                    0 -> DashboardTab(
                        state = state,
                        onLoginClick = { showLogin = true },
                        onGoSignal = { tab = 1 }
                    )
                    1 -> SignalTab(state)
                    2 -> NetworkTab(state, onLoginClick = { showLogin = true })
                    3 -> SmsTab(
                        state = state,
                        sms = sms,
                        onLoad = vm::loadSms,
                        onSend = { num, body -> vm.sendSms(num, body) },
                        onDelete = { ids -> vm.deleteSms(ids) },
                        onLoginClick = { showLogin = true }
                    )
                    4 -> DeviceTab(state = state, onSwitchSim = { slot -> vm.switchSimSlot(slot) })
                    else -> SettingsTab(
                        state = state,
                        onSaveUrl = vm::setBaseUrl,
                        onSetRefresh = vm::setRefreshMs,
                        onLoginClick = { showLogin = true },
                        onLogout = vm::logout,
                        onSetCrypto = vm::setNegotiateCrypto
                    )
                }
            }
        }
    }

    if (showLogin) {
        LoginDialog(
            busy = state.busy,
            onDismiss = { showLogin = false },
            onConfirm = { pwd ->
                vm.login(pwd)
                showLogin = false
            }
        )
    }
}

/** 「短信」在底部导航里的下标 —— 未读角标与视图分发都用它，避免散落的魔法数字 */
private const val SMS_TAB = 3

@Composable
private fun TabIcon(index: Int, selected: Boolean) {
    val tint = if (selected) Cyan else TextMu
    Box(Modifier.size(24.dp)) {
        when (index) {
            0 -> IconGauge(tint)
            1 -> IconChart(tint)
            2 -> IconGlobe(tint)
            3 -> IconMessage(tint)
            4 -> IconChip(tint)
            else -> Icon(
                Icons.Filled.Settings, contentDescription = "Settings",
                tint = tint, modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PanelHeader(state: CpeState, onRefresh: () -> Unit, onLoginClick: () -> Unit) {
    val (dotColor, statusText) = when (state.status) {
        ConnStatus.Online -> Mint to "Online"
        ConnStatus.Connecting, ConnStatus.Idle -> Amber to "Connecting"
        ConnStatus.Offline -> Rose to "Offline"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("CPE Network Panel", color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(dotColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusText,
                        color = dotColor, fontSize = 11.sp, fontWeight = FontWeight.Medium
                    )
                    if (state.status == ConnStatus.Online && state.latencyMs >= 0) {
                        Text(
                            "  ${state.latencyMs}ms",
                            color = TextMu, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    if (state.lastUpdated > 0) {
                        Text(
                            "  Updated ${timeText(state.lastUpdated)}",
                            color = TextMu, fontSize = 11.sp
                        )
                    }
                }
            }
            StatusPill(
                loggedIn = state.loggedIn,
                onClick = { if (!state.loggedIn) onLoginClick() }
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Cyan.copy(alpha = 0.12f))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Text("⟳", color = Cyan, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun StatusPill(loggedIn: Boolean, onClick: () -> Unit) {
    val color: Color = if (loggedIn) Mint else Amber
    val label = if (loggedIn) "Signed in" else "Signed out"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        StatusDot(color, 6)
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
