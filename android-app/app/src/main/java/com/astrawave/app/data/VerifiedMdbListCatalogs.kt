package com.astrawave.app.data

/**
 * Verified built-in catalog IDs with an MDBList mapping on the AstraWave server.
 * Keep this set aligned with web-app/lib/mdblist-catalog-map.ts.
 */
object VerifiedMdbListCatalogs {
    val ids = setOf(
        "movie:popular-movies",
        "movie:most-watched-movies",
        "movie:most-anticipated-movies",
        "show:popular-shows",
        "show:most-watched-shows",
        "show:most-anticipated-shows",
        "movie:trending-movies",
        "show:trending-shows",
        "movie:top-rated-movies",
        "show:top-rated-shows",
        "movie:imdb-top-movies",
        "movie:new-releases",
        "show:new-series",
        "movie:critics-favorites",
        "show:critics-favorites-tv",
        "show:limited-series",
        "movie:psychological-thrillers",
        "movie:comfort-movies",
        "movie:dolby-vision-picks",
        "movie:4k-hdr-showcase",
        "movie:audience-favorites",
        "show:prestige-drama",
        "movie:horror-hits",
        "movie:action-essentials",
        "movie:comedy-hits",
        "movie:drama-essentials",
        "movie:crime-movies",
        "movie:sci-fi-essentials",
        "movie:documentaries",
        "movie:family-night",
        "movie:animation-favorites",
        "movie:mystery-movies",
        "movie:superhero-movies",
        "movie:thriller-picks",
        "movie:sports-movies",
        "show:true-crime",
        "movie:anime-movies",
        "show:anime-series",
        "movie:international-cinema",
        "movie:japanese-movies",
        "movie:french-cinema",
        "show:k-dramas",
        "show:indian-series",
    )

    operator fun contains(catalogId: String): Boolean = catalogId in ids
}
