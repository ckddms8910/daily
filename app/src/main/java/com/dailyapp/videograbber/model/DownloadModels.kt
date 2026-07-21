package com.dailyapp.videograbber.model

enum class DownloadType { DIRECT_FILE, HLS }

enum class DownloadState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val type: DownloadType,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Int = 0,
    val fileUri: String? = null,
    val errorMessage: String? = null
)
