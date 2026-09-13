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
            PanelCard(title = "短信", accent = Blue) {
                HintBox("短信的读取与发送都需要登录管理后台。", Amber)
                Spacer(Modifier.height(12.dp))
                Btn("去登录", Cyan) { onLoginClick() }
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
            title = { Text("删除该会话的全部短信？", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(num, color = Cyan, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "将删除 ${ids.size} 条短信，操作直接作用于设备，无法撤销。",
                        color = TextLo, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(ids)
                    confirmDelete = null
                    openNumber = null
                }) { Text("删除", color = Rose, fontSize = 14.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消", color = TextLo, fontSize = 14.sp) }
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
                Text("＋", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("新建短信", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (sms.loading && !sms.loaded) {
            item {
                PanelCard(title = "短信列表", accent = Blue) { HintBox("正在读取短信…", Amber) }
            }
        }

        sms.error?.let { err ->
            item {
                PanelCard(title = "短信列表", accent = Rose) {
                    HintBox(err, Rose)
                    Spacer(Modifier.height(12.dp))
                    Btn("重试", Cyan) { onLoad() }
                }
            }
        }

        if (sms.empty) {
            item {
                PanelCard(title = "短信列表", accent = Blue) {
                    HintBox("设备上还没有短信。点上面的「新建短信」可以发送第一条。", TextLo)
                }
            }
        }

        items(sms.threads, key = { it.number }) { t ->
            ThreadRow(t, onOpen = { onOpen(t.number) }, onDelete = { onDelete(t) })
        }

        if (sms.threads.isNotEmpty()) {
            item {
                HintBox(
                    "短信正文由设备以 UTF-16BE 十六进制下发，在手机本地解码后展示，不经过任何第三方服务。\n" +
                        "列表按号码聚合，点进去可以看到完整往来记录。",
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
                latest.failed -> { Pill("发送失败", Rose); Spacer(Modifier.width(7.dp)) }
                latest.draft -> { Pill("草稿", Amber); Spacer(Modifier.width(7.dp)) }
                latest.outgoing -> { Pill("已发送", Mint); Spacer(Modifier.width(7.dp)) }
            }
            Text(
                latest.content.replace("\n", " ").ifBlank { "（空消息）" },
                color = TextLo, fontSize = 13.sp, lineHeight = 19.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${t.count} 条", color = TextMu, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDelete) {
                Text("删除", color = Rose, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CapacityCard(state: CpeState, sms: SmsUiState, onLoad: () -> Unit) {
    val cap = sms.capacity ?: state.smsCapacity
    PanelCard(
        title = "短信",
        accent = Blue,
        trailing = {
            TextButton(onClick = onLoad, enabled = !sms.loading) {
                Text(if (sms.loading) "刷新中…" else "刷新", color = if (sms.loading) TextMu else Cyan, fontSize = 12.sp)
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("短信总数", "${sms.total}", "条", Cyan, Modifier.weight(1f))
            MetricTile("未读", "${sms.unread}", "条", if (sms.unread > 0) Amber else Mint, Modifier.weight(1f))
            MetricTile("会话", "${sms.threads.size}", "个", Violet, Modifier.weight(1f))
        }
        cap?.let { c ->
            Spacer(Modifier.height(10.dp))
            val used = c.optInt("sms_nv_rev_total") + c.optInt("sms_nv_send_total") + c.optInt("sms_nv_draftbox_total")
            KV("设备已用 / 容量", "$used / ${c.optInt("sms_nv_total")} 条")
            KV("SIM 卡容量", "${c.optInt("sms_sim_total")} 条")
            KV("存储位置", "设备内部")
        }
        state.smsParam?.let { p ->
            Spacer(Modifier.height(6.dp))
            KV("短信中心号", p.optString("sca"))
            KV("状态报告", if (p.optInt("status_report_on") != 0) "已开启" else "已关闭")
        }
        if (cap == null) {
            Spacer(Modifier.height(8.dp))
            HintBox("正在读取容量信息…", TextLo)
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
            TextButton(onClick = onBack) { Text("‹ 返回", color = Cyan, fontSize = 13.sp) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    thread.number,
                    color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace, maxLines = 1
                )
                Text("共 ${thread.count} 条", color = TextMu, fontSize = 10.sp)
            }
            TextButton(onClick = onDeleteAll) { Text("删除", color = Rose, fontSize = 13.sp) }
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
                placeholder = { Text("输入短信内容", color = TextMu, fontSize = 13.sp) }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (input.isNotEmpty()) {
                    Text(
                        "${cnt.length} 字 · ${cnt.parts} 条 · ${if (cnt.unicode) "UCS2" else "GSM7"}",
                        color = TextMu, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Btn(
                    if (sending) "发送中…" else "发送",
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
            Text(m.content.ifBlank { "（空消息）" }, color = TextHi, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.failed) {
                    Text("发送失败", color = Rose, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                } else if (m.draft) {
                    Text("草稿", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
            TextButton(onClick = onBack) { Text("‹ 返回", color = Cyan, fontSize = 13.sp) }
            Text(
                "新建短信", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
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
                label = { Text("接收号码", color = TextMu, fontSize = 12.sp) }
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
                label = { Text("短信内容", color = TextMu, fontSize = 12.sp) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${cnt.length} 字 · ${cnt.parts} 条 · ${if (cnt.unicode) "UCS2" else "GSM7"}",
                color = TextMu, fontSize = 11.sp
            )
            if (bodyHexPreview.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("编码后 $bodyHexPreview", color = TextMu, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(18.dp))
            Btn(
                if (sending) "发送中…" else "发送",
                if (sending || number.isBlank() || body.isBlank()) TextMu else Cyan,
                enabled = !sending && number.isNotBlank() && body.isNotBlank()
            ) {
                onSend(number.trim(), body.trim())
            }
            Spacer(Modifier.height(14.dp))
            HintBox(
                "短信由设备上的 SIM 卡直接发出，会产生运营商短信费用。\n" +
                    "正文会按设备要求编码成 UTF-16BE 十六进制，并标注 GSM7 / UCS2 类型；" +
                    "中文按 UCS2 计，单条上限 70 字，超出会自动分条。\n" +
                    "本应用不做自动重试，避免一次点击发出多条。",
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
