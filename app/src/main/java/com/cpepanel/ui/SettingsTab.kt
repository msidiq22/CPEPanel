package com.cpepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.cpepanel.cpe.UbusMethod
import com.cpepanel.data.CpeState
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Blue
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.PanelHi
import com.cpepanel.ui.theme.Rose
import com.cpepanel.ui.theme.Stroke
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu

@Composable
fun SettingsTab(
    state: CpeState,
    onSaveUrl: (String) -> Unit,
    onSetRefresh: (Long) -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    onSetCrypto: (Boolean) -> Unit
) {
    var urlDraft by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var expanded by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PanelCard(title = "CPE 地址", accent = Cyan) {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TextHi
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Stroke,
                        cursorColor = Cyan
                    ),
                    placeholder = { Text("http://192.168.0.1", color = TextMu, fontSize = 14.sp) }
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSaveUrl(urlDraft) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan
                        )
                    ) { Text("保存并重连", fontSize = 13.sp) }
                    TextButton(onClick = { urlDraft = "http://192.168.0.1" }) {
                        Text("恢复默认", color = TextLo, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintBox("多数中兴 CPE 默认是 http://192.168.0.1；若改过 LAN 网段或端口，填完整地址即可。", TextLo)
            }
        }

        item {
            PanelCard(title = "刷新频率", accent = Blue) {
                val options = listOf(1000L, 3000L, 5000L, 10000L, 30000L, 0L)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.take(3).forEach { ms ->
                            RefreshChip(ms, state.refreshMs == ms) { onSetRefresh(ms) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.drop(3).forEach { ms ->
                            RefreshChip(ms, state.refreshMs == ms) { onSetRefresh(ms) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintBox(
                    if (state.refreshMs <= 0L) {
                        "当前：已关闭自动刷新。数据不再自动更新，可用右上角按钮手动刷新一次。"
                    } else {
                        "当前：${refreshLabel(state.refreshMs)}。采样越密，信号曲线越平滑，耗电也略高。"
                    },
                    TextLo
                )
            }
        }

        item {
            PanelCard(title = "管理员登录", accent = if (state.loggedIn) Mint else Amber) {
                KV("当前状态", if (state.loggedIn) "已登录（可读全部只读接口）" else "未登录（仅免登录接口）",
                    if (state.loggedIn) Mint else Amber, mono = false)
                Spacer(Modifier.height(10.dp))
                if (state.loggedIn) {
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Rose.copy(alpha = 0.16f), contentColor = Rose
                        )
                    ) { Text("退出登录", fontSize = 13.sp) }
                } else {
                    Button(
                        onClick = onLoginClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan
                        )
                    ) { Text("登录", fontSize = 13.sp) }
                }
                Spacer(Modifier.height(8.dp))
                HintBox(
                    "本应用只做只读采集，不会修改设备任何配置。\n" +
                        "登录口令在前端即完成两次 SHA-256 加盐哈希，明文密码不会离开手机。",
                    TextLo
                )
            }
        }

        item {
            PanelCard(title = "高级", accent = TextMu) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("会话加密协商", color = TextHi, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "登录后额外协商 RSA + AES 会话密钥，与官方前端行为一致。\n" +
                                "设备返回的 Wi-Fi 密钥、APN 口令等字段靠它才能解密，建议保持开启。",
                            color = TextLo, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Switch(
                        checked = state.negotiateCrypto,
                        onCheckedChange = onSetCrypto,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Cyan,
                            checkedTrackColor = Cyan.copy(alpha = 0.3f)
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                KV(
                    "当前状态",
                    when {
                        !state.negotiateCrypto -> "已关闭"
                        state.cryptoReady -> "已协商成功"
                        state.loggedIn -> "等待协商（重新登录生效）"
                        else -> "未登录"
                    },
                    if (state.cryptoReady) Mint else TextMu,
                    mono = false
                )
            }
        }

        item { RawDataCard(state, expanded) { expanded = if (expanded == it) null else it } }

        item {
            PanelCard(title = "说明", accent = TextMu) {
                Text(
                    "本应用是个人自用的本地管理面板，直连你的 CPE，不经过任何云端服务器。\n\n" +
                        "· 所有请求都发往你在「CPE 地址」里填写的设备；\n" +
                        "· 不收集、不上传任何数据；\n" +
                        "· 当前版本只读，不包含任何写入/重启/改配置的操作。",
                    color = TextLo, fontSize = 12.sp, lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun RefreshChip(ms: Long, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Cyan else TextLo
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Cyan.copy(alpha = 0.14f) else PanelHi)
            .border(1.dp, if (selected) Cyan.copy(alpha = 0.5f) else Stroke, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(refreshLabel(ms), color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RawDataCard(state: CpeState, expanded: String?, onToggle: (String) -> Unit) {
    PanelCard(title = "原始接口数据", accent = Blue) {
        Text(
            "点开任意接口可以直接看到设备返回的原始 JSON，便于对照排查。",
            color = TextLo, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))

        Api.ALL.forEach { m ->
            RawRow(m, state, expanded == m.key) { onToggle(m.key) }
        }
    }
}

@Composable
private fun RawRow(m: UbusMethod, state: CpeState, isOpen: Boolean, onToggle: () -> Unit) {
    val data = state.freeOf(m) ?: state.authOf(m)
    val err = state.freeErrors[m.key] ?: state.authErrors[m.key]
    val statusText = when {
        data != null -> "OK"
        err != null -> if (err.contains("登录")) "需登录" else "失败"
        else -> "未采集"
    }
    val statusColor = when {
        data != null -> Mint
        err != null -> Amber
        else -> TextMu
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggle() }
                .padding(vertical = 9.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.label, color = TextHi, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(m.key, color = TextMu, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Pill(statusText, statusColor)
            Spacer(Modifier.size(6.dp))
            Text(if (isOpen) "▾" else "▸", color = TextLo, fontSize = 12.sp)
        }
        if (isOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelHi)
                    .padding(10.dp)
            ) {
                val text = when {
                    data != null -> runCatching { data.toString(2) }.getOrElse { data.toString() }
                    err != null -> err
                    else -> "尚未采集到数据"
                }
                Text(
                    text,
                    color = if (data != null) TextLo else Amber,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
