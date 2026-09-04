package com.astrawave.app.data

/**
 * Compatibility layer between the existing AstraWave catalog UI models and the
 * zero-configuration metadata gateway. This keeps UI migration incremental: Home,
 * Movies, TV, Discover and Search can switch away from user-supplied TMDB credentials
 * without rewriting their rendering components.
 */
class ZeroConfigCatalogRepository(
    private val gateway: AstraWaveMetadataGateway = AstraWaveMetadataGateway(),
) {
    fun loadTrendingMovies(): TmdbCatalogPage = TmdbCatalogPage(
        catalog = AstraWaveCatalog.TRENDING_MOVIES,
        page = 1,
        totalPages = 1,
        items = gateway.load(AstraWaveMetadataGateway.Catalog.TRENDING_MOVIES)
            .map { it.toTmdbItem("movie") },
    )

    fun loadTrendingSeries(): TmdbCatalogPage = TmdbCatalogPage(
        catalog = AstraWaveCatalog.TRENDING_TV,
        page = 1,
        totalPages = 1,
        items = gateway.load(AstraWaveMetadataGateway.Catalog.TRENDING_SERIES)
            .map { it.toTmdbItem("tv") },
    )

    fun search(query: String): List<TmdbItem> = gateway.search(query).mapNotNull { item ->
        val mediaType = when (item.type.lowercase()) {
            "movie" -> "movie"
            "series", "tv" -> "tv"
            else -> null
        } ?: return@mapNotNull null
        item.toTmdbItem(mediaType)
    }

    private fun AstraWaveMetadataGateway.Item.toTmdbItem(fallbackMediaType: String): TmdbItem {
        val numericId = id.toLongOrNull()
            ?: id.substringAfterLast(':').toLongOrNull()
            ?: stablePositiveId(id)

        val posterPath = posterUrl?.takeIf { it.startsWith("/") }
        val backdropPath = backdropUrl?.takeIf { it.startsWith("/") }
        ArtworkRegistry.register(name, posterUrl ?: backdropUrl ?: posterPath ?: backdropPath)

        return TmdbItem(
            id = numericId,
            title = name,
            overview = description.orEmpty(),
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaType = when (type.lowercase()) {
                "movie" -> "movie"
                "series", "tv" -> "tv"
                else -> fallbackMediaType
            },
        )
    }

    private fun stablePositiveId(value: String): Long {
        val hash = value.fold(1125899906842597L) { acc, char -> 31L * acc + char.code }
        return if (hash == Long.MIN_VALUE) 0L else kotlin.math.abs(hash)
    }
}
