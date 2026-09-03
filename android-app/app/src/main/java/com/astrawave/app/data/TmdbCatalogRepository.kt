package com.astrawave.app.data

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Built-in AstraWave TMDB catalog definitions used by Home, Movies, TV and Discover. */
enum class AstraWaveCatalog(
    val title: String,
    val mediaType: String,
    val endpoint: String,
) {
    TRENDING_MOVIES("Trending Movies", "movie", "/trending/movie/day"),
    POPULAR_MOVIES("Popular Movies", "movie", "/movie/popular"),
    NOW_PLAYING_MOVIES("New Releases", "movie", "/movie/now_playing"),
    TOP_RATED_MOVIES("Top Rated Movies", "movie", "/movie/top_rated"),
    UPCOMING_MOVIES("Coming Soon", "movie", "/movie/upcoming"),
    TRENDING_TV("Trending TV", "tv", "/trending/tv/day"),
    POPULAR_TV("Popular TV", "tv", "/tv/popular"),
    AIRING_TODAY("Airing Today", "tv", "/tv/airing_today"),
    ON_THE_AIR("New Episodes", "tv", "/tv/on_the_air"),
    TOP_RATED_TV("Top Rated TV", "tv", "/tv/top_rated"),
}

data class TmdbCatalogPage(
    val catalog: AstraWaveCatalog,
    val page: Int,
    val totalPages: Int,
    val items: List<TmdbItem>,
)

data class TmdbPersonCredit(
    val id: Long,
    val name: String,
    val role: String? = null,
    val profilePath: String? = null,
)

data class TmdbVideo(
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val official: Boolean,
)

data class TmdbTitleDetails(
    val id: Long,
    val mediaType: String,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val cast: List<TmdbPersonCredit> = emptyList(),
    val crew: List<TmdbPersonCredit> = emptyList(),
    val videos: List<TmdbVideo> = emptyList(),
)

/**
 * Central TMDB catalog repository. TMDB remains metadata/discovery only; playback
 * resolution is handled by AstraWave's source resolver.
 */
