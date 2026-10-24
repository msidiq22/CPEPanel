package com.cpepanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.cpe.SmsCodec
import com.cpepanel.data.CpeState
import com.cpepanel.data.SmsMessage
import com.cpepanel.data.SmsThread
import com.cpepanel.data.SmsUiState
import com.cpepanel.ui.theme.Amber
import com.cpepanel.ui.theme.Blue
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Mint
import com.cpepanel.ui.theme.Panel
import com.cpepanel.ui.theme.PanelHi
import com.cpepanel.ui.theme.Rose
import com.cpepanel.ui.theme.Stroke
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu
import com.cpepanel.ui.theme.Violet

/**
 * 短信页。
 *
 * 三层视图切换（都用本地状态，不引入导航库）：
 *   列表（会话） → 会话详情（气泡 + 直接回复） → 新建短信
 *
 * 数据不来自轮询：[SmsUiState] 由 ViewModel 在「进入本页 / 手动刷新 / 收发完成」时
 * 单独拉取一次 —— 短信接口单次返回上百条、响应体几十 KB，挂到轮询上会把设备
 * 那台小 httpd 打挂（这一点在抓前端静态资源时被 502 教育过）。
 */
@Composable
fun SmsTab(
    state: CpeState,
    sms: SmsUiState,
    onLoad: () -> Unit,
    onSend: (String, String) -> Unit,
    onDelete: (List<Int>) -> Unit,
    onLoginClick: () -> Unit
) {
    var openNumber by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    // 进入本页且已登录时拉一次；登录状态变化（例如刚登录回来）也会触发
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn && !sms.loaded) onLoad()
    }

    if (!state.loggedIn) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PanelCard(title = "SMS", accent = Blue) {
                HintBox("Reading and sending SMS both require an admin sign-in.", Amber)
                Spacer(Modifier.height(12.dp))
                Btn("Sign in", Cyan) { onLoginClick() }
            }
        }
        return
    }

    val thread = openNumber?.let { n -> sms.threads.firstOrNull { it.number == n } }

    when {
        composing -> ComposePane(
            sending = sms.sending,
            onBack = { composing = false },
            onSend = { num, body ->
                onSend(num, body)
                composing = false
            }
        )

        thread != null -> ConversationPane(
            thread = thread,
            sending = sms.sending,
            onBack = { openNumber = null },
            onSend = { text -> onSend(thread.number, text) },
            onDeleteAll = { confirmDelete = thread.number }
        )

        else -> ThreadListPane(
            state = state,
            sms = sms,
            onLoad = onLoad,
            onOpen = { openNumber = it },
            onCompose = { composing = true },
            onDelete = { confirmDelete = it.number }
        )
    }

    // 删除确认（会话内全部短信）
    confirmDelete?.let { num ->
        val ids = sms.threads.firstOrNull { it.number == num }?.messages?.map { it.id }.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Panel,
            title = { Text("Delete all messages in this conversation?", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(num, color = Cyan, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This deletes ${ids.size} messages on the device and cannot be undone.",
                        color = TextLo, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(ids)
                    confirmDelete = null
                    openNumber = null
                }) { Text("Delete", color = Rose, fontSize = 14.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = TextLo, fontSize = 14.sp) }
            }
        )
    }
}

// ─────────────────────────── 会话列表 ───────────────────────────

