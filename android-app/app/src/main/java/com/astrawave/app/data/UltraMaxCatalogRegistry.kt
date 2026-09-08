package com.astrawave.app.data

/**
 * Native AstraWave catalog blueprint inspired by the public Ultra MAX catalog addon.
 *
 * This intentionally models catalog/discovery behavior only. Playback remains in
 * AstraWave's reviewed/customer-authorized source pipeline.
 */
object UltraMaxCatalogRegistry {
    enum class Media { MOVIE, SERIES, BOTH }
    enum class Family {
        TRENDING,
        NEW_LATEST,
        STREAMING_SERVICE,
        GENRE,
        CURATED,
        STUDIO,
        DECADE,
        KIDS,
        UK,
        TRAKT,
    }

    data class Row(
        val id: String,
        val title: String,
        val family: Family,
        val media: Media = Media.BOTH,
        val query: String? = null,
        val genre: String? = null,
        val badge: String? = null,
        val kidsSafe: Boolean = false,
        val requiresTrakt: Boolean = false,
    )

    data class Preset(
        val id: String,
        val title: String,
        val description: String,
        val rowIds: List<String>,
    )

    val languages = listOf(
        "English", "Spanish", "French", "German", "Italian",
        "Portuguese", "Dutch", "Polish", "Turkish",
    )

