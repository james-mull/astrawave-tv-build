package com.astrawave.app.data

import android.content.Context

data class SetupHealthItem(
    val id: String,
    val title: String,
    val detail: String,
    val ready: Boolean,
    val optional: Boolean = false,
)

data class SetupHealthSnapshot(
    val score: Int,
    val readyCount: Int,
    val requiredCount: Int,
    val items: List<SetupHealthItem>,
) {
    val readyToWatch: Boolean get() = items.filterNot { it.optional }.all { it.ready }
}

/**
 * Local, deterministic setup assessment. No network probes are performed here so onboarding stays fast.
 * Optional integrations do not reduce the core readiness score.
 */
class SetupHealthRepository(private val context: Context) {
    fun snapshot(profileId: String): SetupHealthSnapshot {
        val app = context.applicationContext
        val settings = AppSettingsStore(app)
        val sources = IptvSourceStore(app).load(profileId)
        val audio = AudioSourceStore(app).load(profileId)
        val cloud = FirebaseCloudRepository(app)
        val devices = LocalDeviceSessionGateway(app).discover()
        val dvrAuth = DvrAuthorizationStore(app)
        val travel = DownloadTravelStore(app).travelPolicy(profileId)
        val cloudStreamDefaults = CloudStreamRepositoryPreferenceStore(app).load(profileId)
        val recommendedCloudStreamReady = RecommendedSourcePack.cloudStreamRepositoryIds.all { requiredId ->
            cloudStreamDefaults.any { it.id == requiredId && it.enabled }
        }
        val recommendedStremioReady = RecommendedSourcePack.stremioManifestUrls.isNotEmpty()
        val recommendedPackReady = recommendedCloudStreamReady && recommendedStremioReady
        val recommendedPackCount = RecommendedSourcePack.stremioManifestUrls.size +
            RecommendedSourcePack.cloudStreamRepositoryIds.size +
            RecommendedSourcePack.liveSourceIds.size +
            RecommendedSourcePack.audioProviders.size +
            RecommendedSourcePack.providerCatalogs.size

        val items = listOf(
            SetupHealthItem(
                id = "profile",
                title = "Profile",
                detail = "Your household profile is ready.",
                ready = profileId.isNotBlank(),
            ),
            SetupHealthItem(
                id = "recommended-pack",
                title = "AstraWave Recommended Pack",
                detail = if (recommendedPackReady) {
                    "$recommendedPackCount reviewed/default integrations are available for zero-config discovery, Live TV, audio, subtitles and provider availability."
                } else {
                    "A reviewed default source was disabled. Restore Recommended Pack defaults from Sources or Fix Everything Safe."
                },
                ready = recommendedPackReady,
            ),
            SetupHealthItem(
                id = "discovery",
                title = "Movies & TV",
                detail = if (settings.effectiveTmdbBearerToken().isNotBlank()) "Full movie and TV discovery is configured." else "Recommended catalogs work now; add TMDB setup for richer metadata and discovery.",
                ready = true,
            ),
            SetupHealthItem(
                id = "live",
                title = "Live TV & Guide",
                detail = if (sources.any { it.enabled }) "${sources.count { it.enabled }} enabled customer source${if (sources.count { it.enabled } == 1) "" else "s"} plus AstraWave's reviewed free lineup." else "AstraWave Free TV and public guide sources are available; M3U/Xtream is optional.",
                ready = true,
                optional = true,
            ),
            SetupHealthItem(
                id = "audio",
                title = "Music & Podcasts",
                detail = if (audio.subscriptions.isNotEmpty() || audio.stations.isNotEmpty()) "${audio.subscriptions.size} podcast feeds • ${audio.stations.size} radio stations saved." else "Built-in radio, podcast and music discovery works now; saved feeds/stations are optional.",
                ready = true,
                optional = true,
            ),
            SetupHealthItem(
                id = "cloud",
                title = "Cloud Sync",
                detail = if (cloud.signedIn) "Signed in and ready for private profile sync." else "Sign in for cross-device progress, library and diagnostics sync.",
                ready = cloud.signedIn,
                optional = true,
            ),
            SetupHealthItem(
                id = "devices",
                title = "Devices & Remote",
                detail = if (devices.isNotEmpty()) "${devices.size} paired device${if (devices.size == 1) "" else "s"}." else "Pairing a phone/TV companion is optional.",
                ready = devices.isNotEmpty(),
                optional = true,
            ),
            SetupHealthItem(
                id = "dvr",
                title = "DVR",
                detail = if (sources.any { dvrAuth.allowed(profileId, it.id) }) "At least one customer source is explicitly authorized for local DVR." else "DVR stays off until you explicitly authorize a customer source.",
                ready = sources.any { dvrAuth.allowed(profileId, it.id) },
                optional = true,
            ),
            SetupHealthItem(
                id = "travel",
                title = "Offline & Travel",
                detail = if (travel.enabled) "Travel Mode enabled with a ${travel.maxStorageGb} GB cap." else "Offline downloads work when you choose eligible authorized media; Travel Mode is optional.",
                ready = travel.enabled,
                optional = true,
            ),
        )

        val required = items.filterNot { it.optional }
        val readyRequired = required.count { it.ready }
        val score = if (required.isEmpty()) 100 else ((readyRequired * 100.0) / required.size).toInt().coerceIn(0, 100)
        return SetupHealthSnapshot(score, readyRequired, required.size, items)
    }
}
