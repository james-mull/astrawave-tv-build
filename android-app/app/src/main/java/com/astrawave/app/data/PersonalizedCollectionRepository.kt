package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds profile-aware shelves for Movies and TV from the durable local library.
 * Recommendations stay deterministic and explainable: recent history, favorites,
 * watchlist, explicit genre feedback and inferred Cinemeta genres drive ranking.
 */
class PersonalizedCollectionRepository(
    context: Context,
    private val metadata: AstraWaveMetadataGateway = AstraWaveMetadataGateway(),
    private val dynamic: DynamicCollectionRepository = DynamicCollectionRepository(),
) {
    data class Shelf(
        val id: String,
        val title: String,
        val subtitle: String,
        val reason: String,
        val items: List<AstraWaveMetadataGateway.Item>,
    )

    private val library = LocalLibraryStore(context)
    private val feedback = ProfileRecommendationStore(context)

    fun shelves(
        profileId: String,
        media: DynamicCollectionRepository.Media,
    ): List<Shelf> {
        val snapshot = library.snapshot(profileId)
        val preference = feedback.snapshot(profileId)
        val blockedIds = preference.hiddenItemIds + preference.notInterestedItemIds
        val recent = snapshot.history.map { it.item }.filter { it.matches(media) }.take(25)
        val favorites = snapshot.favorites.map { it.item }.filter { it.matches(media) }.take(20)
        val watchlist = snapshot.watchlist.map { it.item }.filter { it.matches(media) }.take(20)
        val saved = (watchlist + favorites).distinctBy { it.id }
        val seenTitles = recent.map { normalize(it.title) }.toSet()

        val genreSeeds = (recent.take(12) + favorites.take(8) + watchlist.take(8)).distinctBy { it.id }
        val genreCounts = linkedMapOf<String, Int>()
        genreSeeds.forEach { item ->
            genresFor(item, media).forEach { genre -> genreCounts[genre] = (genreCounts[genre] ?: 0) + 1 }
        }
        val inferredGenres = genreCounts.entries.sortedByDescending { it.value }.map { it.key }
        val favoriteGenres = (preference.preferredGenres.toList() + inferredGenres)
            .distinctBy { it.lowercase() }
            .filterNot { genre -> preference.dislikedGenres.any { it.equals(genre, true) } }
            .take(4)

        fun filter(items: List<AstraWaveMetadataGateway.Item>, removeSeen: Boolean = true) = items
            .asSequence()
            .distinctBy { it.id }
            .filterNot { item -> item.feedbackKeys().any { it in blockedIds } }
            .filterNot { item -> removeSeen && normalize(item.name) in seenTitles }
            .take(50)
            .toList()

        val shelves = mutableListOf<Shelf>()

        if (recent.isNotEmpty() && favoriteGenres.isNotEmpty()) {
            val genres = favoriteGenres.take(2)
            val items = filter(genres.flatMap { genre ->
                runCatching { dynamic.genre(media, genre, pages = 3) }.getOrDefault(emptyList())
            })
            if (items.isNotEmpty()) {
                shelves += Shelf(
                    id = "because-you-watched",
                    title = "Because You Watched",
                    subtitle = "Fresh ${genres.joinToString(" + ")} picks based on this profile",
                    reason = "Recent viewing and saved titles show the strongest affinity for ${genres.joinToString(" and ")}.",
                    items = items,
                )
            }
        }

        recent.firstOrNull()?.let { seed ->
            val seedGenres = genresFor(seed, media)
                .filterNot { genre -> preference.dislikedGenres.any { it.equals(genre, true) } }
                .take(3)
            if (seedGenres.isNotEmpty()) {
                val items = filter(seedGenres.flatMap { genre ->
                    runCatching { dynamic.genre(media, genre, pages = 2) }.getOrDefault(emptyList())
                }.filterNot { normalize(it.name) == normalize(seed.title) })
                if (items.isNotEmpty()) {
                    shelves += Shelf(
                        id = "more-like-last",
                        title = "More Like ${seed.title}",
                        subtitle = "Similar ${seedGenres.take(2).joinToString(" + ")} picks",
                        reason = "You watched ${seed.title} recently, so AstraWave matched its strongest genres and removed titles you just watched or hid.",
                        items = items,
                    )
                }
            }
        }

        recent.take(8).firstNotNullOfOrNull { seed ->
            franchiseQuery(seed.title)?.let { query -> seed to query }
        }?.let { (seed, query) ->
            val items = filter(
                runCatching { metadata.search(query) }.getOrDefault(emptyList())
                    .filter { it.matches(media) }
                    .filterNot { normalize(it.name) == normalize(seed.title) },
                removeSeen = true,
            )
            if (items.isNotEmpty()) {
                shelves += Shelf(
                    id = "continue-franchise",
                    title = "Continue This Franchise",
                    subtitle = "More from $query",
                    reason = "${seed.title} belongs to the $query franchise. These are other matching titles you have not recently watched.",
                    items = items,
                )
            }
        }

        if (favoriteGenres.isNotEmpty()) {
            val items = filter(favoriteGenres.take(3).flatMap { genre ->
                runCatching { dynamic.genre(media, genre, pages = 2) }.getOrDefault(emptyList())
            })
            if (items.isNotEmpty()) {
                shelves += Shelf(
                    id = "favorite-genres",
                    title = "Your Favorite Genres",
                    subtitle = favoriteGenres.take(3).joinToString(" • "),
                    reason = if (preference.preferredGenres.isNotEmpty()) {
                        "This combines genres you explicitly marked as favorites with genres inferred from your viewing history."
                    } else {
                        "These genres appear most often across your recent viewing, favorites and watchlist."
                    },
                    items = items,
                )
            }
        }

        if (saved.isNotEmpty()) {
            val items = saved.map { it.asMetadata(media) }
                .filterNot { it.feedbackKeys().any { key -> key in preference.hiddenItemIds } }
                .distinctBy { it.id }
                .take(40)
            if (items.isNotEmpty()) {
                shelves += Shelf(
                    id = "recently-saved",
                    title = "Recently Saved",
                    subtitle = "Your watchlist and favorites together",
                    reason = "These are titles this profile explicitly saved or favorited.",
                    items = items,
                )
            }
        }

        val familyAffinity = favoriteGenres.any { it.equals("Family", true) || it.equals("Animation", true) }
        if (familyAffinity && preference.dislikedGenres.none { it.equals("Family", true) }) {
            val items = filter(runCatching { dynamic.genre(media, "Family", pages = 3) }.getOrDefault(emptyList()))
            if (items.isNotEmpty()) {
                shelves += Shelf(
                    id = "family-night",
                    title = "For Family Night",
                    subtitle = "Family-friendly picks tuned from this profile",
                    reason = "Family or animation viewing is one of this profile's stronger signals.",
                    items = items,
                )
            }
        }

        return shelves.distinctBy { it.id }
    }

    private fun LibraryItemRef.matches(media: DynamicCollectionRepository.Media): Boolean = when (media) {
        DynamicCollectionRepository.Media.MOVIE -> type == LibraryMediaType.MOVIE
        DynamicCollectionRepository.Media.SERIES -> type == LibraryMediaType.SERIES || type == LibraryMediaType.EPISODE
    }

    private fun AstraWaveMetadataGateway.Item.matches(media: DynamicCollectionRepository.Media): Boolean = when (media) {
        DynamicCollectionRepository.Media.MOVIE -> type.equals("movie", true)
        DynamicCollectionRepository.Media.SERIES -> type.equals("series", true) || type.equals("tv", true)
    }

    private fun AstraWaveMetadataGateway.Item.feedbackKeys(): Set<String> = buildSet {
        add(id)
        extractImdbId(id)?.let(::add)
    }

    private fun LibraryItemRef.asMetadata(media: DynamicCollectionRepository.Media): AstraWaveMetadataGateway.Item {
        val resolvedId = extractImdbId(sourceId) ?: extractImdbId(id) ?: id
        return AstraWaveMetadataGateway.Item(
            id = resolvedId,
            type = if (media == DynamicCollectionRepository.Media.MOVIE) "movie" else "series",
            name = title,
            posterUrl = posterUrl,
            description = "Saved in your AstraWave library",
        )
    }

    private fun genresFor(
        item: LibraryItemRef,
        media: DynamicCollectionRepository.Media,
    ): List<String> {
        val cacheKey = "${media.name}:${item.id}:${item.title}"
        genreCache[cacheKey]?.let { return it }
        val imdb = extractImdbId(item.sourceId)
            ?: extractImdbId(item.id)
            ?: runCatching {
                metadata.search(item.title)
                    .firstOrNull { it.matches(media) && it.id.startsWith("tt", true) }
                    ?.id
            }.getOrNull()
        if (imdb.isNullOrBlank()) return emptyList()
        val genres = runCatching { fetchGenres(media, imdb) }.getOrDefault(emptyList())
        genreCache[cacheKey] = genres
        return genres
    }

    private fun fetchGenres(
        media: DynamicCollectionRepository.Media,
        imdbId: String,
    ): List<String> {
        val connection = URL("https://v3-cinemeta.strem.io/meta/${media.cinemetaType}/$imdbId.json").openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "AstraWave/1.0")
        val code = connection.responseCode
        if (code !in 200..299) return emptyList()
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val meta = JSONObject(body).optJSONObject("meta") ?: return emptyList()
        val genres = meta.optJSONArray("genres") ?: return emptyList()
        return buildList {
            for (i in 0 until genres.length()) genres.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun extractImdbId(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return Regex("tt\\d{5,12}", RegexOption.IGNORE_CASE).find(value)?.value
    }

    private fun franchiseQuery(title: String): String? {
        val normalized = title.lowercase()
        val known = listOf(
            "harry potter" to "Harry Potter", "star wars" to "Star Wars", "mission impossible" to "Mission Impossible",
            "john wick" to "John Wick", "fast & furious" to "Fast Furious", "fast and furious" to "Fast Furious",
            "jurassic" to "Jurassic", "avengers" to "Avengers", "spider-man" to "Spider-Man", "spiderman" to "Spider-Man",
            "batman" to "Batman", "superman" to "Superman", "lord of the rings" to "Lord of the Rings", "the hobbit" to "The Hobbit",
            "rocky" to "Rocky", "creed" to "Creed", "scream" to "Scream", "insidious" to "Insidious",
            "conjuring" to "The Conjuring", "matrix" to "Matrix", "terminator" to "Terminator", "alien" to "Alien",
            "predator" to "Predator", "james bond" to "James Bond", "bourne" to "Bourne",
        )
        return known.firstOrNull { normalized.contains(it.first) }?.second
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "").trim()

    companion object {
        private val genreCache = ConcurrentHashMap<String, List<String>>()
    }
}