@Composable
private fun ThreadListPane(
    state: CpeState,
    sms: SmsUiState,
    onLoad: () -> Unit,
    onOpen: (String) -> Unit,
    onCompose: () -> Unit,
    onDelete: (SmsThread) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { CapacityCard(state, sms, onLoad) }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Cyan.copy(alpha = 0.12f))
                    .border(1.dp, Cyan.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                    .clickable { onCompose() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("New message", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (sms.loading && !sms.loaded) {
            item {
                PanelCard(title = "Messages", accent = Blue) { HintBox("Reading messages…", Amber) }
            }
        }

        sms.error?.let { err ->
            item {
                PanelCard(title = "Messages", accent = Rose) {
                    HintBox(err, Rose)
                    Spacer(Modifier.height(12.dp))
                    Btn("Retry", Cyan) { onLoad() }
                }
            }
        }

        if (sms.empty) {
            item {
                PanelCard(title = "Messages", accent = Blue) {
                    HintBox("No messages on the device yet. Tap “New message” above to send the first one.", TextLo)
                }
            }
        }

        items(sms.threads, key = { it.number }) { t ->
            ThreadRow(t, onOpen = { onOpen(t.number) }, onDelete = { onDelete(t) })
        }

        if (sms.threads.isNotEmpty()) {
            item {
                HintBox(
                    "Message bodies arrive from the device as UTF-16BE hex, are decoded locally on your phone, and never pass through a third-party service.\n" +
                        "Messages are grouped by number; open one to see the full history.",
                    TextMu
                )
            }
        }
    }
}

@Composable
private fun ThreadRow(t: SmsThread, onOpen: () -> Unit, onDelete: () -> Unit) {
    val latest = t.latest
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Stroke, RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.number,
                color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (t.unread > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Amber)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("${t.unread}", color = androidx.compose.ui.graphics.Color(0xFF2A1A00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(latest.shortTime, color = TextMu, fontSize = 11.sp)
        }

        Spacer(Modifier.height(7.dp))

        Row(verticalAlignment = Alignment.Top) {
            when {
                latest.failed -> { Pill("Failed", Rose); Spacer(Modifier.width(7.dp)) }
                latest.draft -> { Pill("Draft", Amber); Spacer(Modifier.width(7.dp)) }
                latest.outgoing -> { Pill("Sent", Mint); Spacer(Modifier.width(7.dp)) }
            }
            Text(
                latest.content.replace("\n", " ").ifBlank { "(empty message)" },
                color = TextLo, fontSize = 13.sp, lineHeight = 19.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.count} messages", color = TextMu, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDelete) {
                Text("Delete", color = Rose, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CapacityCard(state: CpeState, sms: SmsUiState, onLoad: () -> Unit) {
    val cap = sms.capacity ?: state.smsCapacity
    PanelCard(
        title = "SMS",
        accent = Blue,
        trailing = {
            TextButton(onClick = onLoad, enabled = !sms.loading) {
                Text(if (sms.loading) "Refreshing…" else "Refresh", color = if (sms.loading) TextMu else Cyan, fontSize = 12.sp)
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Total", "${sms.total}", "", Cyan, Modifier.weight(1f))
            MetricTile("Unread", "${sms.unread}", "", if (sms.unread > 0) Amber else Mint, Modifier.weight(1f))
            MetricTile("Threads", "${sms.threads.size}", "", Violet, Modifier.weight(1f))
        }
        cap?.let { c ->
            Spacer(Modifier.height(10.dp))
            val used = c.optInt("sms_nv_rev_total") + c.optInt("sms_nv_send_total") + c.optInt("sms_nv_draftbox_total")
            KV("Device used / capacity", "$used / ${c.optInt("sms_nv_total")}")
            KV("SIM capacity", "${c.optInt("sms_sim_total")}")
            KV("Storage", "Internal")
        }
        state.smsParam?.let { p ->
            Spacer(Modifier.height(6.dp))
            KV("SMSC", p.optString("sca"))
            KV("Delivery reports", if (p.optInt("status_report_on") != 0) "On" else "Off")
        }
        if (cap == null) {
            Spacer(Modifier.height(8.dp))
            HintBox("Reading capacity…", TextLo)
        }
    }
}

// ─────────────────────────── 会话详情 ───────────────────────────

@Composable
private fun ConversationPane(
    thread: SmsThread,
    sending: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onDeleteAll: () -> Unit
) {
    var input by remember(thread.number) { mutableStateOf("") }
    val listState = rememberLazyListState()
    // messages 是 id 倒序，翻过来变成"旧 → 新"的聊天顺序
    val ordered = remember(thread.messages) { thread.messages.reversed() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Cyan, fontSize = 13.sp) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    thread.number,
                    color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace, maxLines = 1
                )
                Text("${thread.count} messages", color = TextMu, fontSize = 10.sp)
            }
            TextButton(onClick = onDeleteAll) { Text("Delete", color = Rose, fontSize = 13.sp) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(ordered, key = { it.id }) { m -> Bubble(m) }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val cnt = SmsCodec.smsCount(input)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending,
                minLines = 1,
                maxLines = 4,
                textStyle = TextStyle(fontSize = 14.sp, color = TextHi),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Stroke,
                    cursorColor = Cyan
                ),
                placeholder = { Text("Type a message", color = TextMu, fontSize = 13.sp) }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (input.isNotEmpty()) {
                    Text(
                        "${cnt.length} chars · ${cnt.parts} parts · ${if (cnt.unicode) "UCS2" else "GSM7"}",
                        color = TextMu, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Btn(
                    if (sending) "Sending…" else "Send",
                    if (sending || input.isBlank()) TextMu else Cyan,
                    enabled = !sending && input.isNotBlank()
                ) {
                    val text = input
                    input = ""
                    onSend(text)
                }
            }
        }
    }

    LaunchedEffect(ordered.size) {
        if (ordered.isNotEmpty()) listState.animateScrollToItem(ordered.size - 1)
    }
}

@Composable
private fun Bubble(m: SmsMessage) {
    val out = m.outgoing
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (out) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (out) Cyan.copy(alpha = 0.15f) else PanelHi)
                .border(
                    1.dp,
                    if (out) Cyan.copy(alpha = 0.30f) else Stroke,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(m.content.ifBlank { "(empty message)" }, color = TextHi, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.failed) {
                    Text("Failed", color = Rose, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                } else if (m.draft) {
                    Text("Draft", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                }
                Text(m.timeText, color = TextMu, fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────── 新建短信 ───────────────────────────

@Composable
private fun ComposePane(
    sending: Boolean,
    onBack: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val cnt = SmsCodec.smsCount(body)
    val bodyHexPreview = remember(body) {
        if (body.isEmpty()) "" else SmsCodec.encodeBody(body).take(48) + if (body.length > 12) "…" else ""
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Cyan, fontSize = 13.sp) }
            Text(
                "New message", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(56.dp))
        }

        // 真机实测踩过的坑：不套 verticalScroll + imePadding 时，键盘一弹出来
        // 就把底部的「发送」按钮整个挤出屏幕（Column 不滚动，点了都点不到）。
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !sending,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = TextHi),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = Stroke, cursorColor = Cyan
                ),
                label = { Text("Recipient", color = TextMu, fontSize = 12.sp) }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending,
                minLines = 4,
                maxLines = 8,
                textStyle = TextStyle(fontSize = 14.sp, color = TextHi),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = Stroke, cursorColor = Cyan
                ),
                label = { Text("Message", color = TextMu, fontSize = 12.sp) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${cnt.length} chars · ${cnt.parts} parts · ${if (cnt.unicode) "UCS2" else "GSM7"}",
                color = TextMu, fontSize = 11.sp
            )
            if (bodyHexPreview.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("Encoded $bodyHexPreview", color = TextMu, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(18.dp))
            Btn(
                if (sending) "Sending…" else "Send",
                if (sending || number.isBlank() || body.isBlank()) TextMu else Cyan,
                enabled = !sending && number.isNotBlank() && body.isNotBlank()
            ) {
                onSend(number.trim(), body.trim())
            }
            Spacer(Modifier.height(14.dp))
            HintBox(
                "Messages are sent from the SIM card in the device and incur carrier SMS charges.\n" +
                    "The body is encoded as UTF-16BE hex with a GSM7 / UCS2 tag as the device expects; " +
                    "Chinese text counts as UCS2, limited to 70 characters per part, and longer text is split automatically.\n" +
                    "This app never retries automatically, so one tap cannot send duplicates.",
                TextMu
            )
        }
    }
}

// ─────────────────────────── 小工具 ───────────────────────────

@Composable
private fun Btn(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color.copy(alpha = 0.14f) else PanelHi)
            .border(1.dp, if (enabled) color.copy(alpha = 0.4f) else Stroke, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
