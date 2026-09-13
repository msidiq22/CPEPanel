package com.cpepanel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cpepanel.data.CpeViewModel
import com.cpepanel.ui.AppRoot
import com.cpepanel.ui.theme.CpePanelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CpePanelTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val vm: CpeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val smsState by vm.sms.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    // 短信的发送/删除结果关系到"短信有没有真的发出去"，用长提示，
    // 免得用户还没读到就消失了
    LaunchedEffect(smsState.toast) {
        smsState.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.consumeSmsToast()
        }
    }

    AppRoot(state = state, sms = smsState, vm = vm)
}
