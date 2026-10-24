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
            Text("Sign in to CPE admin", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Enter the admin password printed on the device.\n" +
                        "The device locks the account after 5 consecutive failures, so double-check before submitting.",
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
                    label = { Text("Admin password", color = TextMu, fontSize = 12.sp) }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "The password is salted and hashed twice with SHA-256 on this device; the plaintext never leaves your phone.",
                    color = TextMu, fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pwd) },
                enabled = !busy && pwd.isNotEmpty()
            ) {
                Text(if (busy) "Signing in…" else "Sign in", color = if (busy) TextMu else Cyan, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel", color = TextLo, fontSize = 14.sp)
            }
        }
    )
}
