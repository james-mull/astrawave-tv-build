package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryMediaType
import java.time.LocalDate

/**
 * Builds high-value Home shelves from the active profile's real history and AstraWave metadata.
 * Expensive detail enrichment is intentionally capped to a few recent titles so Home remains fast.
 */
class HomeIntelligenceRepository(context: Context) {
    data class Row(
        val title: String,
        val subtitle: String,
        val items: List<AstraWaveMetadataGateway.Item>,
    )

    private val appContext = context.applicationContext
    private val library = LocalLibraryStore(appContext)
    private val movieContext = MovieDetailContextRepository()
    private val tvContext = TvDetailContextRepository()
    private val tmdbEpisodes = TmdbSeriesEpisodeRepository()
    private val dynamic = DynamicCollectionRepository()
    private val feedback = ProfileRecommendationStore(appContext)

    suspend fun rows(profileId: String): List<Row> {
        val history = library.history(profileId)
        val progress = library.progress(profileId).associateBy { it.item.id }
        val rows = mutableListOf<Row>()

        val recentMovieIds = history.asSequence()
            .filter { it.item.type == LibraryMediaType.MOVIE }
            .mapNotNull { tmdbMovieId(it.item.sourceId ?: it.item.id) }
            .distinct()
            .take(4)
            .toList()

        val recentSeriesIds = history.asSequence()
            .mapNotNull { entry ->
                when (entry.item.type) {
                    LibraryMediaType.SERIES -> tmdbSeriesId(entry.item.sourceId ?: entry.item.id)
                    LibraryMediaType.EPISODE -> tmdbSeriesIdFromEpisode(entry.item.sourceId ?: entry.item.id)
                    else -> null
                }
            }
            .distinct()
            .take(4)
            .toList()

        val movieContexts = recentMovieIds.mapNotNull { id -> runCatching { id to movieContext.load(id) }.getOrNull() }
        val tvContexts = recentSeriesIds.mapNotNull { id -> runCatching { id to tvContext.load(id) }.getOrNull() }

        val franchise = movieContexts.flatMap { (_, ctx) ->
            val official = ctx.collection?.parts.orEmpty()
            val universe = ctx.universe?.parts.orEmpty()
            (official + universe).map(::movieItem)
        }.distinctBy { it.id }.filterNot { item -> recentMovieIds.any { it.toString() == item.id } }.take(18)
        if (franchise.isNotEmpty()) rows += Row(
            "Next in Franchise",
            "Continue movie series and connected universes from titles you've watched",
            franchise,
        )

        val director = movieContexts.firstNotNullOfOrNull { it.second.related.director?.takeIf { person -> person.movies.isNotEmpty() } }
        director?.let { person ->
            rows += Row(
                "More From ${person.name}",
                "More movies from a director you've recently watched",
                person.movies.map(::movieItem).distinctBy { it.id }.take(18),
            )
        }

        val actor = movieContexts.firstNotNullOfOrNull { it.second.related.actor?.takeIf { person -> person.movies.isNotEmpty() } }
        actor?.let { person ->
            rows += Row(
                "More With ${person.name}",
                "More movies featuring cast from your recent viewing",
                person.movies.map(::movieItem).distinctBy { it.id }.take(18),
            )
        }

        val creator = tvContexts.firstNotNullOfOrNull { it.second.related.creator?.takeIf { person -> person.shows.isNotEmpty() } }
        creator?.let { person ->
            rows += Row(
                "More From ${person.name}",
                "Series from a creator you've been watching",
                person.shows.map(::seriesItem).distinctBy { it.id }.take(18),
            )
        }

        val cast = tvContexts.firstNotNullOfOrNull { it.second.related.actor?.takeIf { person -> person.shows.isNotEmpty() } }
        cast?.let { person ->
            rows += Row(
                "More With ${person.name}",
                "Shows featuring cast from your recent series",
                person.shows.map(::seriesItem).distinctBy { it.id }.take(18),
            )
        }

        val similar = (movieContexts.flatMap { it.second.related.similar.map(::movieItem) } +
            tvContexts.flatMap { it.second.related.similar.map(::seriesItem) })
            .distinctBy { "${it.type}:${it.id}" }
            .take(24)
        if (similar.isNotEmpty()) rows += Row(
            "Because You Watched",
            "Related movies and shows based on your recent viewing",
            similar,
        )

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
                ?.let { episode ->
                    listOf(AstraWaveMetadataGateway.Item(
                        id = seriesId.toString(),
                        type = "series",
                        name = series.title ?: "Series",
                        description = "New episode • S${episode.season}E${episode.episode} ${episode.title}",
                        posterUrl = episode.thumbnail,
                        backdropUrl = episode.thumbnail,
                        releaseInfo = episode.released,
                    ))
                }.orEmpty()
        }.distinctBy { it.id }.take(12)
        if (newEpisodes.isNotEmpty()) rows += Row(
            "New Episodes",
            "Recently aired episodes from series you've been watching",
            newEpisodes,
        )

        val prefs = feedback.snapshot(profileId)
        val favoriteGenres = prefs.preferredGenres.filterNot { liked -> prefs.dislikedGenres.any { it.equals(liked, true) } }.take(3)
        if (favoriteGenres.isNotEmpty()) {
            val trendingGenres = favoriteGenres.flatMap { genre ->
                runCatching { dynamic.genre(DynamicCollectionRepository.Media.MOVIE, genre, pages = 2) }.getOrDefault(emptyList()) +
                    runCatching { dynamic.genre(DynamicCollectionRepository.Media.SERIES, genre, pages = 2) }.getOrDefault(emptyList())
            }.distinctBy { "${it.type}:${it.id}" }.take(24)
            if (trendingGenres.isNotEmpty()) rows += Row(
                "Trending in Your Genres",
                "Popular now in ${favoriteGenres.joinToString(", ")}",
                trendingGenres,
            )
        }

        return rows.filter { it.items.isNotEmpty() }
    }

    private fun movieItem(item: MovieDetailContextRepository.CollectionMovie) = AstraWaveMetadataGateway.Item(
        id = item.id,
        type = "movie",
        name = item.title,
        description = null,
        posterUrl = item.posterUrl,
        backdropUrl = item.backdropUrl,
        releaseInfo = item.releaseDate,
    )

    private fun seriesItem(item: TvDetailContextRepository.ShowItem) = AstraWaveMetadataGateway.Item(
        id = item.id,
        type = "series",
        name = item.title,
        description = null,
        posterUrl = item.posterUrl,
        backdropUrl = item.backdropUrl,
        releaseInfo = item.firstAirDate,
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
}
