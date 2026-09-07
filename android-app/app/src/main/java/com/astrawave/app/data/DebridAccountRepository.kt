package com.astrawave.app.data

import com.astrawave.app.core.ScrapedLink
import org.json.JSONObject

/**
 * Optional, user-linked Real-Debrid account support.
 *
 * AstraWave never uses this service to discover titles or files. The account may only optimize a
 * URL that the normal VOD resolver has already accepted as an authorized playback candidate.
 * Access tokens remain encrypted in the existing Android-Keystore-backed credential store.
 */
class DebridAccountRepository(private val context: android.content.Context) {
    data class AccountStatus(
        val connected: Boolean,
        val username: String? = null,
        val type: String? = null,
        val expiration: String? = null,
        val error: String? = null,
    )

    private val credentials = PersonalMediaCredentialStore(context.applicationContext)

    fun saveRealDebridToken(profileId: String, token: String) {
        credentials.saveToken(key(profileId), token.trim())
    }

    fun disconnectRealDebrid(profileId: String) {
        credentials.clear(key(profileId))
    }

    fun hasRealDebrid(profileId: String): Boolean =
        credentials.loadToken(key(profileId))?.isNotBlank() == true

    fun verifyRealDebrid(profileId: String): AccountStatus {
        val token = credentials.loadToken(key(profileId))
            ?: return AccountStatus(connected = false, error = "Real-Debrid is not connected")
        return runCatching {
            val json = RealDebridClient(token).user()
            AccountStatus(
                connected = true,
                username = json.optString("username").takeIf(String::isNotBlank),
                type = json.optString("type").takeIf(String::isNotBlank),
                expiration = json.optString("expiration").takeIf(String::isNotBlank),
            )
        }.getOrElse { error ->
            AccountStatus(connected = false, error = error.message ?: "Real-Debrid account verification failed")
        }
    }

    /**
     * Attempts to improve already-authorized candidates. Any API error simply preserves the
     * original source so linked debrid can never make normal playback worse.
     */
    fun optimize(
        profileId: String,
        sources: List<ResolvedSource>,
        maxCandidates: Int = 6,
    ): List<ResolvedSource> {
        val token = credentials.loadToken(key(profileId)) ?: return sources
        if (sources.isEmpty()) return sources
        val client = RealDebridClient(token)
        val optimizedByOriginal = sources.take(maxCandidates.coerceIn(1, 12)).mapNotNull { source ->
            val optimized = runCatching { client.unrestrictLink(source.link.url) }.getOrNull() ?: return@mapNotNull null
            val url = optimized.optString("download").trim().takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            if (url == source.link.url) return@mapNotNull null
            val health = runCatching { StreamHealthChecker.check(url) }.getOrNull()
            if (health?.reachable != true) return@mapNotNull null
            val filename = optimized.optString("filename").takeIf(String::isNotBlank)
            val quality = inferQuality(filename.orEmpty()).takeIf { it != "Auto" } ?: source.link.quality
            val link = ScrapedLink(
                url = url,
                sourceName = "Real-Debrid • ${source.link.sourceName}",
                quality = quality,
                mimeType = health.contentType ?: source.link.mimeType,
                language = source.link.language,
                direct = true,
                licenseLabel = "User-linked debrid optimization",
                attribution = source.link.attribution,
            )
            source.link.url to ResolvedSource(
                link = link,
                reachable = true,
                latencyMs = health.latencyMs,
                contentType = health.contentType ?: source.contentType,
                score = source.score + 140,
            )
        }.toMap()

        return sources.map { source -> optimizedByOriginal[source.link.url] ?: source }
            .groupBy { normalizeUrl(it.link.url) }
            .mapNotNull { (_, duplicates) -> duplicates.maxByOrNull { it.score } }
            .sortedWith(compareByDescending<ResolvedSource> { it.score }.thenBy { it.latencyMs ?: Long.MAX_VALUE })
    }

    private fun key(profileId: String) = "debrid:real-debrid:${profileId.ifBlank { "default" }}"

    private fun normalizeUrl(value: String) = value.trim().substringBefore('#').trimEnd('/').lowercase()

    private fun inferQuality(label: String): String = when {
        label.contains("2160", true) || label.contains("4k", true) || label.contains("uhd", true) -> "4K"
        label.contains("1440", true) -> "1440p"
        label.contains("1080", true) || label.contains("fhd", true) -> "1080p"
        label.contains("720", true) || label.contains("hd", true) -> "720p"
        label.contains("480", true) -> "480p"
        else -> "Auto"
    }
}
