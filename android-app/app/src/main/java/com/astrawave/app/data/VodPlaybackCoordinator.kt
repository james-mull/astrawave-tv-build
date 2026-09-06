package com.astrawave.app.data

import android.content.Context
import android.content.Intent
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.ScrapeRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Shared one-click VOD playback path for details, search, home and Astra intents.
 *
 * It searches owned personal media and eligible online sources in parallel. Strong personal-media
 * matches use AstraWave's secure personal locator, while online candidates are ranked and handed to
 * PlayerActivity with ordered failover backups. Catalog presence alone never grants playback.
 */
class VodPlaybackCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = UnifiedVodSourceRepository(appContext)
    private val personalResolver = PersonalMediaVodResolver(appContext)

    suspend fun prepare(request: VodPlaybackRequest): VodPlaybackLaunch = coroutineScope {
        val scrapeRequest = ScrapeRequest(
            title = request.title,
            year = request.year,
            season = request.season,
            episode = request.episode,
            externalIds = buildMap {
                request.stremioId?.takeIf(String::isNotBlank)?.let { put("stremio_id", it) }
                request.stremioType?.takeIf(String::isNotBlank)?.let { put("stremio_type", it) }
            },
        )
        val online = async { resolver.plan(scrapeRequest, request.profileId) }
        val personal = async { personalResolver.findBest(request) }
        VodPlaybackLaunch(
            request = request,
            plan = online.await(),
            personal = personal.await(),
        )
    }

    fun intent(launch: VodPlaybackLaunch): Intent? {
        if (!launch.playable) return null
        val request = launch.request
        launch.personal?.let { personal ->
            return Intent(appContext, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PlayerActivity.EXTRA_URL, personal.locator)
                .putExtra(PlayerActivity.EXTRA_PROFILE_ID, request.profileId)
        }

        if (!launch.plan.playable) return null
        val libraryType = when {
            request.season != null && request.episode != null -> LibraryMediaType.EPISODE
            request.mediaType.equals("series", true) || request.mediaType.equals("tv", true) -> LibraryMediaType.SERIES
            else -> LibraryMediaType.MOVIE
        }
        val playbackId = request.libraryId ?: buildString {
            append(request.mediaType.lowercase().ifBlank { "movie" })
            append(':')
            append(request.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            request.season?.let { append(":s$it") }
            request.episode?.let { append("e$it") }
        }
        return Intent(appContext, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PlayerActivity.EXTRA_URL, launch.plan.urls.first())
            .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(launch.plan.urls))
            .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true)
            .putExtra(PlayerActivity.EXTRA_PROFILE_ID, request.profileId)
            .putExtra(PlayerActivity.EXTRA_LIBRARY_ID, playbackId)
            .putExtra(PlayerActivity.EXTRA_LIBRARY_TITLE, request.playbackTitle())
            .putExtra(PlayerActivity.EXTRA_LIBRARY_TYPE, libraryType.name)
            .putExtra(PlayerActivity.EXTRA_LIBRARY_SOURCE_ID, request.sourceId)
    }

    suspend fun prepareIntent(request: VodPlaybackRequest): Pair<VodPlaybackLaunch, Intent?> {
        val launch = prepare(request)
        return launch to intent(launch)
    }
}

data class VodPlaybackRequest(
    val title: String,
    val year: Int? = null,
    val mediaType: String = "movie",
    val profileId: String = "default",
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val stremioType: String? = null,
    val stremioId: String? = null,
    val libraryId: String? = null,
    val sourceId: String? = null,
) {
    fun playbackTitle(): String = if (season != null && episode != null) {
        buildString {
            append(title)
            append(" — S")
            append(season)
            append('E')
            append(episode)
            episodeTitle?.takeIf(String::isNotBlank)?.let { append(" $it") }
        }
    } else title
}

data class VodPlaybackLaunch(
    val request: VodPlaybackRequest,
    val plan: VodPlaybackPlan,
    val personal: PersonalVodCandidate? = null,
) {
    val playable: Boolean get() = personal != null || plan.playable
    val bestSource: ResolvedSource? get() = plan.preferred
    val backupCount: Int get() = plan.backupCount
    val providerCount: Int get() = plan.providers.size + if (personal != null) 1 else 0
    val bestQuality: String? get() = plan.bestQuality
    val bestProviderLabel: String? get() = personal?.let { "${it.provider.name} • ${it.connectionName}" } ?: bestSource?.link?.sourceName
}
