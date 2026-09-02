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
        return parseItems(json)
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
            items = parseItems(root),
        )
    }

    private fun parseItems(json: String): List<TmdbItem> = parseItems(JSONObject(json))

    private fun parseItems(root: JSONObject): List<TmdbItem> {
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
                    )
                )
            }
        }
    }
}
