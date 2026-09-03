package com.astrawave.app.data

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Small in-memory artwork lookup shared by metadata repositories and Compose artwork surfaces.
 * It lets existing title-driven cards render real remote art without coupling UI components to
 * a specific metadata provider.
 */
object ArtworkRegistry {
    private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    private val urls = ConcurrentHashMap<String, String>()

    fun register(title: String, pathOrUrl: String?) {
        val key = key(title)
        if (key.isBlank() || pathOrUrl.isNullOrBlank() || pathOrUrl == "null") return
        val url = when {
            pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("http://") -> pathOrUrl
            pathOrUrl.startsWith("/") -> "$TMDB_IMAGE_BASE$pathOrUrl"
            else -> pathOrUrl
        }
        urls[key] = url
    }

    fun resolve(title: String): String? = urls[key(title)]

    private fun key(title: String): String = title
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("\\s+"), " ")
}
