package com.astrawave.app.data

enum class BuiltInCatalogMediaType { MOVIE, SHOW }
enum class BuiltInCatalogCategory { DISCOVERY, EDITORIAL, GENRE_THEME, MOOD_SEASONAL, PREMIUM_INTERNATIONAL }

data class BuiltInCatalogDefinition(
    val id: String,
    val title: String,
    val mediaType: BuiltInCatalogMediaType,
    val category: BuiltInCatalogCategory,
    val documentedUrl: String? = null,
    val featured: Boolean = false,
) {
    val fallbackQuery: String get() = title
}

/**
 * 150 built-in AstraWave catalog definitions: 75 movies + 75 TV shows.
 * We hardcode catalog identity/placement, not title contents. MDBList-backed payloads can refresh.
 * documentedUrl is set only for MDBList URLs documented as stable; the rest retain metadata-search
 * fallback until a concrete MDBList mapping is configured.
 */
object AstraWaveBuiltInCatalogRegistry {
    private fun d(id: String, title: String, type: BuiltInCatalogMediaType, category: BuiltInCatalogCategory, url: String? = null, featured: Boolean = false) =
        BuiltInCatalogDefinition(id, title, type, category, url, featured)

    val movies = listOf(
        d("movie:popular-movies", "Popular Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, "https://mdblist.com/lists/official/movies/popular", true),
        d("movie:trending-movies", "Trending Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:most-watched-movies", "Most Watched Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, "https://mdblist.com/lists/official/movies/most-watched", true),
        d("movie:most-anticipated-movies", "Most Anticipated Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, "https://mdblist.com/lists/official/movies/anticipated", true),
        d("movie:top-rated-movies", "Top Rated Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:imdb-top-movies", "IMDb Top Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:trakt-trending-movies", "Trakt Trending Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:trakt-popular-movies", "Trakt Popular Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:trakt-most-watched-movies", "Trakt Most Watched Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:trakt-most-collected-movies", "Trakt Most Collected Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("movie:new-releases", "New Releases", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("movie:now-playing", "Now Playing", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("movie:coming-soon", "Coming Soon", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("movie:weekend-box-office", "Weekend Box Office", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("movie:best-of-2026", "Best of 2026", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("movie:best-of-2025", "Best of 2025", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:critics-favorites", "Critics Favorites", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:audience-favorites", "Audience Favorites", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:award-winners", "Award Winners", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:oscar-winners", "Oscar Winners", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:golden-globe-winners", "Golden Globe Winners", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:cannes-favorites", "Cannes Favorites", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:sundance-favorites", "Sundance Favorites", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:hidden-gems", "Hidden Gems", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:cult-classics", "Cult Classics", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.EDITORIAL),
        d("movie:action-essentials", "Action Essentials", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:adventure-movies", "Adventure Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:animation-favorites", "Animation Favorites", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:comedy-hits", "Comedy Hits", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:crime-movies", "Crime Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:documentaries", "Documentaries", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:drama-essentials", "Drama Essentials", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:family-night", "Family Night", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:fantasy-worlds", "Fantasy Worlds", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:horror-hits", "Horror Hits", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:mystery-movies", "Mystery Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:romance-favorites", "Romance Favorites", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:sci-fi-essentials", "Sci-Fi Essentials", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:thriller-picks", "Thriller Picks", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:war-movies", "War Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:western-classics", "Western Classics", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:superhero-movies", "Superhero Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:martial-arts", "Martial Arts", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:heist-movies", "Heist Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:spy-movies", "Spy Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:disaster-movies", "Disaster Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:survival-stories", "Survival Stories", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:post-apocalyptic", "Post-Apocalyptic", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:time-travel", "Time Travel", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:mind-benders", "Mind-Benders", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.GENRE_THEME),
        d("movie:psychological-thrillers", "Psychological Thrillers", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:true-stories", "True Stories", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:biopics", "Biopics", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:sports-movies", "Sports Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:music-movies", "Music Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:christmas-movies", "Christmas Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:halloween-movies", "Halloween Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:summer-blockbusters", "Summer Blockbusters", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:feel-good-movies", "Feel-Good Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:comfort-movies", "Comfort Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:date-night", "Date Night", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:girls-night", "Girls Night", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:guys-night", "Guys Night", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:under-90-minutes", "Under 90 Minutes", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:epic-movies", "Epic Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("movie:4k-hdr-showcase", "4K/HDR Showcase", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL, null, true),
        d("movie:dolby-vision-picks", "Dolby Vision Picks", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:atmos-showcase", "Atmos Showcase", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:international-cinema", "International Cinema", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:korean-movies", "Korean Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:japanese-movies", "Japanese Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:indian-cinema", "Indian Cinema", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:french-cinema", "French Cinema", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:spanish-language-movies", "Spanish-Language Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("movie:anime-movies", "Anime Movies", BuiltInCatalogMediaType.MOVIE, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
    )

    val shows = listOf(
        d("show:popular-shows", "Popular Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, "https://mdblist.com/lists/official/shows/popular", true),
        d("show:trending-shows", "Trending Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:most-watched-shows", "Most Watched Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, "https://mdblist.com/lists/official/shows/most-watched", true),
        d("show:most-anticipated-shows", "Most Anticipated Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, "https://mdblist.com/lists/official/shows/anticipated", true),
        d("show:top-rated-shows", "Top Rated Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:imdb-top-shows", "IMDb Top Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:trakt-trending-shows", "Trakt Trending Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:trakt-popular-shows", "Trakt Popular Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:trakt-most-watched-shows", "Trakt Most Watched Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:trakt-most-collected-shows", "Trakt Most Collected Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.DISCOVERY, null, true),
        d("show:new-series", "New Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("show:new-episodes", "New Episodes", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("show:airing-today", "Airing Today", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("show:on-the-air", "On the Air", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("show:best-of-2026-tv", "Best of 2026 TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL, null, true),
        d("show:best-of-2025-tv", "Best of 2025 TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:critics-favorites-tv", "Critics Favorites TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:audience-favorites-tv", "Audience Favorites TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:emmy-winners", "Emmy Winners", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:golden-globe-tv-winners", "Golden Globe TV Winners", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:prestige-drama", "Prestige Drama", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:binge-worthy", "Binge-Worthy", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:hidden-gem-series", "Hidden Gem Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:cult-tv", "Cult TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:limited-series", "Limited Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.EDITORIAL),
        d("show:action-series", "Action Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:adventure-series", "Adventure Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:animated-series", "Animated Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:comedy-series", "Comedy Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:crime-series", "Crime Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:documentary-series", "Documentary Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:drama-series", "Drama Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:family-series", "Family Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:fantasy-series", "Fantasy Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:horror-series", "Horror Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:mystery-series", "Mystery Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:romance-series", "Romance Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:sci-fi-series", "Sci-Fi Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:thriller-series", "Thriller Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:war-series", "War Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:western-series", "Western Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:superhero-series", "Superhero Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:martial-arts-series", "Martial Arts Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:heist-series", "Heist Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:spy-series", "Spy Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:survival-series", "Survival Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:post-apocalyptic-series", "Post-Apocalyptic Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:time-travel-series", "Time Travel Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:mind-bending-series", "Mind-Bending Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:psychological-series", "Psychological Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.GENRE_THEME),
        d("show:true-crime", "True Crime", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:true-story-series", "True Story Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:biographical-series", "Biographical Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:sports-series", "Sports Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:music-series", "Music Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:reality-favorites", "Reality Favorites", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:competition-shows", "Competition Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:game-shows", "Game Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:talk-shows", "Talk Shows", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:late-night", "Late Night", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:food-cooking", "Food & Cooking", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:home-design", "Home & Design", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:travel-series", "Travel Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:nature-series", "Nature Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:science-series", "Science Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.MOOD_SEASONAL),
        d("show:kids-tv", "Kids TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL, null, true),
        d("show:teen-series", "Teen Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:k-dramas", "K-Dramas", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:japanese-series", "Japanese Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:anime-series", "Anime Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL, null, true),
        d("show:british-tv", "British TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:nordic-noir", "Nordic Noir", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:spanish-language-tv", "Spanish-Language TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:indian-series", "Indian Series", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
        d("show:international-tv", "International TV", BuiltInCatalogMediaType.SHOW, BuiltInCatalogCategory.PREMIUM_INTERNATIONAL),
    )

    val all = movies + shows

    init {
        check(movies.size == 75)
        check(shows.size == 75)
        check(all.map { it.id }.distinct().size == 150)
    }

    fun featured(type: BuiltInCatalogMediaType, limit: Int = 12): List<BuiltInCatalogDefinition> =
        (if (type == BuiltInCatalogMediaType.MOVIE) movies else shows).filter { it.featured }.take(limit.coerceAtLeast(1))

    fun category(type: BuiltInCatalogMediaType, category: BuiltInCatalogCategory): List<BuiltInCatalogDefinition> =
        (if (type == BuiltInCatalogMediaType.MOVIE) movies else shows).filter { it.category == category }
}
