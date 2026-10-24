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
            PanelCard(title = "CPE address", accent = Cyan) {
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
                    ) { Text("Save & reconnect", fontSize = 13.sp) }
                    TextButton(onClick = { urlDraft = "http://192.168.0.1" }) {
                        Text("Restore default", color = TextLo, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HintBox("Most ZTE CPEs default to http://192.168.0.1; if the LAN subnet or port was changed, enter the full address.", TextLo)
            }
        }

        item {
            PanelCard(title = "Refresh rate", accent = Blue) {
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
                        "Current: auto-refresh is off. Data no longer updates itself; use the button in the top-right to refresh once."
                    } else {
                        "Current: ${refreshLabel(state.refreshMs)}. Denser sampling smooths the signal curve but costs a little more battery."
                    },
                    TextLo
                )
            }
        }

        item {
            PanelCard(title = "Admin sign-in", accent = if (state.loggedIn) Mint else Amber) {
                KV("Status", if (state.loggedIn) "Signed in (all authenticated endpoints)" else "Signed out (anonymous endpoints only)",
                    if (state.loggedIn) Mint else Amber, mono = false)
                Spacer(Modifier.height(10.dp))
                if (state.loggedIn) {
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Rose.copy(alpha = 0.16f), contentColor = Rose
                        )
                    ) { Text("Sign out", fontSize = 13.sp) }
                } else {
                    Button(
                        onClick = onLoginClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan.copy(alpha = 0.16f), contentColor = Cyan
                        )
                    ) { Text("Sign in", fontSize = 13.sp) }
                }
                Spacer(Modifier.height(8.dp))
                HintBox(
                    "This app is read-first: the only writes are SMS send/delete and SIM slot switching, each confirmed;\n" +
                        "it never changes network, Wi-Fi or routing configuration.\n" +
                        "The password is salted and hashed twice with SHA-256 on the phone; the plaintext never leaves it.",
                    TextLo
                )
            }
        }

        item {
            PanelCard(title = "Advanced", accent = TextMu) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Session encryption", color = TextHi, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Negotiates an RSA + AES session key after sign-in, matching the stock web UI.\n" +
                                "Wi-Fi keys, APN passwords and similar fields can only be decrypted with it, so keep it on.",
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
                    "Status",
                    when {
                        !state.negotiateCrypto -> "Off"
                        state.cryptoReady -> "Negotiated"
                        state.loggedIn -> "Pending (applies on next sign-in)"
                        else -> "Signed out"
                    },
                    if (state.cryptoReady) Mint else TextMu,
                    mono = false
                )
            }
        }

        item { RawDataCard(state, expanded) { expanded = if (expanded == it) null else it } }

        item {
            PanelCard(title = "About", accent = TextMu) {
                Text(
                    "A personal, local-only management panel that talks straight to your CPE with no cloud server involved.\n\n" +
                        "· Every request goes to the device address you entered under “CPE address”;\n" +
                        "· no data is collected or uploaded;\n" +
                        "· the only writes are SMS send/delete and SIM slot switching, each confirmed and never retried automatically.",
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
    PanelCard(title = "Raw endpoint data", accent = Blue) {
        Text(
            "Expand any endpoint to see the raw JSON the device returned, handy for cross-checking problems.",
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
        err != null -> if (err.contains("login", true)) "Sign-in needed" else "Failed"
        else -> "Not fetched"
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
                    else -> "No data collected yet"
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
