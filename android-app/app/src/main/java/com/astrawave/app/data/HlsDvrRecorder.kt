package com.astrawave.app.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Minimal fail-closed HLS recorder for explicitly DVR-authorized customer sources.
 * Supports unencrypted MPEG-TS media playlists and master-playlist selection. AES keys,
 * byte ranges and fMP4 EXT-X-MAP playlists are rejected until those transports are implemented.
 */
class HlsDvrRecorder {
    data class Result(val segments: Int, val bytes: Long)

    fun record(
        playlistUrl: String,
        target: File,
        endEpochMs: Long,
        shouldStop: () -> Boolean,
    ): Result {
        var mediaUrl = selectMediaPlaylist(playlistUrl)
        val seen = linkedSetOf<String>()
        var segments = 0
        var bytes = 0L
        target.parentFile?.mkdirs()
        target.outputStream().buffered(128 * 1024).use { output ->
            while (System.currentTimeMillis() < endEpochMs && !shouldStop()) {
                val playlist = fetchText(mediaUrl)
                validateMediaPlaylist(playlist)
                val parsed = parseMediaPlaylist(mediaUrl, playlist)
                var wrote = false
                parsed.segmentUrls.forEach { segmentUrl ->
                    if (System.currentTimeMillis() >= endEpochMs || shouldStop()) return@forEach
                    if (!seen.add(segmentUrl)) return@forEach
                    val segmentBytes = fetchBytes(segmentUrl)
                    output.write(segmentBytes)
                    segments++
                    bytes += segmentBytes.size
                    wrote = true
                }
                output.flush()
                if (parsed.endList) break
                val delay = ((parsed.targetDurationSeconds.coerceAtLeast(2) * 500L).coerceIn(1_000L, 6_000L))
                if (!wrote) Thread.sleep(delay) else Thread.sleep(delay.coerceAtMost(2_000L))
            }
        }
        if (segments == 0 || bytes <= 0L) throw IOException("HLS source returned no recordable media segments")
        return Result(segments, bytes)
    }

    private fun selectMediaPlaylist(url: String): String {
        val text = fetchText(url)
        if (!text.lineSequence().any { it.startsWith("#EXT-X-STREAM-INF") }) return url
        val lines = text.lines()
        val variants = mutableListOf<Pair<Long, String>>()
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return@forEachIndexed
            variants += bandwidth to resolve(url, next.trim())
        }
        return variants.maxByOrNull { it.first }?.second ?: throw IOException("HLS master playlist has no usable variants")
    }

    private data class Parsed(val segmentUrls: List<String>, val targetDurationSeconds: Int, val endList: Boolean)

    private fun parseMediaPlaylist(baseUrl: String, text: String): Parsed {
        val target = Regex("#EXT-X-TARGETDURATION:(\\d+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 6
        val segments = text.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { resolve(baseUrl, it) }
            .toList()
        return Parsed(segments, target, text.contains("#EXT-X-ENDLIST", ignoreCase = true))
    }

    private fun validateMediaPlaylist(text: String) {
        if (!text.trimStart().startsWith("#EXTM3U")) throw IOException("Source is not a valid HLS playlist")
        if (text.contains("#EXT-X-MAP", ignoreCase = true)) throw IOException("fMP4 HLS DVR is not enabled for this source yet")
        if (text.contains("#EXT-X-BYTERANGE", ignoreCase = true)) throw IOException("Byte-range HLS DVR is not enabled for this source yet")
        text.lineSequence().filter { it.startsWith("#EXT-X-KEY", ignoreCase = true) }.forEach { line ->
            val method = Regex("METHOD=([^,]+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)?.uppercase(Locale.US)
            if (method != null && method != "NONE") throw IOException("Encrypted HLS DVR is not enabled for this source")
        }
    }

    private fun fetchText(url: String): String = String(fetchBytes(url), Charsets.UTF_8)

    private fun fetchBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AstraWave-DVR/1.0")
            connection.connect()
            if (connection.responseCode !in 200..299) throw IOException("HLS source returned HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun resolve(base: String, value: String): String = URL(URL(base), value).toString()
}
