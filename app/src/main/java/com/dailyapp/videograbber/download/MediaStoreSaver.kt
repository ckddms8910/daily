package com.dailyapp.videograbber.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MediaStoreSaver(private val context: Context) {

    fun createVideoEntry(displayName: String): Uri {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoGrabber")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert returned null")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoGrabber")
                if (!dir.exists()) dir.mkdirs()
                Uri.fromFile(File(dir, displayName))
            }
        } catch (e: Exception) {
            throw DownloadException.storageError(e)
        }
    }

    fun openOutputStream(uri: Uri): OutputStream {
        return try {
            if (uri.scheme == "file") {
                FileOutputStream(File(uri.path!!))
            } else {
                context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("openOutputStream returned null")
            }
        } catch (e: Exception) {
            throw DownloadException.storageError(e)
        }
    }

    fun finish(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme != "file") {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun discard(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            // best-effort cleanup of a partial/failed download
        }
    }
}
