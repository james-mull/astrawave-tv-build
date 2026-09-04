package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.PersonalMediaGateway
import com.astrawave.app.core.PersonalMediaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

/**
 * AstraWave-wide search aggregator. Network-heavy sources are queried in parallel and the live
 * lineup is cached so typing into Search never reloads every playlist for each keystroke.
 */
class UnifiedSearchRepository(context: Context) {
    enum class Kind { MOVIE, TV, LIVE, SPORTS, RADIO, MUSIC, PODCAST, PERSONAL, LIST, LIBRARY }

    data class Result(
        val kind: Kind,
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val description: String? = null,
        val artworkUrl: String? = null,
        val sourceId: String? = null,
        val streamUrls: List<String> = emptyList(),
        val listQuery: String? = null,
        val listQueries: List<String> = emptyList(),
        val listGenre: String? = null,
        val listMediaType: LibraryMediaType? = null,
        val score: Int = 0,
    )

    private data class LiveCache(val createdAt: Long, val groups: List<LiveChannelGroup>)

    private val appContext = context.applicationContext
    private val metadata = AstraWaveMetadataGateway()
    private val audio = AstraWaveAudioDiscoveryRepository()
    private val personal = PersonalMediaStore(appContext)
    private val iptv = IptvSourceStore(appContext)
    private val live = CombinedLiveTvRepository()
    private val sports = TheSportsDbClient()
    private val library = LocalLibraryStore(appContext)
    private val plexGateway = PlexPersonalMediaGateway(appContext)
    private val embyFamilyGateway = EmbyFamilyPersonalMediaGateway(appContext)
    private val webDavGateway = WebDavPersonalMediaGateway(appContext)
    private val personalPlayback = PersonalMediaPlaybackRepository(appContext)
    private val collections = AstraWaveCollectionSearchIndex.entries