    val rows = listOf(
        Row("trending_movies", "Top Movies This Week", Family.TRENDING, Media.MOVIE, badge = "HOT"),
        Row("trending_series", "Top Series This Week", Family.TRENDING, Media.SERIES, badge = "HOT"),
        Row("popular_movies", "Popular Movies", Family.TRENDING, Media.MOVIE),
        Row("popular_series", "Popular Series", Family.TRENDING, Media.SERIES),
        Row("top_rated_movies", "Top Rated Movies", Family.TRENDING, Media.MOVIE),
        Row("top_rated_series", "Top Rated Series", Family.TRENDING, Media.SERIES),

        Row("new_movies", "New & Latest Movies", Family.NEW_LATEST, Media.MOVIE, query = "new release movie", badge = "NEW"),
        Row("new_series", "New & Latest TV", Family.NEW_LATEST, Media.SERIES, query = "new series", badge = "NEW"),
        Row("digital_releases", "New Digital Releases", Family.NEW_LATEST, Media.MOVIE, query = "digital release", badge = "NEW"),
        Row("bluray_releases", "New Blu-ray Releases", Family.NEW_LATEST, Media.MOVIE, query = "blu ray release"),

        Row("netflix_movies", "Netflix Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Netflix"),
        Row("netflix_series", "Netflix Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Netflix"),
        Row("prime_movies", "Prime Video Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Prime Video"),
        Row("prime_series", "Prime Video Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Prime Video"),
        Row("disney_movies", "Disney+ Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Disney Plus"),
        Row("disney_series", "Disney+ Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Disney Plus"),
        Row("max_movies", "HBO / Max Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "HBO Max"),
        Row("max_series", "HBO / Max Series", Family.STREAMING_SERVICE, Media.SERIES, query = "HBO Max"),
        Row("apple_movies", "Apple TV+ Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Apple TV Plus"),
        Row("apple_series", "Apple TV+ Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Apple TV Plus"),
        Row("hulu_series", "Hulu Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Hulu"),
        Row("itvx_series", "ITVX", Family.STREAMING_SERVICE, Media.SERIES, query = "ITV"),
        Row("channel4_series", "Channel 4", Family.STREAMING_SERVICE, Media.SERIES, query = "Channel 4"),

        Row("genre_horror", "Horror", Family.GENRE, genre = "Horror"),
        Row("genre_action", "Action", Family.GENRE, genre = "Action"),
        Row("genre_comedy", "Comedy", Family.GENRE, genre = "Comedy"),
        Row("genre_scifi", "Sci-Fi", Family.GENRE, genre = "Sci-Fi"),
        Row("genre_mystery", "Mystery", Family.GENRE, genre = "Mystery"),
        Row("genre_family", "Family", Family.GENRE, genre = "Family", kidsSafe = true),

        Row("curated_mindfuck", "Mind-Bending Movies", Family.CURATED, Media.MOVIE, query = "mind bending movies"),
        Row("curated_twists", "Plot Twists", Family.CURATED, Media.MOVIE, query = "plot twist movies"),
        Row("curated_heists", "Heists", Family.CURATED, query = "heist"),
        Row("curated_zombies", "Zombies", Family.CURATED, query = "zombie"),
        Row("curated_time_travel", "Time Travel", Family.CURATED, query = "time travel"),

        Row("studio_marvel", "Marvel", Family.STUDIO, query = "Marvel"),
        Row("studio_dc", "DC", Family.STUDIO, query = "DC Comics"),
        Row("studio_a24", "A24", Family.STUDIO, Media.MOVIE, query = "A24"),
        Row("studio_ghibli", "Studio Ghibli", Family.STUDIO, Media.MOVIE, query = "Studio Ghibli", kidsSafe = true),
        Row("studio_warner", "Warner Bros.", Family.STUDIO, query = "Warner Bros"),
        Row("studio_universal", "Universal", Family.STUDIO, query = "Universal Pictures"),

        Row("decade_2020s", "2020s", Family.DECADE, query = "2020s"),
        Row("decade_2010s", "2010s", Family.DECADE, query = "2010s"),
        Row("decade_2000s", "2000s", Family.DECADE, query = "2000s"),
        Row("decade_1990s", "1990s", Family.DECADE, query = "1990s"),
        Row("decade_1980s", "1980s", Family.DECADE, query = "1980s"),

        Row("kids_family", "Family Favorites", Family.KIDS, genre = "Family", kidsSafe = true),
        Row("kids_animation", "Kids Animation", Family.KIDS, genre = "Animation", kidsSafe = true),
        Row("kids_adventure", "Family Adventure", Family.KIDS, genre = "Adventure", kidsSafe = true),

        Row("uk_bbc", "BBC", Family.UK, Media.SERIES, query = "BBC"),
        Row("uk_itv", "ITV", Family.UK, Media.SERIES, query = "ITV"),
        Row("uk_comedy", "UK Comedy", Family.UK, query = "British comedy"),
        Row("uk_drama", "UK Drama", Family.UK, query = "British drama"),

        Row("trakt_trending", "Trakt Trending", Family.TRAKT, requiresTrakt = true, badge = "TRAKT"),
        Row("trakt_popular", "Trakt Popular", Family.TRAKT, requiresTrakt = true, badge = "TRAKT"),
        Row("trakt_anticipated", "Trakt Anticipated", Family.TRAKT, requiresTrakt = true, badge = "TRAKT"),
        Row("trakt_watchlist", "My Trakt Watchlist", Family.TRAKT, requiresTrakt = true, badge = "TRAKT"),
    )

    val quickPickIds = listOf(
        "trending_movies", "trending_series",
        "netflix_movies", "netflix_series",
        "prime_movies", "prime_series",
        "disney_movies", "disney_series",
        "apple_movies", "apple_series",
    )

    val presets = listOf(
        Preset(
            id = "quick_start",
            title = "Quick Start",
            description = "Trending plus the major streaming-service rows.",
            rowIds = quickPickIds,
        ),
        Preset(
            id = "everything",
            title = "Everything",
            description = "A broad AstraWave discovery setup across every catalog family.",
            rowIds = rows.map { it.id },
        ),
        Preset(
            id = "movies",
            title = "Movie Lover",
            description = "Movies, releases, studios, genres, decades and curated collections.",
            rowIds = rows.filter { it.media != Media.SERIES && it.family != Family.TRAKT }.map { it.id },
        ),
        Preset(
            id = "tv",
            title = "TV Fan",
            description = "Series, streaming networks, genres, UK rows and trending TV.",
            rowIds = rows.filter { it.media != Media.MOVIE && it.family != Family.TRAKT }.map { it.id },
        ),
        Preset(
            id = "family",
            title = "Family",
            description = "Family-safe and kids-oriented discovery rows.",
            rowIds = rows.filter { it.kidsSafe }.map { it.id },
        ),
    )

    fun row(id: String): Row? = rows.firstOrNull { it.id == id }
    fun preset(id: String): Preset? = presets.firstOrNull { it.id == id }
}
