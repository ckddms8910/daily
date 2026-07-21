package com.dailyapp.videograbber.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wireguard.android.backend.Tunnel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnSettingsScreen(
    viewModel: VpnViewModel,
    onBack: () -> Unit,
    onConnectClicked: () -> Unit
) {
    val configText by viewModel.configText.collectAsState()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("개인 VPN 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                "본인이 소유한 서버 또는 이미 사용 중인 VPN 서비스에서 발급받은 " +
                    "WireGuard 설정(.conf)의 내용을 붙여넣으세요. 이 앱은 VPN 서버를 " +
                    "제공하지 않으며, 오직 클라이언트로서 연결만 수행합니다.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = configText,
                onValueChange = viewModel::onConfigChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                label = { Text("WireGuard 설정 (.conf 내용)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "상태: " + statusLabel(state),
                style = MaterialTheme.typography.titleSmall
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))

            if (state == Tunnel.State.UP) {
                OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("연결 해제")
                }
            } else {
                Button(onClick = onConnectClicked, modifier = Modifier.fillMaxWidth()) {
                    Text("연결")
                }
            }
        }
    }
}

private fun statusLabel(state: Tunnel.State): String = when (state) {
    Tunnel.State.UP -> "연결됨"
    Tunnel.State.DOWN -> "연결 안 됨"
    Tunnel.State.TOGGLE -> "전환 중"
}
