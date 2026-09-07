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

        val items = listOf(
            SetupHealthItem(
                id = "profile",
                title = "Profile",
                detail = "Your household profile is ready.",
                ready = profileId.isNotBlank(),
            ),
            SetupHealthItem(
                id = "discovery",
                title = "Movies & TV",
                detail = if (settings.effectiveTmdbBearerToken().isNotBlank()) "Full movie and TV discovery is configured." else "Add TMDB setup for full metadata and discovery.",
                ready = settings.effectiveTmdbBearerToken().isNotBlank(),
            ),
            SetupHealthItem(
                id = "live",
                title = "Live TV & Guide",
                detail = if (sources.any { it.enabled }) "${sources.count { it.enabled }} enabled customer source${if (sources.count { it.enabled } == 1) "" else "s"}." else "AstraWave Free TV remains available; add M3U/Xtream only if you use one.",
                ready = true,
                optional = true,
            ),
            SetupHealthItem(
                id = "audio",
                title = "Music & Podcasts",
                detail = if (audio.subscriptions.isNotEmpty() || audio.stations.isNotEmpty()) "${audio.subscriptions.size} podcast feeds • ${audio.stations.size} radio stations saved." else "Built-in audio discovery works now; personal feeds/stations are optional.",
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