    suspend fun search(query: String, profileId: String, restrictedToKids: Boolean = false): List<Result> = coroutineScope {
        val q = query.trim()
        if (q.isBlank()) return@coroutineScope emptyList()

        val metadataTask = async(Dispatchers.IO) { runCatching { searchMetadata(q) }.getOrDefault(emptyList()) }
        val libraryTask = async(Dispatchers.IO) { searchLibrary(q, profileId) }
        val listTask = async(Dispatchers.Default) { searchLists(q) }

        val liveTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchLive(q, profileId) }.getOrDefault(emptyList()) }
        val sportsTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchSports(q, profileId) }.getOrDefault(emptyList()) }
        val audioTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchAudio(q) }.getOrDefault(emptyList()) }
        val personalTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchPersonal(q, profileId) }.getOrDefault(emptyList()) }

        buildList {
            addAll(metadataTask.await())
            addAll(libraryTask.await())
            addAll(listTask.await())
            liveTask?.let { addAll(it.await()) }
            sportsTask?.let { addAll(it.await()) }
            audioTask?.let { addAll(it.await()) }
            personalTask?.let { addAll(it.await()) }
        }
            .distinctBy { "${it.kind}:${it.id}" }
            .sortedWith(compareByDescending<Result> { it.score }.thenBy { it.title.lowercase() })
            .take(160)
    }

    fun suggestions(query: String, profileId: String): List<String> {
        val q = normalize(query)
        val recent = SearchHistoryStore(appContext).recent(profileId)
        val candidates = (recent + collections.map { it.title } + QUICK_SUGGESTIONS).distinct()
        if (q.isBlank()) return candidates.take(10)
        return candidates.map { it to fuzzyScore(q, normalize(it)) }
            .filter { it.second >= 30 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(8)
    }

    fun rememberSearch(profileId: String, query: String) = SearchHistoryStore(appContext).remember(profileId, query)
    fun recentSearches(profileId: String): List<String> = SearchHistoryStore(appContext).recent(profileId)
    fun clearRecent(profileId: String) = SearchHistoryStore(appContext).clear(profileId)

    private fun searchMetadata(query: String): List<Result> = metadata.search(query).mapNotNull { item ->
        val kind = when {
            item.type.equals("movie", true) -> Kind.MOVIE
            item.type.equals("series", true) || item.type.equals("tv", true) -> Kind.TV
            else -> return@mapNotNull null
        }
        Result(
            kind = kind,
            id = item.id,
            title = item.name,
            subtitle = item.releaseInfo,
            description = item.description,
            artworkUrl = item.posterUrl ?: item.backdropUrl,
            sourceId = when {
                item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (kind == Kind.TV) "series" else "movie"}:${item.id}"
                item.id.toLongOrNull() != null -> "tmdb:${item.id}"
                else -> null
            },
            score = 100 + fuzzyScore(normalize(query), normalize(item.name)),
        )
    }

    private fun searchLibrary(query: String, profileId: String): List<Result> {
        val q = normalize(query)
        val items = (library.history(profileId).map { it.item } +
            library.watchlist(profileId).map { it.item } +
            library.favorites(profileId).map { it.item }).distinctBy { it.id }
        return items.mapNotNull { item ->
            val score = fuzzyScore(q, normalize(item.title))
            if (score < 35) return@mapNotNull null
            val kind = when (item.type) {
                LibraryMediaType.MOVIE -> Kind.MOVIE
                LibraryMediaType.SERIES, LibraryMediaType.EPISODE -> Kind.TV
                else -> Kind.LIBRARY
            }
            Result(
                kind = kind,
                id = "library:${item.id}",
                title = item.title,
                subtitle = "From your library",
                artworkUrl = item.posterUrl,
                sourceId = item.sourceId,
                score = 70 + score,
            )
        }
    }

    private fun searchLive(query: String, profileId: String): List<Result> {
        val q = normalize(query)
        val groups = liveGroups(profileId)
        return groups.mapNotNull { group ->
            val searchable = listOfNotNull(
                group.displayName,
                group.currentProgram?.title,
                group.nextProgram?.title,
                group.bestCandidate?.group,
            ).joinToString(" ")
            val score = fuzzyScore(q, normalize(searchable))
            if (score < 35) return@mapNotNull null
            val candidates = group.candidates.sortedBy { it.priority }
            Result(
                kind = Kind.LIVE,
                id = group.canonicalName,
                title = group.displayName,
                subtitle = group.currentProgram?.title?.let { "On now • $it" } ?: candidates.firstOrNull()?.group,
                description = candidates.firstOrNull()?.source,
                artworkUrl = candidates.firstOrNull()?.logo,
                streamUrls = candidates.map { it.url }.filter(String::isNotBlank).distinct(),
                score = 85 + score,
            )
        }.sortedByDescending { it.score }.take(30)
    }

    private fun searchSports(query: String, profileId: String): List<Result> {
        val q = normalize(query)
        val date = LocalDate.now(ZoneOffset.UTC).toString()
        val groups = liveGroups(profileId)
        return sports.eventsForDay(date).mapNotNull { event ->
            val searchable = listOfNotNull(event.name, event.homeTeam, event.awayTeam, event.league, event.sport, event.network).joinToString(" ")
            val score = fuzzyScore(q, normalize(searchable))
            if (score < 35) return@mapNotNull null
            val network = event.network.orEmpty()
            val matched = if (network.isBlank()) null else groups.maxByOrNull { fuzzyScore(normalize(network), normalize(it.displayName)) }
            val matchScore = matched?.let { fuzzyScore(normalize(network), normalize(it.displayName)) } ?: 0
            val urls = if (matchScore >= 45) matched?.candidates?.sortedBy { it.priority }?.map { it.url }.orEmpty() else emptyList()
            Result(
                kind = Kind.SPORTS,
                id = event.id,
                title = event.name,
                subtitle = listOfNotNull(event.league, event.time, event.status).joinToString(" • ").ifBlank { null },
                description = event.network?.let { "TV: $it" },
                streamUrls = urls.filter(String::isNotBlank).distinct(),
                score = 80 + score,
            )
        }.sortedByDescending { it.score }.take(20)
    }

    private fun searchAudio(query: String): List<Result> {
        val normalizedQuery = normalize(query)
        val radio = runCatching { audio.searchRadio(query, 16) }.getOrDefault(emptyList()).map { station ->
            Result(
                Kind.RADIO,
                station.id,
                station.name,
                listOfNotNull(station.genre, station.country).joinToString(" • ").ifBlank { null },
                artworkUrl = station.logoUrl,
                streamUrls = listOf(station.streamUrl),
                score = 75 + fuzzyScore(normalizedQuery, normalize(station.name)),
            )
        }
        val music = runCatching { audio.searchMusic(query, 18) }.getOrDefault(emptyList()).map { item ->
            Result(
                Kind.MUSIC,
                item.id,
                item.title,
                item.subtitle,
                artworkUrl = item.artworkUrl,
                streamUrls = listOfNotNull(item.mediaUrl),
                score = 75 + fuzzyScore(normalizedQuery, normalize(item.title)),
            )
        }
        val podcasts = runCatching { audio.searchPodcasts(query, 16) }.getOrDefault(emptyList()).map { pod ->
            Result(
                Kind.PODCAST,
                pod.id,
                pod.title,
                "Podcast",
                artworkUrl = pod.artworkUrl,
                sourceId = pod.feedUrl,
                score = 72 + fuzzyScore(normalizedQuery, normalize(pod.title)),
            )
        }
        return (radio + music + podcasts).sortedByDescending { it.score }.take(36)
    }

    private suspend fun searchPersonal(query: String, profileId: String): List<Result> = coroutineScope {
        val q = normalize(query)
        val connections = personal.load(profileId).filter { it.enabled }
        if (connections.isEmpty()) return@coroutineScope emptyList()

        connections.map { connection ->
            async(Dispatchers.IO) {
                val connectionLabel = connection.provider.name.replace('_', ' ')
                val gateway: PersonalMediaGateway = when (connection.provider) {
                    PersonalMediaProvider.PLEX -> plexGateway
                    PersonalMediaProvider.JELLYFIN, PersonalMediaProvider.EMBY -> embyFamilyGateway
                    PersonalMediaProvider.WEBDAV, PersonalMediaProvider.NAS -> webDavGateway
                }

                val items = runCatching { gateway.search(connection, query, 40) }.getOrDefault(emptyList())
                val itemResults = items.mapNotNull { item ->
                    val searchable = listOfNotNull(item.title, item.subtitle, item.externalId).joinToString(" ")
                    val score = fuzzyScore(q, normalize(searchable))
                    if (score < 25) return@mapNotNull null
                    Result(
                        kind = Kind.PERSONAL,
                        id = "personal:${connection.id}:${item.id}",
                        title = item.title,
                        subtitle = listOfNotNull(item.subtitle, connection.name).joinToString(" • ").ifBlank { connection.name },
                        description = "$connectionLabel personal library",
                        artworkUrl = item.posterUrl ?: item.backdropUrl,
                        sourceId = item.externalId,
                        streamUrls = listOf(personalPlayback.locator(connection, item)),
                        score = 78 + score,
                    )
                }

                val connectionScore = fuzzyScore(q, normalize("${connection.name} $connectionLabel"))
                val connectionResult = if (connectionScore >= 45) {
                    listOf(
                        Result(
                            kind = Kind.PERSONAL,
                            id = "personal-connection:${connection.id}",
                            title = connection.name,
                            subtitle = "$connectionLabel • ${connection.itemCount} items",
                            description = "Personal media server",
                            score = 55 + connectionScore,
                        ),
                    )
                } else emptyList()

                itemResults + connectionResult
            }
        }.awaitAll().flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.score }
            .take(50)
    }

    private fun searchLists(query: String): List<Result> {
        val q = normalize(query)
        return collections.mapNotNull { entry ->
            val searchable = buildString {
                append(entry.title)
                append(' ')
                append(entry.subtitle)
                entry.queries.forEach { append(' '); append(it) }
            }
            val score = maxOf(
                fuzzyScore(q, normalize(entry.title)),
                fuzzyScore(q, normalize(searchable)),
            )
            if (score < 35) return@mapNotNull null
            Result(
                kind = Kind.LIST,
                id = "list:${entry.mediaType}:${entry.id}",
                title = entry.title,
                subtitle = entry.subtitle,
                listQuery = entry.queries.firstOrNull(),
                listQueries = entry.queries,
                listMediaType = entry.mediaType,
                score = 65 + score,
            )
        }.sortedByDescending { it.score }.take(40)
    }

    private fun liveGroups(profileId: String): List<LiveChannelGroup> {
        val now = System.currentTimeMillis()
        synchronized(LIVE_CACHE) {
            LIVE_CACHE[profileId]?.takeIf { now - it.createdAt <= LIVE_CACHE_MS }?.let { return it.groups }
        }
        val groups = runCatching { live.load(iptv.load(profileId)).groups }.getOrDefault(emptyList())
        synchronized(LIVE_CACHE) { LIVE_CACHE[profileId] = LiveCache(now, groups) }
        return groups
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun fuzzyScore(query: String, value: String): Int {
        if (query.isBlank() || value.isBlank()) return 0
        if (value == query) return 100
        if (value.startsWith(query)) return 92
        if (value.contains(query)) return 82
        val qTokens = query.split(' ').filter(String::isNotBlank)
        val vTokens = value.split(' ').filter(String::isNotBlank)
        val tokenHits = qTokens.count { q -> vTokens.any { v -> v.startsWith(q) || editDistance(q, v) <= typoBudget(q) } }
        if (tokenHits == qTokens.size && tokenHits > 0) return 68
        if (tokenHits > 0) return 42 + (20 * tokenHits / qTokens.size.coerceAtLeast(1))
        val compactQ = query.replace(" ", "")
        val compactV = value.replace(" ", "")
        return if (editDistance(compactQ, compactV.take(compactQ.length + 2)) <= typoBudget(compactQ)) 45 else 0
    }

    private fun typoBudget(value: String): Int = when {
        value.length >= 9 -> 2
        value.length >= 5 -> 1
        else -> 0
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    companion object {
        private const val LIVE_CACHE_MS = 10 * 60 * 1000L
        private val LIVE_CACHE = mutableMapOf<String, LiveCache>()
        private val QUICK_SUGGESTIONS = listOf(
            "Action", "Comedy", "Sci-Fi", "Crime", "Family", "Marvel", "Star Wars",
            "Breaking Bad", "The Bear", "NBA", "NFL", "news", "rock", "true crime",
        )
    }
}

private class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_search_history_v1", Context.MODE_PRIVATE)

    fun recent(profileId: String): List<String> = prefs.getString("recent:$profileId", "").orEmpty()
        .split('\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .take(10)

    fun remember(profileId: String, query: String) {
        val value = query.trim()
        if (value.isBlank()) return
        val next = (listOf(value) + recent(profileId).filterNot { it.equals(value, true) }).take(10)
        prefs.edit().putString("recent:$profileId", next.joinToString("\n")).apply()
    }

    fun clear(profileId: String) {
        prefs.edit().remove("recent:$profileId").apply()
    }
}
