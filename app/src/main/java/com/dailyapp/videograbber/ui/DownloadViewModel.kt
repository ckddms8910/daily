package com.dailyapp.videograbber.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dailyapp.videograbber.data.DownloadStore
import com.dailyapp.videograbber.download.VideoDownloadWorker
import com.dailyapp.videograbber.model.DownloadItem
import com.dailyapp.videograbber.model.DownloadState
import com.dailyapp.videograbber.model.DownloadType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val store = DownloadStore(application)

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    init {
        _items.value = store.loadAll()
        _items.value.forEach { observeWork(it.id) }
    }

    fun onUrlChanged(value: String) {
        _urlInput.value = value
    }

    fun startDownload() {
        val url = _urlInput.value.trim()
        if (url.isEmpty()) return

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            addFailedPlaceholder(url, "올바른 URL이 아닙니다. http:// 또는 https:// 로 시작하는 주소를 입력해 주세요.")
            _urlInput.value = ""
            return
        }

        val isHls = url.substringBefore("?").substringAfterLast('.', "").equals("m3u8", ignoreCase = true)
        val id = UUID.randomUUID().toString()
        val title = url.substringAfterLast('/').substringBefore('?').ifBlank { "video_$id" }
            .substringBeforeLast('.')
            .ifBlank { "video_$id" }

        val item = DownloadItem(
            id = id,
            url = url,
            title = title,
            type = if (isHls) DownloadType.HLS else DownloadType.DIRECT_FILE,
            state = DownloadState.QUEUED
        )
        updateItems { it + item }
        _urlInput.value = ""

        val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(
                workDataOf(
                    VideoDownloadWorker.KEY_URL to url,
                    VideoDownloadWorker.KEY_TITLE to title,
                    VideoDownloadWorker.KEY_IS_HLS to isHls
                )
            )
            .addTag(id)
            .build()

        workManager.enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, request)
        observeWork(id)
    }

    fun cancelDownload(id: String) {
        workManager.cancelUniqueWork(id)
        updateItems { list -> list.map { if (it.id == id) it.copy(state = DownloadState.CANCELLED) else it } }
    }

    fun removeDownload(id: String) {
        workManager.cancelUniqueWork(id)
        updateItems { list -> list.filterNot { it.id == id } }
    }

    private fun addFailedPlaceholder(url: String, reason: String) {
        val id = UUID.randomUUID().toString()
        updateItems {
            it + DownloadItem(
                id = id,
                url = url,
                title = url,
                type = DownloadType.DIRECT_FILE,
                state = DownloadState.FAILED,
                errorMessage = reason
            )
        }
    }

    private fun observeWork(id: String) {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(id).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                applyWorkInfo(id, info)
            }
        }
    }

    private fun applyWorkInfo(id: String, info: WorkInfo) {
        val progress = info.progress.getInt(VideoDownloadWorker.KEY_PROGRESS, -1)
        val state = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.QUEUED
            WorkInfo.State.RUNNING -> DownloadState.RUNNING
            WorkInfo.State.SUCCEEDED -> DownloadState.SUCCEEDED
            WorkInfo.State.FAILED -> DownloadState.FAILED
            WorkInfo.State.CANCELLED -> DownloadState.CANCELLED
        }
        val resultUri = info.outputData.getString(VideoDownloadWorker.KEY_RESULT_URI)
        val error = info.outputData.getString(VideoDownloadWorker.KEY_ERROR)

        updateItems { list ->
            list.map { existing ->
                if (existing.id == id) {
                    existing.copy(
                        state = state,
                        progress = if (progress >= 0) progress else existing.progress,
                        fileUri = resultUri ?: existing.fileUri,
                        errorMessage = error ?: existing.errorMessage
                    )
                } else existing
            }
        }
    }

    private fun updateItems(transform: (List<DownloadItem>) -> List<DownloadItem>) {
        _items.value = transform(_items.value)
        store.saveAll(_items.value)
    }
}
