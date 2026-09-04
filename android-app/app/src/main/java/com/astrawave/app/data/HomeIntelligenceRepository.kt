package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryMediaType
import java.time.LocalDate

/**
 * Builds high-value Home shelves from the active profile's real history and AstraWave metadata.
 * Expensive detail enrichment is capped and cached so Home remains responsive.
 */
class HomeIntelligenceRepository(context: Context) {
    data class Row(
        val title: String,
        val subtitle: String,
        val items: List<AstraWaveMetadataGateway.Item>,
        val badge: String? = null,
        val priority: Int = 50,
    )

    private data class CachedRows(
        val signature: String,
        val createdAtMs: Long,
        val rows: List<Row>,
    )

    private val appContext = context.applicationContext
    private val library = LocalLibraryStore(appContext)
    private val movieContext = MovieDetailContextRepository()
    private val tvContext = TvDetailContextRepository()
    private val tmdbEpisodes = TmdbSeriesEpisodeRepository()
    private val dynamic = DynamicCollectionRepository()
    private val feedback = ProfileRecommendationStore(appContext)

    suspend fun rows(profileId: String, forceRefresh: Boolean = false): List<Row> {
        val history = library.history(profileId)
        val progressList = library.progress(profileId)
        val progress = progressList.associateBy { it.item.id }
        val prefs = feedback.snapshot(profileId)
        val signature = buildString {
            append(history.firstOrNull()?.playedAtEpochMs ?: 0L)
            append(':')
            append(progressList.maxOfOrNull { it.updatedAtEpochMs } ?: 0L)
            append(':')
            append(prefs.preferredGenres.sorted().joinToString(","))
            append(':')
            append(prefs.dislikedGenres.sorted().joinToString(","))
        }
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            synchronized(cache) {
                cache[profileId]
                    ?.takeIf { it.signature == signature && now - it.createdAtMs <= CACHE_TTL_MS }
                    ?.let { return it.rows }
            }
        }

        val rows = mutableListOf<Row>()
        val recentMovieIds = history.asSequence()
            .filter { it.item.type == LibraryMediaType.MOVIE }
            .mapNotNull { tmdbMovieId(it.item.sourceId ?: it.item.id) }
            .distinct().take(3).toList()
        val recentSeriesIds = history.asSequence()
            .mapNotNull { entry ->
                when (entry.item.type) {
                    LibraryMediaType.SERIES -> tmdbSeriesId(entry.item.sourceId ?: entry.item.id)
                    LibraryMediaType.EPISODE -> tmdbSeriesIdFromEpisode(entry.item.sourceId ?: entry.item.id)
                    else -> null
                }
            }
            .distinct().take(3).toList()

        // Context lookups are intentionally capped. The same profile result is then reused from cache.
        val movieContexts = recentMovieIds.mapNotNull { id -> runCatching { id to movieContext.load(id) }.getOrNull() }
        val tvContexts = recentSeriesIds.mapNotNull { id -> runCatching { id to tvContext.load(id) }.getOrNull() }

        val newEpisodes = recentSeriesIds.flatMap { seriesId ->
            val series = runCatching { tmdbEpisodes.loadSeries(seriesId) }.getOrNull() ?: return@flatMap emptyList()
            val today = LocalDate.now()
            series.episodes
                .filter { episode ->
                    val released = runCatching { episode.released?.take(10)?.let(LocalDate::parse) }.getOrNull()
                    released != null && !released.isAfter(today)
                }
                .sortedWith(compareByDescending<StremioEpisode> { it.released ?: "" }.thenByDescending { it.season }.thenByDescending { it.episode })
                .firstOrNull { episode -> progress[episode.id]?.completed != true }
                ?.let { episode -> listOf(AstraWaveMetadataGateway.Item(
                    id = seriesId.toString(),
                    type = "series",
                    name = series.title ?: "Series",
                    description = "S${episode.season}E${episode.episode} • ${episode.title}",
                    posterUrl = episode.thumbnail,
                    backdropUrl = episode.thumbnail,
                    releaseInfo = episode.released,
                )) }.orEmpty()
        }.distinctBy { it.id }.take(12)
        if (newEpisodes.isNotEmpty()) rows += Row(
            title = "New Episodes",
            subtitle = "Recently aired episodes from series you've been watching",
            items = newEpisodes,
            badge = "NEW EPISODE",
            priority = 10,
        )

        val franchise = movieContexts.flatMap { (_, ctx) ->
            (ctx.collection?.parts.orEmpty() + ctx.universe?.parts.orEmpty()).map(::movieItem)
        }.distinctBy { it.id }
            .filterNot { item -> recentMovieIds.any { it.toString() == item.id } }
            .take(18)
        if (franchise.isNotEmpty()) rows += Row(
            title = "Next in Franchise",
            subtitle = "Continue movie series and connected universes from titles you've watched",
            items = franchise,
            badge = "NEXT IN SERIES",
            priority = 20,
        )