class TmdbCatalogRepository(
    private val bearerToken: String,
    private val language: String = "en-US",
) {
    private val baseUrl = "https://api.themoviedb.org/3"

    fun isConfigured(): Boolean = bearerToken.isNotBlank()

    fun load(catalog: AstraWaveCatalog, page: Int = 1): TmdbCatalogPage {
        require(isConfigured()) { "TMDB bearer token is not configured" }
        val json = SimpleHttp.getText(
            "$baseUrl${catalog.endpoint}?language=${encode(language)}&page=$page",
            headers(),
        )
        return parsePage(catalog, json)
    }

    fun search(query: String, page: Int = 1): List<TmdbItem> {
        if (query.isBlank()) return emptyList()
        require(isConfigured()) { "TMDB bearer token is not configured" }
        val json = SimpleHttp.getText(
            "$baseUrl/search/multi?query=${encode(query)}&include_adult=false&language=${encode(language)}&page=$page",
            headers(),
        )
        return parseItems(JSONObject(json), fallbackMediaType = null)
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }

    /** Loads native title metadata plus credits and video metadata for Phase 3 detail surfaces. */
    fun loadDetails(mediaType: String, id: Long): TmdbTitleDetails {
        require(mediaType == "movie" || mediaType == "tv") { "Unsupported TMDB media type: $mediaType" }
        require(isConfigured()) { "TMDB bearer token is not configured" }
        val json = SimpleHttp.getText(
            "$baseUrl/$mediaType/$id?language=${encode(language)}&append_to_response=credits,videos",
            headers(),
        )
        return parseDetails(mediaType, JSONObject(json))
    }

    fun builtInMovieCatalogs(): List<AstraWaveCatalog> = listOf(
        AstraWaveCatalog.TRENDING_MOVIES,
        AstraWaveCatalog.POPULAR_MOVIES,
        AstraWaveCatalog.NOW_PLAYING_MOVIES,
        AstraWaveCatalog.TOP_RATED_MOVIES,
        AstraWaveCatalog.UPCOMING_MOVIES,
    )

    fun builtInTvCatalogs(): List<AstraWaveCatalog> = listOf(
        AstraWaveCatalog.TRENDING_TV,
        AstraWaveCatalog.POPULAR_TV,
        AstraWaveCatalog.AIRING_TODAY,
        AstraWaveCatalog.ON_THE_AIR,
        AstraWaveCatalog.TOP_RATED_TV,
    )

    private fun headers() = mapOf(
        "Authorization" to "Bearer $bearerToken",
        "accept" to "application/json",
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun parsePage(catalog: AstraWaveCatalog, json: String): TmdbCatalogPage {
        val root = JSONObject(json)
        return TmdbCatalogPage(
            catalog = catalog,
            page = root.optInt("page", 1),
            totalPages = root.optInt("total_pages", 1),
            items = parseItems(root, fallbackMediaType = catalog.mediaType),
        )
    }

    private fun parseDetails(mediaType: String, root: JSONObject): TmdbTitleDetails {
        val castArray = root.optJSONObject("credits")?.optJSONArray("cast")
        val crewArray = root.optJSONObject("credits")?.optJSONArray("crew")
        val videosArray = root.optJSONObject("videos")?.optJSONArray("results")
        val genresArray = root.optJSONArray("genres")

        val cast = buildList {
            if (castArray != null) {
                for (i in 0 until minOf(castArray.length(), 24)) {
                    val person = castArray.optJSONObject(i) ?: continue
                    add(
                        TmdbPersonCredit(
                            id = person.optLong("id"),
                            name = person.optString("name"),
                            role = person.optString("character").takeIf { it.isNotBlank() },
                            profilePath = person.optString("profile_path").takeIf { it.isNotBlank() && it != "null" },
                        )
                    )
                }
            }
        }
        val crew = buildList {
            if (crewArray != null) {
                for (i in 0 until crewArray.length()) {
                    val person = crewArray.optJSONObject(i) ?: continue
                    val job = person.optString("job")
                    if (job !in setOf("Director", "Writer", "Screenplay", "Creator", "Executive Producer")) continue
                    add(
                        TmdbPersonCredit(
                            id = person.optLong("id"),
                            name = person.optString("name"),
                            role = job.takeIf { it.isNotBlank() },
                            profilePath = person.optString("profile_path").takeIf { it.isNotBlank() && it != "null" },
                        )
                    )
                }
            }
        }.distinctBy { "${it.id}:${it.role}" }.take(16)
        val videos = buildList {
            if (videosArray != null) {
                for (i in 0 until videosArray.length()) {
                    val video = videosArray.optJSONObject(i) ?: continue
                    val key = video.optString("key")
                    val site = video.optString("site")
                    if (key.isBlank() || site.isBlank()) continue
                    add(
                        TmdbVideo(
                            key = key,
                            name = video.optString("name").ifBlank { "Video" },
                            site = site,
                            type = video.optString("type"),
                            official = video.optBoolean("official", false),
                        )
                    )
                }
            }
        }
        val genres = buildList {
            if (genresArray != null) {
                for (i in 0 until genresArray.length()) {
                    genresArray.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }

        val title = root.optString("title").ifBlank { root.optString("name") }
        val runtime = root.optInt("runtime", 0).takeIf { it > 0 }
            ?: root.optJSONArray("episode_run_time")?.optInt(0, 0)?.takeIf { it > 0 }
        return TmdbTitleDetails(
            id = root.optLong("id"),
            mediaType = mediaType,
            title = title,
            overview = root.optString("overview"),
            posterPath = root.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
            backdropPath = root.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
            releaseDate = root.optString(if (mediaType == "movie") "release_date" else "first_air_date").takeIf { it.isNotBlank() },
            runtimeMinutes = runtime,
            genres = genres,
            cast = cast,
            crew = crew,
            videos = videos,
        )
    }

    private fun parseItems(root: JSONObject, fallbackMediaType: String?): List<TmdbItem> {
        val array = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").ifBlank { obj.optString("name") }
                if (title.isBlank()) continue
                add(
                    TmdbItem(
                        id = obj.optLong("id"),
                        title = title,
                        overview = obj.optString("overview"),
                        posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                        backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                        mediaType = obj.optString("media_type").takeIf { it.isNotBlank() } ?: fallbackMediaType,
                    )
                )
            }
        }
    }
}
