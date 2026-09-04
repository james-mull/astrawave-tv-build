package com.astrawave.app.data

/**
 * Repository/index endpoints AstraWave can use to discover Stremio-compatible
 * addons. Discovery does not imply automatic trust or playback eligibility.
 */
data class StremioRepositoryDefinition(
    val id: String,
    val name: String,
    val repositoryUrl: String,
    val official: Boolean,
    val autoDiscover: Boolean,
    val note: String,
)

object StremioRepositoryRegistry {
    val defaults: List<StremioRepositoryDefinition> = listOf(
        StremioRepositoryDefinition(
            id = "stremio-official",
            name = "Stremio Official Addon Repository",
            repositoryUrl = "https://api9.strem.io/addonsrepo.json",
            official = true,
            autoDiscover = true,
            note = "Official Stremio addon repository. Official manifests may be surfaced automatically when compatible.",
        ),
        StremioRepositoryDefinition(
            id = "stremio-community",
            name = "Stremio Community Addons",
            repositoryUrl = "https://addons.strem.io/community",
            official = false,
            autoDiscover = false,
            note = "Community discovery entry point. Community addons remain user opt-in and are health/source checked before playback.",
        ),
    )

    /** High-value official addons seeded automatically by StremioAddonStore. */
    val recommendedOfficialManifestUrls: List<String> = listOf(
        "https://v3-cinemeta.strem.io/manifest.json",
        "https://v3-channels.strem.io/manifest.json",
        "https://watchhub.strem.io/manifest.json",
        "https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json",
        "https://opensubtitles-v3.strem.io/manifest.json",
    )
}