        val similar = (movieContexts.flatMap { it.second.related.similar.map(::movieItem) } +
            tvContexts.flatMap { it.second.related.similar.map(::seriesItem) })
            .distinctBy { "${it.type}:${it.id}" }.take(24)
        if (similar.isNotEmpty()) rows += Row(
            title = "Because You Watched",
            subtitle = "Related movies and shows based on your recent viewing",
            items = similar,
            badge = "FOR YOU",
            priority = 30,
        )

        movieContexts.firstNotNullOfOrNull { it.second.related.director?.takeIf { p -> p.movies.isNotEmpty() } }?.let { person ->
            rows += Row("More From ${person.name}", "More movies from a director you've recently watched", person.movies.map(::movieItem).distinctBy { it.id }.take(18), "DIRECTOR", 40)
        }
        movieContexts.firstNotNullOfOrNull { it.second.related.actor?.takeIf { p -> p.movies.isNotEmpty() } }?.let { person ->
            rows += Row("More With ${person.name}", "More movies featuring cast from your recent viewing", person.movies.map(::movieItem).distinctBy { it.id }.take(18), "CAST", 42)
        }
        tvContexts.firstNotNullOfOrNull { it.second.related.creator?.takeIf { p -> p.shows.isNotEmpty() } }?.let { person ->
            rows += Row("More From ${person.name}", "Series from a creator you've been watching", person.shows.map(::seriesItem).distinctBy { it.id }.take(18), "CREATOR", 41)
        }
        tvContexts.firstNotNullOfOrNull { it.second.related.actor?.takeIf { p -> p.shows.isNotEmpty() } }?.let { person ->
            rows += Row("More With ${person.name}", "Shows featuring cast from your recent series", person.shows.map(::seriesItem).distinctBy { it.id }.take(18), "CAST", 43)
        }

        val favoriteGenres = prefs.preferredGenres
            .filterNot { liked -> prefs.dislikedGenres.any { it.equals(liked, true) } }
            .take(2)
        if (favoriteGenres.isNotEmpty()) {
            val trendingGenres = favoriteGenres.flatMap { genre ->
                runCatching { dynamic.genre(DynamicCollectionRepository.Media.MOVIE, genre, pages = 1) }.getOrDefault(emptyList()) +
                    runCatching { dynamic.genre(DynamicCollectionRepository.Media.SERIES, genre, pages = 1) }.getOrDefault(emptyList())
            }.distinctBy { "${it.type}:${it.id}" }.take(24)
            if (trendingGenres.isNotEmpty()) rows += Row(
                title = "Trending in Your Genres",
                subtitle = "Popular now in ${favoriteGenres.joinToString(", ")}",
                items = trendingGenres,
                badge = "TRENDING",
                priority = 50,
            )
        }

        val seriesHeavy = history.take(20).count { it.item.type == LibraryMediaType.EPISODE || it.item.type == LibraryMediaType.SERIES } >
            history.take(20).count { it.item.type == LibraryMediaType.MOVIE }
        val ordered = rows.filter { it.items.isNotEmpty() }
            .sortedWith(compareBy<Row> { row ->
                when {
                    seriesHeavy && row.title == "New Episodes" -> 0
                    !seriesHeavy && row.title == "Next in Franchise" -> 0
                    else -> row.priority
                }
            }.thenBy { it.title })

        synchronized(cache) {
            cache[profileId] = CachedRows(signature, now, ordered)
            while (cache.size > MAX_CACHE_PROFILES) cache.entries.firstOrNull()?.key?.let(cache::remove)
        }
        return ordered
    }

    fun invalidate(profileId: String) {
        synchronized(cache) { cache.remove(profileId) }
    }

    private fun movieItem(item: MovieDetailContextRepository.CollectionMovie) = AstraWaveMetadataGateway.Item(
        id = item.id, type = "movie", name = item.title, posterUrl = item.posterUrl,
        backdropUrl = item.backdropUrl, releaseInfo = item.releaseDate,
    )

    private fun seriesItem(item: TvDetailContextRepository.ShowItem) = AstraWaveMetadataGateway.Item(
        id = item.id, type = "series", name = item.title, posterUrl = item.posterUrl,
        backdropUrl = item.backdropUrl, releaseInfo = item.firstAirDate,
    )

    private fun tmdbMovieId(value: String): Long? {
        if (value.startsWith("tmdb:", true)) return value.substringAfterLast(':').toLongOrNull()
        return value.toLongOrNull()
    }

    private fun tmdbSeriesId(value: String): Long? {
        if (value.startsWith("tmdb:", true)) return value.substringAfterLast(':').toLongOrNull()
        return null
    }

    private fun tmdbSeriesIdFromEpisode(value: String): Long? {
        if (!value.startsWith("tmdbtv:", true)) return null
        return value.split(':').getOrNull(1)?.toLongOrNull()
    }

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_CACHE_PROFILES = 8
        private val cache = linkedMapOf<String, CachedRows>()
    }
}
