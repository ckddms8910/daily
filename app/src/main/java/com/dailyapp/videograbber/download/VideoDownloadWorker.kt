package com.dailyapp.videograbber.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VideoDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL)
        val title = inputData.getString(KEY_TITLE) ?: "video"
        val isHls = inputData.getBoolean(KEY_IS_HLS, false)

        if (url.isNullOrBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to DownloadException.invalidUrl().message))
        }

        setForeground(createForegroundInfo(0))

        val saver = MediaStoreSaver(applicationContext)
        var target: Uri? = null
        try {
            target = if (isHls) downloadHls(url, title, saver) else downloadDirectFile(url, title, saver)
            Result.success(workDataOf(KEY_RESULT_URI to target.toString()))
        } catch (e: DownloadException) {
            target?.let { saver.discard(it) }
            Result.failure(workDataOf(KEY_ERROR to e.message))
        } catch (e: SocketTimeoutException) {
            target?.let { saver.discard(it) }
            Result.failure(workDataOf(KEY_ERROR to DownloadException.networkError(e).message))
        } catch (e: IOException) {
            target?.let { saver.discard(it) }
            Result.failure(workDataOf(KEY_ERROR to DownloadException.networkError(e).message))
        } catch (e: Exception) {
            target?.let { saver.discard(it) }
            Result.failure(workDataOf(KEY_ERROR to DownloadException.unknown(e).message))
        }
    }

    private suspend fun downloadDirectFile(url: String, title: String, saver: MediaStoreSaver): Uri {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw if (response.code == 404) DownloadException.notFound(url) else DownloadException.httpError(response.code)
            }
            val body = response.body ?: throw DownloadException.notAVideo()
            val contentType = response.header("Content-Type") ?: ""
            if (contentType.startsWith("text/html")) {
                throw DownloadException.notAVideo()
            }

            val contentLength = body.contentLength()
            val extension = guessExtension(url, contentType)
            val target = saver.createVideoEntry("$title.$extension")
            var readTotal = 0L
            saver.openOutputStream(target).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        readTotal += read
                        if (contentLength > 0) {
                            reportProgress(((readTotal * 100) / contentLength).toInt())
                        }
                    }
                }
            }
            if (readTotal == 0L) {
                saver.discard(target)
                throw DownloadException.notAVideo()
            }
            saver.finish(target)
            return target
        }
    }

    private suspend fun downloadHls(url: String, title: String, saver: MediaStoreSaver): Uri {
        val masterContent = fetchText(url)
        val mediaPlaylistUrl = if (M3u8Parser.isMasterPlaylist(masterContent)) {
            M3u8Parser.resolveMediaPlaylistUrl(masterContent, url)
        } else {
            url
        }
        val mediaContent = if (mediaPlaylistUrl == url) masterContent else fetchText(mediaPlaylistUrl)

        if (mediaContent.contains("METHOD=SAMPLE-AES")) {
            throw DownloadException.unsupportedProtection()
        }

        val playlist = M3u8Parser.parseMediaPlaylist(mediaContent, mediaPlaylistUrl)
        if (playlist.segments.isEmpty()) throw DownloadException.emptyPlaylist()

        val target = saver.createVideoEntry("$title.ts")
        val keyCache = HashMap<String, ByteArray>()

        saver.openOutputStream(target).use { out ->
            playlist.segments.forEachIndexed { index, segment ->
                val segmentBytes = fetchBytes(segment.uri)
                val plain = if (segment.key != null) {
                    if (segment.key.method != "AES-128") throw DownloadException.unsupportedProtection()
                    val keyBytes = keyCache.getOrPut(segment.key.uri) {
                        try {
                            fetchBytes(segment.key.uri)
                        } catch (e: DownloadException) {
                            throw DownloadException.keyFetchFailed()
                        }
                    }
                    decryptAes128(segmentBytes, keyBytes, segment.key.ivHex, index)
                } else {
                    segmentBytes
                }
                out.write(plain)
                reportProgress(((index + 1) * 100) / playlist.segments.size)
            }
        }
        saver.finish(target)
        return target
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, ivHex: String?, sequence: Int): ByteArray {
        val iv = if (ivHex != null) {
            ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            val buffer = java.nio.ByteBuffer.allocate(16)
            buffer.putInt(12, sequence)
            buffer.array()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun fetchText(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw if (response.code == 404) DownloadException.notFound(url) else DownloadException.httpError(response.code)
            }
            return response.body?.string()?.takeIf { it.isNotBlank() } ?: throw DownloadException.notAVideo()
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw if (response.code == 404) DownloadException.notFound(url) else DownloadException.httpError(response.code)
            }
            return response.body?.bytes() ?: throw DownloadException.notAVideo()
        }
    }

    private suspend fun reportProgress(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        setProgress(workDataOf(KEY_PROGRESS to clamped))
        setForeground(createForegroundInfo(clamped))
    }

    private fun guessExtension(url: String, contentType: String): String {
        val fromUrl = url.substringBefore("?").substringAfterLast('.', "")
        if (fromUrl.length in 2..4 && fromUrl.all { it.isLetterOrDigit() }) return fromUrl
        return when {
            contentType.contains("mp4") -> "mp4"
            contentType.contains("webm") -> "webm"
            contentType.contains("quicktime") -> "mov"
            contentType.contains("x-matroska") -> "mkv"
            else -> "mp4"
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val channelId = "downloads"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "다운로드", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("영상 다운로드 중")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_IS_HLS = "is_hls"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 4201
    }
}
