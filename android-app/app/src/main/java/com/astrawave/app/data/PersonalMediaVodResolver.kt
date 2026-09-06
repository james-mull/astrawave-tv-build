package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaGateway
import com.astrawave.app.core.PersonalMediaItem
import com.astrawave.app.core.PersonalMediaProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Finds strong matches in user-owned personal media without exposing credentials to the generic
 * source resolver. Results are returned as AstraWave personal locators; PlayerActivity resolves the
 * locator using Keystore-backed credentials at playback time.
 */
class PersonalMediaVodResolver(context: Context) {
    private val appContext = context.applicationContext
    private val store = PersonalMediaStore(appContext)
    private val playback = PersonalMediaPlaybackRepository(appContext)
    private val plex by lazy { PlexPersonalMediaGateway(appContext) }
    private val embyFamily by lazy { EmbyFamilyPersonalMediaGateway(appContext) }
    private val webDav by lazy { WebDavPersonalMediaGateway(appContext) }

    suspend fun findBest(request: VodPlaybackRequest): PersonalVodCandidate? = coroutineScope {
        val connections = store.load(request.profileId).filter { it.enabled }
        if (connections.isEmpty()) return@coroutineScope null
        val query = request.episodeTitle?.takeIf(String::isNotBlank) ?: request.title
        connections.map { connection ->
            async {
                runCatching {
                    gateway(connection).search(connection, query, 24)
                        .mapNotNull { item -> score(request, connection, item) }
                        .maxByOrNull { it.score }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().maxByOrNull { it.score }?.takeIf { it.score >= MIN_STRONG_MATCH }
    }

    private fun score(
        request: VodPlaybackRequest,
        connection: PersonalMediaConnection,
        item: PersonalMediaItem,
    ): PersonalVodCandidate? {
        val wantedType = when {
            request.season != null && request.episode != null -> LibraryMediaType.EPISODE
            request.mediaType.equals("series", true) || request.mediaType.equals("tv", true) -> LibraryMediaType.SERIES
            else -> LibraryMediaType.MOVIE
        }
        if (item.type != wantedType && !(wantedType == LibraryMediaType.SERIES && item.type == LibraryMediaType.EPISODE)) return null

        val requestedTitle = normalize(request.episodeTitle?.takeIf(String::isNotBlank) ?: request.title)
        val candidateTitle = normalize(item.title)
        var score = when {
            requestedTitle == candidateTitle -> 1000
            candidateTitle.startsWith(requestedTitle) || requestedTitle.startsWith(candidateTitle) -> 760
            candidateTitle.contains(requestedTitle) || requestedTitle.contains(candidateTitle) -> 610
            else -> 0
        }
        if (score == 0) return null
        if (item.type == wantedType) score += 120
        request.year?.let { year ->
            if (item.subtitle?.contains(year.toString()) == true) score += 80
        }
        if (connection.status.name == "READY") score += 40

        return PersonalVodCandidate(
            locator = playback.locator(connection, item),
            connectionId = connection.id,
            connectionName = connection.name,
            provider = connection.provider,
            item = item,
            score = score,
        )
    }

    private fun gateway(connection: PersonalMediaConnection): PersonalMediaGateway = when (connection.provider) {
        PersonalMediaProvider.PLEX -> plex
        PersonalMediaProvider.JELLYFIN, PersonalMediaProvider.EMBY -> embyFamily
        PersonalMediaProvider.WEBDAV, PersonalMediaProvider.NAS -> webDav
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val MIN_STRONG_MATCH = 780
    }
}

data class PersonalVodCandidate(
    val locator: String,
    val connectionId: String,
    val connectionName: String,
    val provider: PersonalMediaProvider,
    val item: PersonalMediaItem,
    val score: Int,
)
