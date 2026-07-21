package com.dailyapp.videograbber.download

import java.net.URI

data class HlsKey(val uri: String, val method: String, val ivHex: String?)

data class HlsSegment(val uri: String, val key: HlsKey?)

data class HlsPlaylist(val segments: List<HlsSegment>)

object M3u8Parser {

    fun isMasterPlaylist(content: String): Boolean =
        content.contains("#EXT-X-STREAM-INF")

    /** Picks the highest-bandwidth variant from a master playlist. */
    fun resolveMediaPlaylistUrl(masterContent: String, masterUrl: String): String {
        val lines = masterContent.lines()
        var bestBandwidth = -1L
        var bestUri: String? = null
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val nextUri = lines.getOrNull(i + 1)?.trim()
                if (!nextUri.isNullOrBlank() && !nextUri.startsWith("#") && bandwidth >= bestBandwidth) {
                    bestBandwidth = bandwidth
                    bestUri = nextUri
                }
            }
        }
        return if (bestUri != null) resolveUrl(masterUrl, bestUri) else masterUrl
    }

    fun parseMediaPlaylist(content: String, playlistUrl: String): HlsPlaylist {
        val segments = mutableListOf<HlsSegment>()
        var currentKey: HlsKey? = null
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-KEY") -> currentKey = parseKeyAttribute(line, playlistUrl)
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> segments += HlsSegment(resolveUrl(playlistUrl, line), currentKey)
            }
        }
        return HlsPlaylist(segments)
    }

    private fun parseKeyAttribute(line: String, playlistUrl: String): HlsKey? {
        val method = Regex("METHOD=([^,]+)").find(line)?.groupValues?.get(1)?.trim() ?: return null
        if (method == "NONE") return null
        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return null
        val iv = Regex("IV=0[xX]([0-9A-Fa-f]+)").find(line)?.groupValues?.get(1)
        return HlsKey(resolveUrl(playlistUrl, uri), method, iv)
    }

    private fun resolveUrl(baseUrl: String, possiblyRelative: String): String {
        return if (possiblyRelative.startsWith("http://") || possiblyRelative.startsWith("https://")) {
            possiblyRelative
        } else {
            URI(baseUrl).resolve(possiblyRelative).toString()
        }
    }
}
