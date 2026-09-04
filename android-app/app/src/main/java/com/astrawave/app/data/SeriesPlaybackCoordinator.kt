package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ScrapeRequest

/** Resolves and prepares the next episode for Cinemeta/Stremio and native TMDB series identities. */
class SeriesPlaybackCoordinator(context: Context) {
    data class NextEpisodePlan(
        val seriesId: String,
        val episode: StremioEpisode,
        val urls: List<String>,
    )

    private val stremioEpisodes = StremioSeriesEpisodeRepository()
    private val tmdbEpisodes = TmdbSeriesEpisodeRepository()
    private val sources = UnifiedVodSourceRepository(context)

    suspend fun prepareNext(currentEpisodeId: String?, profileId: String = "default"): NextEpisodePlan? {
        val current = currentEpisodeId?.takeIf { it.isNotBlank() } ?: return null
        val identity = identify(current) ?: return null
        val catalog = when (identity) {
            is SeriesIdentity.Stremio -> {
                val loaded = runCatching { stremioEpisodes.load(identity.seriesId) }.getOrDefault(emptyList())
                SeriesCatalog(identity.seriesId, null, loaded, exactStremioIds = true)
            }
            is SeriesIdentity.Tmdb -> {
                val loaded = runCatching { tmdbEpisodes.loadSeries(identity.seriesId) }.getOrElse {
                    TmdbSeriesEpisodeRepository.SeriesEpisodes(identity.seriesId, null, emptyList())
                }
                SeriesCatalog(identity.seriesId.toString(), loaded.title, loaded.episodes, exactStremioIds = false)
            }
        }

        val ordered = catalog.episodes
            .filter { it.season > 0 && it.episode > 0 }
            .sortedWith(compareBy<StremioEpisode> { it.season }.thenBy { it.episode })
        val index = ordered.indexOfFirst { it.id == current }
        if (index < 0 || index + 1 >= ordered.size) return null
        val next = ordered[index + 1]
        val requestTitle = catalog.seriesTitle?.takeIf { it.isNotBlank() } ?: next.title
        val externalIds = if (catalog.exactStremioIds) {
            mapOf("stremio_id" to next.id, "stremio_type" to "series")
        } else emptyMap()

        val resolved = runCatching {
            sources.discover(
                ScrapeRequest(
                    title = requestTitle,
                    season = next.season,
                    episode = next.episode,
                    externalIds = externalIds,
                ),
                profileId = profileId,
            )
        }.getOrDefault(emptyList())
        val urls = resolved.map { it.link.url }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty()) return null
        return NextEpisodePlan(seriesId = catalog.seriesId, episode = next, urls = urls)
    }

    private data class SeriesCatalog(
        val seriesId: String,
        val seriesTitle: String?,
        val episodes: List<StremioEpisode>,
        val exactStremioIds: Boolean,
    )

    private sealed interface SeriesIdentity {
        data class Stremio(val seriesId: String) : SeriesIdentity
        data class Tmdb(val seriesId: Long) : SeriesIdentity
    }

    private fun identify(id: String): SeriesIdentity? {
        if (id.startsWith("tmdbtv:", ignoreCase = true)) {
            val parts = id.split(':')
            if (parts.size < 4) return null
            val seriesId = parts[1].toLongOrNull() ?: return null
            if (parts[2].toIntOrNull() == null || parts[3].toIntOrNull() == null) return null
            return SeriesIdentity.Tmdb(seriesId)
        }

        // Cinemeta episode IDs are normally imdbId:season:episode.
        val parts = id.split(':')
        if (parts.size < 3) return null
        val root = parts.firstOrNull()?.takeIf { it.matches(Regex("tt\\d+", RegexOption.IGNORE_CASE)) } ?: return null
        if (parts[1].toIntOrNull() == null || parts[2].toIntOrNull() == null) return null
        return SeriesIdentity.Stremio(root)
    }
}
