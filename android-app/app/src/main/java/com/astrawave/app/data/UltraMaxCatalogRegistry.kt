package com.astrawave.app.data

/**
 * Native AstraWave catalog blueprint inspired by the public Ultra MAX catalog addon.
 * Playback remains in AstraWave's reviewed/customer-authorized source pipeline.
 */
object UltraMaxCatalogRegistry {
    enum class Media { MOVIE, SERIES, BOTH }
    enum class Family { TRENDING, NEW_LATEST, STREAMING_SERVICE, GENRE, CURATED, STUDIO, DECADE, KIDS, UK, TRAKT }

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

    data class Preset(val id: String, val title: String, val description: String, val rowIds: List<String>)

    val languages = listOf("English", "Spanish", "French", "German", "Italian", "Portuguese", "Dutch", "Polish", "Turkish")

    val rows = listOf(
        Row("trending_movies", "Top Movies This Week", Family.TRENDING, Media.MOVIE, badge = "HOT"),
        Row("trending_series", "Top Series This Week", Family.TRENDING, Media.SERIES, badge = "HOT"),
        Row("popular_movies", "Popular Movies", Family.TRENDING, Media.MOVIE),
        Row("popular_series", "Popular Series", Family.TRENDING, Media.SERIES),
        Row("top_rated_movies", "Top Rated Movies", Family.TRENDING, Media.MOVIE),
        Row("top_rated_series", "Top Rated Series", Family.TRENDING, Media.SERIES),
        Row("right_now_movies", "Right Now Movies", Family.TRENDING, Media.MOVIE, query = "movies trending right now", badge = "NOW"),
        Row("right_now_series", "Right Now Series", Family.TRENDING, Media.SERIES, query = "series trending right now", badge = "NOW"),
        Row("anime_movies", "Anime Movies", Family.TRENDING, Media.MOVIE, genre = "Animation"),
        Row("anime_series", "Anime Series", Family.TRENDING, Media.SERIES, query = "anime"),
        Row("bollywood_movies", "Bollywood Movies", Family.TRENDING, Media.MOVIE, query = "Bollywood"),
        Row("bollywood_series", "Bollywood Series", Family.TRENDING, Media.SERIES, query = "Hindi series"),

        Row("new_movies", "New & Latest Movies", Family.NEW_LATEST, Media.MOVIE, query = "new release movie", badge = "NEW"),
        Row("new_series", "New & Latest TV", Family.NEW_LATEST, Media.SERIES, query = "new series", badge = "NEW"),
        Row("digital_releases", "New Digital Releases", Family.NEW_LATEST, Media.MOVIE, query = "digital release", badge = "NEW"),
        Row("bluray_releases", "New Blu-ray Releases", Family.NEW_LATEST, Media.MOVIE, query = "blu ray release"),
        Row("now_playing_movies", "Now Playing", Family.NEW_LATEST, Media.MOVIE, query = "now playing movies"),
        Row("airing_today_series", "Airing Today", Family.NEW_LATEST, Media.SERIES, query = "airing today tv"),
        Row("on_the_air_series", "On The Air", Family.NEW_LATEST, Media.SERIES, query = "tv on the air"),

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
        Row("hulu_movies", "Hulu Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Hulu"),
        Row("hulu_series", "Hulu Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Hulu"),
        Row("paramount_movies", "Paramount+ Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Paramount Plus"),
        Row("paramount_series", "Paramount+ Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Paramount Plus"),
        Row("peacock_movies", "Peacock Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Peacock"),
        Row("peacock_series", "Peacock Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Peacock"),
        Row("mgm_movies", "MGM+ Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "MGM Plus"),
        Row("starz_series", "Starz Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Starz"),
        Row("acorn_movies", "Acorn TV Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Acorn TV"),
        Row("acorn_series", "Acorn TV Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Acorn TV"),
        Row("shudder_movies", "Shudder Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Shudder"),
        Row("shudder_series", "Shudder Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Shudder"),
        Row("britbox_movies", "BritBox Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "BritBox"),
        Row("britbox_series", "BritBox Series", Family.STREAMING_SERVICE, Media.SERIES, query = "BritBox"),
        Row("itvx_movies", "ITVX Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "ITV"),
        Row("itvx_series", "ITVX Series", Family.STREAMING_SERVICE, Media.SERIES, query = "ITV"),
        Row("channel4_movies", "Channel 4 Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Channel 4"),
        Row("channel4_series", "Channel 4 Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Channel 4"),
        Row("crunchyroll_movies", "Crunchyroll Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Crunchyroll anime"),
        Row("crunchyroll_series", "Crunchyroll Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Crunchyroll anime"),
        Row("hidive_movies", "HIDIVE Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "HIDIVE anime"),
        Row("hidive_series", "HIDIVE Series", Family.STREAMING_SERVICE, Media.SERIES, query = "HIDIVE anime"),
        Row("discovery_movies", "Discovery+ Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "Discovery Plus"),
        Row("discovery_series", "Discovery+ Series", Family.STREAMING_SERVICE, Media.SERIES, query = "Discovery Plus"),
        Row("natgeo_movies", "National Geographic Movies", Family.STREAMING_SERVICE, Media.MOVIE, query = "National Geographic"),
        Row("natgeo_series", "National Geographic Series", Family.STREAMING_SERVICE, Media.SERIES, query = "National Geographic"),
        Row("ae_series", "A&E Series", Family.STREAMING_SERVICE, Media.SERIES, query = "A&E"),
        Row("animal_planet_series", "Animal Planet", Family.STREAMING_SERVICE, Media.SERIES, query = "Animal Planet"),

        Row("genre_horror", "Horror", Family.GENRE, genre = "Horror"),
        Row("genre_action", "Action", Family.GENRE, genre = "Action"),
        Row("genre_comedy", "Comedy", Family.GENRE, genre = "Comedy"),
        Row("genre_scifi", "Sci-Fi", Family.GENRE, genre = "Sci-Fi"),
        Row("genre_mystery", "Mystery", Family.GENRE, genre = "Mystery"),
        Row("genre_documentary", "Documentary", Family.GENRE, genre = "Documentary"),
        Row("genre_romance", "Romance", Family.GENRE, genre = "Romance"),
        Row("genre_thriller", "Thriller", Family.GENRE, genre = "Thriller"),
        Row("genre_crime", "Crime", Family.GENRE, genre = "Crime"),
        Row("genre_animation", "Animation", Family.GENRE, genre = "Animation", kidsSafe = true),
        Row("genre_family", "Family", Family.GENRE, genre = "Family", kidsSafe = true),
        Row("genre_fantasy", "Fantasy", Family.GENRE, genre = "Fantasy"),
        Row("genre_drama", "Drama", Family.GENRE, genre = "Drama"),

        Row("curated_mindfuck", "Mind-Bending Movies", Family.CURATED, Media.MOVIE, query = "mind bending movies"),
        Row("curated_twists", "Plot Twists", Family.CURATED, Media.MOVIE, query = "plot twist movies"),
        Row("curated_superhero", "Superhero", Family.CURATED, query = "superhero"),
        Row("curated_heists", "Heists", Family.CURATED, query = "heist"),
        Row("curated_serial_killer", "Serial Killer", Family.CURATED, query = "serial killer"),
        Row("curated_time_loop", "Time Loop", Family.CURATED, query = "time loop"),
        Row("curated_mafia", "Mafia", Family.CURATED, query = "mafia"),
        Row("curated_detective", "Detective", Family.CURATED, query = "detective"),
        Row("curated_prison", "Prison", Family.CURATED, query = "prison"),
        Row("curated_zombies", "Zombies", Family.CURATED, query = "zombie"),
        Row("curated_vampires", "Vampires", Family.CURATED, query = "vampire"),
        Row("curated_werewolves", "Werewolves", Family.CURATED, query = "werewolf"),
        Row("curated_paranormal", "Paranormal", Family.CURATED, query = "paranormal"),
        Row("curated_found_footage", "Found Footage", Family.CURATED, query = "found footage"),
        Row("curated_psychological_horror", "Psychological Horror", Family.CURATED, query = "psychological horror"),
        Row("curated_horror_comedy", "Horror Comedy", Family.CURATED, query = "horror comedy"),
        Row("curated_folk_horror", "Folk Horror", Family.CURATED, query = "folk horror"),
        Row("curated_cosmic_horror", "Cosmic Horror", Family.CURATED, query = "cosmic horror"),
        Row("curated_time_travel", "Time Travel", Family.CURATED, query = "time travel"),

        Row("studio_marvel", "Marvel", Family.STUDIO, query = "Marvel"),
        Row("studio_dc", "DC", Family.STUDIO, query = "DC Comics"),
        Row("studio_a24", "A24", Family.STUDIO, Media.MOVIE, query = "A24"),
        Row("studio_blumhouse", "Blumhouse", Family.STUDIO, Media.MOVIE, query = "Blumhouse"),
        Row("studio_ghibli", "Studio Ghibli", Family.STUDIO, Media.MOVIE, query = "Studio Ghibli", kidsSafe = true),
        Row("studio_warner", "Warner Bros.", Family.STUDIO, query = "Warner Bros"),
        Row("studio_universal", "Universal", Family.STUDIO, query = "Universal Pictures"),
        Row("studio_sony", "Sony Pictures", Family.STUDIO, query = "Sony Pictures"),
        Row("studio_paramount", "Paramount Pictures", Family.STUDIO, query = "Paramount Pictures"),
        Row("studio_20th_century", "20th Century", Family.STUDIO, query = "20th Century Studios"),
        Row("studio_lionsgate", "Lionsgate", Family.STUDIO, query = "Lionsgate"),
        Row("studio_new_line", "New Line Cinema", Family.STUDIO, query = "New Line Cinema"),
        Row("network_abc", "ABC Series", Family.STUDIO, Media.SERIES, query = "ABC TV series"),
        Row("network_cbs", "CBS Series", Family.STUDIO, Media.SERIES, query = "CBS TV series"),
        Row("network_fox", "FOX Series", Family.STUDIO, Media.SERIES, query = "FOX TV series"),
        Row("network_nbc", "NBC Series", Family.STUDIO, Media.SERIES, query = "NBC TV series"),

        Row("decade_2020s", "2020s", Family.DECADE, query = "2020s"),
        Row("decade_2010s", "2010s", Family.DECADE, query = "2010s"),
        Row("decade_2000s", "2000s", Family.DECADE, query = "2000s"),
        Row("decade_1990s", "1990s", Family.DECADE, query = "1990s"),
        Row("decade_1980s", "1980s", Family.DECADE, query = "1980s"),
        Row("decade_1970s", "1970s", Family.DECADE, query = "1970s"),

        Row("kids_family", "Family Favorites", Family.KIDS, genre = "Family", kidsSafe = true),
        Row("kids_animation", "Kids Animation", Family.KIDS, genre = "Animation", kidsSafe = true),
        Row("kids_adventure", "Family Adventure", Family.KIDS, genre = "Adventure", kidsSafe = true),
        Row("kids_disney", "Disney Family", Family.KIDS, query = "Disney family", kidsSafe = true),
        Row("kids_pixar", "Pixar", Family.KIDS, query = "Pixar", kidsSafe = true),

        Row("uk_bbc", "BBC", Family.UK, Media.SERIES, query = "BBC"),
        Row("uk_itv", "ITV", Family.UK, Media.SERIES, query = "ITV"),
        Row("uk_channel4", "Channel 4 UK", Family.UK, Media.SERIES, query = "Channel 4 UK"),
        Row("uk_comedy", "UK Comedy", Family.UK, query = "British comedy"),
        Row("uk_drama", "UK Drama", Family.UK, query = "British drama"),
        Row("uk_crime", "UK Crime", Family.UK, query = "British crime series"),

        Row("trakt_trending", "Trakt Trending", Family.TRAKT, badge = "TRAKT"),
        Row("trakt_popular", "Trakt Popular", Family.TRAKT, badge = "TRAKT"),
        Row("trakt_anticipated", "Trakt Anticipated", Family.TRAKT, badge = "TRAKT"),
        Row("trakt_watchlist", "My Trakt Watchlist", Family.TRAKT, requiresTrakt = true, badge = "TRAKT"),
    )

    val quickPickIds = listOf(
        "trending_movies", "trending_series", "right_now_movies", "right_now_series",
        "netflix_movies", "netflix_series", "prime_movies", "prime_series",
        "disney_movies", "disney_series", "apple_movies", "apple_series",
    )

    val presets = listOf(
        Preset("quick_start", "Quick Start", "Trending plus the major streaming-service rows.", quickPickIds),
        Preset("everything", "Everything", "A broad AstraWave discovery setup across every catalog family.", rows.map { it.id }),
        Preset("movies", "Movie Lover", "Movies, releases, studios, genres, decades and curated collections.", rows.filter { it.media != Media.SERIES && it.family != Family.TRAKT }.map { it.id }),
        Preset("tv", "TV Fan", "Series, streaming networks, genres, UK rows and trending TV.", rows.filter { it.media != Media.MOVIE && it.family != Family.TRAKT }.map { it.id }),
        Preset("family", "Family", "Family-safe and kids-oriented discovery rows.", rows.filter { it.kidsSafe }.map { it.id }),
    )

    fun row(id: String): Row? = rows.firstOrNull { it.id == id }
    fun preset(id: String): Preset? = presets.firstOrNull { it.id == id }
}
