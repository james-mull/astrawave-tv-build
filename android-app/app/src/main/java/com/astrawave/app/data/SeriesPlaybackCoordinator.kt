package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ScrapeRequest

/**
 * Resolves the next episode for compatible Stremio/Cinemeta series identities and prepares
 * the same ranked/health-checked source set used by the normal episode details flow.
 */
class SeriesPlaybackCoordinator(context: Context) {
    data class NextEpisodePlan(
        val seriesId: String,
        val episode: StremioEpisode,
        val urls: List<String>,
    )

    private val episodes = StremioSeriesEpisodeRepository()
    private val sources = UnifiedVodSourceRepository(context)

    suspend fun prepareNext(currentEpisodeId: String?, profileId: String = "default"): NextEpisodePlan? {
        val current = currentEpisodeId?.takeIf { it.isNotBlank() } ?: return null
        val seriesId = seriesIdFromEpisodeId(current) ?: return null
        val ordered = runCatching { episodes.load(seriesId) }.getOrDefault(emptyList())
            .filter { it.season > 0 && it.episode > 0 }
            .sortedWith(compareBy<StremioEpisode> { it.season }.thenBy { it.episode })
        val index = ordered.indexOfFirst { it.id == current }
        if (index < 0 || index + 1 >= ordered.size) return null
        val next = ordered[index + 1]
        val resolved = runCatching {
            sources.discover(
                ScrapeRequest(
                    title = next.title,
                    season = next.season,
                    episode = next.episode,
                    externalIds = mapOf(
                        "stremio_id" to next.id,
                        "stremio_type" to "series",
                    ),
                ),
                profileId = profileId,
            )
        }.getOrDefault(emptyList())
        val urls = resolved.map { it.link.url }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty()) return null
        return NextEpisodePlan(seriesId = seriesId, episode = next, urls = urls)
    }

    private fun seriesIdFromEpisodeId(id: String): String? {
        // Cinemeta episode IDs are normally imdbId:season:episode. Keep a conservative parser so
        // arbitrary source IDs are never mistaken for a series identity.
        val parts = id.split(':')
        if (parts.size < 3) return null
        val root = parts.firstOrNull()?.takeIf { it.matches(Regex("tt\\d+", RegexOption.IGNORE_CASE)) } ?: return null
        if (parts[1].toIntOrNull() == null || parts[2].toIntOrNull() == null) return null
        return root
    }
}
