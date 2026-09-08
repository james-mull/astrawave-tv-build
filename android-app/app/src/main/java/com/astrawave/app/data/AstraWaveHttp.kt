package com.astrawave.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * Shared HTTP client for public AstraWave metadata, Live TV, EPG and compatible addon manifests.
 * It deliberately carries no cookies, credentials, or source authorization policy.
 *
 * Public feeds use a short in-memory cache and stale-on-error fallback so moving between Live,
 * Guide and Sports does not redownload the same large playlists repeatedly. A dead upstream also
 * no longer makes an already-loaded session appear empty.
 */
object AstraWaveHttp {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val FRESH_TTL_MS = 15 * 60 * 1000L
    private const val STALE_FALLBACK_TTL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_CACHE_ENTRIES = 64

    private data class CacheEntry(val fetchedAtMs: Long, val bytes: ByteArray)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun getText(url: String): String = getBytes(url).toString(Charsets.UTF_8)

    fun getBytes(url: String): ByteArray {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Unsupported URL scheme" }
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.fetchedAtMs <= FRESH_TTL_MS }?.let { return it.bytes.copyOf() }

        return try {
            val bytes = fetchBytes(url)
            cache[url] = CacheEntry(now, bytes)
            trimCache()
            bytes.copyOf()
        } catch (error: Exception) {
            cache[url]
                ?.takeIf { now - it.fetchedAtMs <= STALE_FALLBACK_TTL_MS }
                ?.bytes
                ?.copyOf()
                ?: throw error
        }
    }

    fun openDecoded(url: String): InputStream = ByteArrayInputStream(getBytes(url))

    fun invalidate(url: String? = null) {
        if (url == null) cache.clear() else cache.remove(url)
    }

    private fun fetchBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = true
            setRequestProperty("User-Agent", "AstraWave/1.0 Android")
            setRequestProperty("Accept", "application/json,text/plain,application/xml,text/xml,application/x-mpegURL,application/vnd.apple.mpegurl,*/*")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Connection", "keep-alive")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.use(::readLimited)?.toString(Charsets.UTF_8)?.take(240).orEmpty()
                error("HTTP $code${if (detail.isBlank()) "" else ": $detail"}")
            }

            val raw = connection.inputStream
            val compressed = connection.contentEncoding?.contains("gzip", ignoreCase = true) == true ||
                (url.substringBefore('?').endsWith(".gz", ignoreCase = true) && connection.contentType?.contains("gzip", true) != false)
            val input: InputStream = if (compressed) GZIPInputStream(raw) else raw
            return input.use(::readLimited)
        } finally {
            connection.disconnect()
        }
    }

    private fun trimCache() {
        if (cache.size <= MAX_CACHE_ENTRIES) return
        cache.entries
            .sortedBy { it.value.fetchedAtMs }
            .take(cache.size - MAX_CACHE_ENTRIES)
            .forEach { cache.remove(it.key) }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "Remote response exceeded ${MAX_BYTES / (1024 * 1024)} MB safety limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
