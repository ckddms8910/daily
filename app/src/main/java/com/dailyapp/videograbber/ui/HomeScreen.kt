package com.dailyapp.videograbber.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dailyapp.videograbber.model.DownloadItem
import com.dailyapp.videograbber.model.DownloadState
import com.dailyapp.videograbber.model.DownloadType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: DownloadViewModel, onOpenVpnSettings: () -> Unit) {
    val items by viewModel.items.collectAsState()
    val urlInput by viewModel.urlInput.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("영상 다운로드") },
                actions = {
                    IconButton(onClick = onOpenVpnSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "VPN 설정")
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = viewModel::onUrlChanged,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("영상 URL (mp4, m3u8 등)") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::startDownload) {
                    Text("다운로드")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("다운로드한 영상이 여기에 표시됩니다")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onCancel = { viewModel.cancelDownload(item.id) },
                            onRemove = { viewModel.removeDownload(item.id) },
                            onPlay = { playVideo(context, item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        text = if (item.type == DownloadType.HLS) "HLS 스트림" else "직접 파일",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                when (item.state) {
                    DownloadState.SUCCEEDED -> IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                    }
                    DownloadState.QUEUED, DownloadState.RUNNING -> IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "취소")
                    }
                    else -> IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, contentDescription = "삭제")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (item.state) {
                DownloadState.RUNNING, DownloadState.QUEUED -> {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${item.progress}%", style = MaterialTheme.typography.labelSmall)
                }
                DownloadState.SUCCEEDED -> Text("완료", style = MaterialTheme.typography.labelSmall)
                DownloadState.FAILED -> Text(
                    text = "다운로드 실패 — ${item.errorMessage ?: "알 수 없는 오류"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                DownloadState.CANCELLED -> Text("취소됨", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun playVideo(context: android.content.Context, item: DownloadItem) {
    val uriString = item.fileUri ?: return
    val uri = android.net.Uri.parse(uriString)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
