package com.astrawave.app.data

/**
 * Stable-ID service substitutions for the built-in 75/75 catalog registry.
 *
 * We intentionally keep the legacy catalog IDs so existing per-profile hidden/pinned/order settings
 * remain valid while the visible catalog identity is upgraded to higher-value streaming discovery.
 */
object CatalogServiceOverrides {
    private val titles = mapOf(
        // Movies: replace weaker/overlapping mood/theme slots.
        "movie:girls-night" to "Netflix Movies",
        "movie:guys-night" to "Prime Video Movies",
        "movie:date-night" to "Disney+ Movies",
        "movie:feel-good-movies" to "Max Movies",
        "movie:summer-blockbusters" to "Apple TV+ Movies",
        "movie:music-movies" to "Hulu Movies",
        "movie:disaster-movies" to "Peacock Movies",
        "movie:survival-stories" to "Paramount+ Movies",

        // TV: replace narrow overlapping genre/theme slots.
        "show:martial-arts-series" to "Netflix Shows",
        "show:heist-series" to "Prime Video Shows",
        "show:spy-series" to "Disney+ Shows",
        "show:survival-series" to "Max Shows",
        "show:post-apocalyptic-series" to "Apple TV+ Shows",
        "show:time-travel-series" to "Hulu Shows",
        "show:psychological-series" to "Peacock Shows",
        "show:biographical-series" to "Paramount+ Shows",
    )

    val ids: Set<String> = titles.keys

    fun title(catalogId: String, fallback: String): String = titles[catalogId] ?: fallback

    fun apply(definition: BuiltInCatalogDefinition): BuiltInCatalogDefinition =
        titles[definition.id]?.let { serviceTitle ->
            definition.copy(
                title = serviceTitle,
                category = BuiltInCatalogCategory.PREMIUM_INTERNATIONAL,
            )
        } ?: definition

    operator fun contains(catalogId: String): Boolean = catalogId in ids
}
