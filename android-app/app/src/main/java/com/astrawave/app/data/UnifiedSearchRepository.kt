package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.LibraryMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    suspend fun search(query: String, profileId: String, restrictedToKids: Boolean = false): List<Result> = coroutineScope {
        val q = query.trim()
        if (q.isBlank()) return@coroutineScope emptyList()

        val metadataTask = async(Dispatchers.IO) { runCatching { searchMetadata(q) }.getOrDefault(emptyList()) }
        val libraryTask = async(Dispatchers.IO) { searchLibrary(q, profileId) }
        val listTask = async(Dispatchers.IO) { searchLists(q) }

        val liveTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchLive(q, profileId) }.getOrDefault(emptyList()) }
        val sportsTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchSports(q, profileId) }.getOrDefault(emptyList()) }
        val audioTask = if (restrictedToKids) null else async(Dispatchers.IO) { runCatching { searchAudio(q) }.getOrDefault(emptyList()) }
        val personalTask = if (restrictedToKids) null else async(Dispatchers.IO) { searchPersonal(q, profileId) }

        val all = buildList {
            addAll(metadataTask.await())
            addAll(libraryTask.await())
            addAll(listTask.await())
            liveTask?.let { addAll(it.await()) }
            sportsTask?.let { addAll(it.await()) }
            audioTask?.let { addAll(it.await()) }
            personalTask?.let { addAll(it.await()) }
        }
        all.distinctBy { "${it.kind}:${it.id}" }
            .sortedWith(compareByDescending<Result> { it.score }.thenBy { it.title.lowercase() })
            .take(120)
    }

    fun suggestions(query: String, profileId: String): List<String> {
        val q = normalize(query)
        val recent = SearchHistoryStore(appContext).recent(profileId)
        val candidates = (recent + LIST_INDEX.map { it.title } + QUICK_SUGGESTIONS).distinct()
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
            Result(kind, "library:${item.id}", item.title, "From your library", artworkUrl = item.posterUrl, sourceId = item.sourceId, score = 70 + score)
        }
    }

    private fun searchLive(query: String, profileId: String): List<Result> {
        val q = normalize(query)
        val groups = liveGroups(profileId)
        return groups.mapNotNull { group ->
            val searchable = listOfNotNull(group.displayName, group.currentProgram?.title, group.nextProgram?.title, group.bestCandidate?.group).joinToString(" ")
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
        val radio = runCatching { audio.searchRadio(query, 16) }.getOrDefault(emptyList()).map { station ->
            Result(Kind.RADIO, station.id, station.name, listOfNotNull(station.genre, station.country).joinToString(" • ").ifBlank { null }, artworkUrl = station.logoUrl, streamUrls = listOf(station.streamUrl), score = 75 + fuzzyScore(normalize(query), normalize(station.name)))
        }
        val music = runCatching { audio.searchMusic(query, 18) }.getOrDefault(emptyList()).map { item ->
            Result(Kind.MUSIC, item.id, item.title, item.subtitle, artworkUrl = item.artworkUrl, streamUrls = listOfNotNull(item.mediaUrl), score = 75 + fuzzyScore(normalize(query), normalize(item.title)))
        }
        val podcasts = runCatching { audio.searchPodcasts(query, 16) }.getOrDefault(emptyList()).map { pod ->
            Result(Kind.PODCAST, pod.id, pod.title, "Podcast", artworkUrl = pod.artworkUrl, sourceId = pod.feedUrl, score = 72 + fuzzyScore(normalize(query), normalize(pod.title)))
        }
        return (radio + music + podcasts).sortedByDescending { it.score }.take(36)
    }

    private fun searchPersonal(query: String, profileId: String): List<Result> {
        val q = normalize(query)
        return personal.load(profileId).mapNotNull { connection ->
            val score = fuzzyScore(q, normalize("${connection.name} ${connection.provider.name}"))
            if (score < 35) return@mapNotNull null
            Result(
                Kind.PERSONAL,
                connection.id,
                connection.name,
                "${connection.provider.name.replace('_', ' ')} • ${connection.itemCount} items",
                description = if (connection.enabled) "Personal media connection" else "Connection disabled",
                score = 60 + score,
            )
        }
    }

    private fun searchLists(query: String): List<Result> {
        val q = normalize(query)
        return LIST_INDEX.mapNotNull { entry ->
            val score = maxOf(fuzzyScore(q, normalize(entry.title)), fuzzyScore(q, normalize("${entry.title} ${entry.subtitle}")))
            if (score < 35) return@mapNotNull null
            Result(
                Kind.LIST,
                "list:${entry.mediaType}:${entry.title}",
                entry.title,
                entry.subtitle,
                listQuery = entry.query,
                listGenre = entry.genre,
                listMediaType = entry.mediaType,
                score = 65 + score,
            )
        }.sortedByDescending { it.score }.take(24)
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
                current[j + 1] = min(min(current[j] + 1, previous[j + 1] + 1), previous[j] + if (a[i] == b[j]) 0 else 1)
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[b.length]
    }

    private data class ListEntry(
        val title: String,
        val subtitle: String,
        val mediaType: LibraryMediaType,
        val query: String? = null,
        val genre: String? = null,
    )

    companion object {
        private const val LIVE_CACHE_MS = 10 * 60 * 1000L
        private val LIVE_CACHE = mutableMapOf<String, LiveCache>()
        private val QUICK_SUGGESTIONS = listOf("Action", "Comedy", "Sci-Fi", "Crime", "Family", "Marvel", "Star Wars", "Breaking Bad", "The Bear", "NBA", "NFL", "news", "rock", "true crime")
        private val LIST_INDEX = listOf(
            ListEntry("Weekly Hot List", "What everyone is watching", LibraryMediaType.MOVIE, query = "popular movies"),
            ListEntry("Top 100", "AstraWave movie essentials", LibraryMediaType.MOVIE, query = "best movies"),
            ListEntry("Marvel Universe", "Heroes and connected stories", LibraryMediaType.MOVIE, query = "Marvel"),
            ListEntry("Star Wars", "Galaxy-spanning saga", LibraryMediaType.MOVIE, query = "Star Wars"),
            ListEntry("Harry Potter in Order", "Wizarding World marathon", LibraryMediaType.MOVIE, query = "Harry Potter"),
            ListEntry("Mission: Impossible", "Ethan Hunt missions", LibraryMediaType.MOVIE, query = "Mission Impossible"),
            ListEntry("John Wick", "The complete action franchise", LibraryMediaType.MOVIE, query = "John Wick"),
            ListEntry("Action", "High-energy favorites", LibraryMediaType.MOVIE, genre = "Action"),
            ListEntry("Comedy", "Laugh-out-loud movie picks", LibraryMediaType.MOVIE, genre = "Comedy"),
            ListEntry("Horror", "Scares for movie night", LibraryMediaType.MOVIE, genre = "Horror"),
            ListEntry("Sci-Fi", "Big ideas and worlds", LibraryMediaType.MOVIE, genre = "Science Fiction"),
            ListEntry("Thrillers", "Tense and twisty", LibraryMediaType.MOVIE, genre = "Thriller"),
            ListEntry("Family Night", "Everyone can watch", LibraryMediaType.MOVIE, genre = "Family"),
            ListEntry("Christopher Nolan", "Big ideas and spectacle", LibraryMediaType.MOVIE, query = "Christopher Nolan"),
            ListEntry("Tom Cruise", "Action and star power", LibraryMediaType.MOVIE, query = "Tom Cruise"),
            ListEntry("Weekly Hot TV", "Series people are watching now", LibraryMediaType.SERIES, query = "popular tv"),
            ListEntry("Binge Worthy", "Easy shows to keep watching", LibraryMediaType.SERIES, query = "binge worthy tv"),
            ListEntry("Crime & Mystery", "Detectives and dark mysteries", LibraryMediaType.SERIES, genre = "Crime"),
            ListEntry("Comedy TV", "Modern and classic comedy", LibraryMediaType.SERIES, genre = "Comedy"),
            ListEntry("Sci-Fi TV", "Big worlds and concepts", LibraryMediaType.SERIES, genre = "Science Fiction"),
            ListEntry("Prestige Drama", "Premium character-driven series", LibraryMediaType.SERIES, genre = "Drama"),
            ListEntry("Limited Series", "One-and-done stories", LibraryMediaType.SERIES, query = "limited series"),
            ListEntry("HBO Favorites", "Prestige HBO series", LibraryMediaType.SERIES, query = "HBO"),
            ListEntry("Netflix Hits", "Streaming-era favorites", LibraryMediaType.SERIES, query = "Netflix"),
            ListEntry("Apple TV+", "Premium Apple originals", LibraryMediaType.SERIES, query = "Apple TV"),
            ListEntry("Anime", "Popular anime series", LibraryMediaType.SERIES, genre = "Animation"),
            ListEntry("K-Dramas", "Popular Korean dramas", LibraryMediaType.SERIES, query = "Korean drama"),
            ListEntry("Highly Rewatchable", "Shows worth returning to", LibraryMediaType.SERIES, query = "rewatchable tv")
        )
    }
}

private class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_search_history_v1", Context.MODE_PRIVATE)
    fun recent(profileId: String): List<String> = prefs.getString("recent:$profileId", "").orEmpty().split('\n').map(String::trim).filter(String::isNotBlank).take(10)
    fun remember(profileId: String, query: String) {
        val value = query.trim()
        if (value.isBlank()) return
        val next = (listOf(value) + recent(profileId).filterNot { it.equals(value, true) }).take(10)
        prefs.edit().putString("recent:$profileId", next.joinToString("\n")).apply()
    }
    fun clear(profileId: String) { prefs.edit().remove("recent:$profileId").apply() }
}
