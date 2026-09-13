package com.cpepanel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpepanel.ui.theme.Cyan
import com.cpepanel.ui.theme.Panel
import com.cpepanel.ui.theme.Stroke
import com.cpepanel.ui.theme.TextHi
import com.cpepanel.ui.theme.TextLo
import com.cpepanel.ui.theme.TextMu

@Composable
fun LoginDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Panel,
        title = {
            Text("登录 CPE 管理后台", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "输入设备上标注的管理员密码。\n" +
                        "设备限制连续错误 5 次后会锁定登录，请确认无误再提交。",
                    color = TextLo, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = TextHi
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Stroke,
                        cursorColor = Cyan
                    ),
                    label = { Text("管理密码", color = TextMu, fontSize = 12.sp) }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "密码在本机完成两次 SHA-256 加盐哈希后才发送，明文不会离开手机。",
                    color = TextMu, fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pwd) },
                enabled = !busy && pwd.isNotEmpty()
            ) {
                Text(if (busy) "登录中…" else "登录", color = if (busy) TextMu else Cyan, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("取消", color = TextLo, fontSize = 14.sp)
            }
        }
    )
}
