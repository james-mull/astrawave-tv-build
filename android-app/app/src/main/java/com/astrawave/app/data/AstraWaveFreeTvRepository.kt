package com.astrawave.app.data

import com.astrawave.app.core.IptvSource
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * AstraWave Free TV client.
 *
 * Startup intentionally loads a small market-aware primary lineup so Live TV can render quickly.
 * Primary feeds are fetched concurrently; the broad public inventory is reserved for explicit
 * fallback/diagnostic use and never blocks first paint.
 */
class AstraWaveFreeTvRepository(
    private val marketCountry: String = defaultMarketCountry(),
    private val playlistUrl: String = DEFAULT_PLAYLIST_URL,
    private val nexusUsPlaylistUrl: String = DEFAULT_NEXUS_US_PLAYLIST_URL,
    private val publicBroadcasterPlaylistUrl: String = DEFAULT_PUBLIC_BROADCASTER_PLAYLIST_URL,
    private val freeTvPlaylistUrl: String = DEFAULT_FREE_TV_PLAYLIST_URL,
    private val iptvOrgPlaylistUrls: List<String> = DEFAULT_IPTV_ORG_PLAYLIST_URLS,
    private val worldIptvPlaylistUrl: String = DEFAULT_WORLD_IPTV_PLAYLIST_URL,
) {
    private data class Feed(val url: String, val source: String, val priority: Int)

    fun loadChannels(): List<LiveChannel> {
        val market = sanitizeCountry(marketCountry)
        val feeds = buildList {
            add(Feed("https://iptv-org.github.io/iptv/countries/$market.m3u", "IPTV.org ${market.uppercase(Locale.US)}", 2))
            if (market == "us") add(Feed(nexusUsPlaylistUrl, "AstraWave Nexus US", 4))
            add(Feed(playlistUrl, "AstraWave Free TV", 5))
        }
        return loadFeedsConcurrently(feeds)
            .filter { it.url.isNotBlank() && it.normalizedName.isNotBlank() }
            .distinctBy { "${it.normalizedName}:${it.url}" }
    }

    fun loadExpandedChannels(): List<LiveChannel> {
        val primary = loadChannels()
        val feeds = buildList {
            add(Feed(publicBroadcasterPlaylistUrl, "AstraWave Public TV", 8))
            add(Feed(freeTvPlaylistUrl, "Free-TV Public", 9))
            iptvOrgPlaylistUrls.distinct().forEachIndexed { index, url ->
                add(Feed(url, iptvOrgSourceName(url), 10 + index.coerceAtMost(6)))
            }
            add(Feed(worldIptvPlaylistUrl, "World IPTV Verified", 18))
        }
        val alternatesRaw = loadFeedsConcurrently(feeds, maxThreads = 6)
        val canonicalByName = primary.associateBy { it.normalizedName }
        val alternates = alternatesRaw.map { channel ->
            val canonical = canonicalByName[channel.normalizedName]
            val canonicalTvgId = canonical?.tvgId?.takeIf(String::isNotBlank)
            if (canonicalTvgId == null) channel else channel.copy(id = canonicalTvgId, tvgId = canonicalTvgId)
        }
        return (primary + alternates)
            .filter { it.url.isNotBlank() && it.normalizedName.isNotBlank() }
            .distinctBy { "${it.normalizedName}:${it.url}" }
    }

    private fun loadFeedsConcurrently(feeds: List<Feed>, maxThreads: Int = 3): List<LiveChannel> {
        if (feeds.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(feeds.size.coerceAtMost(maxThreads).coerceAtLeast(1))
        return try {
            val liveTv = LiveTvRepository()
            val futures = feeds.map { feed ->
                pool.submit<List<LiveChannel>> {
                    runCatching { liveTv.loadM3u(feed.url, feed.source, feed.priority) }.getOrDefault(emptyList())
                }
            }
            futures.flatMap { future -> runCatching { future.get(12, TimeUnit.SECONDS) }.getOrDefault(emptyList()) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun iptvOrgSourceName(url: String): String {
        val country = Regex("/countries/([a-z]{2})\\.m3u", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
        if (!country.isNullOrBlank()) return "IPTV.org ${country.uppercase(Locale.US)}"
        return when {
            "/categories/sports.m3u" in url -> "IPTV.org Sports"
            "/categories/news.m3u" in url -> "IPTV.org News"
            "/categories/entertainment.m3u" in url -> "IPTV.org Entertainment"
            "/categories/movies.m3u" in url -> "IPTV.org Movies"
            "/categories/documentary.m3u" in url -> "IPTV.org Documentary"
            "/categories/music.m3u" in url -> "IPTV.org Music"
            "/categories/kids.m3u" in url -> "IPTV.org Kids"
            "/categories/science.m3u" in url -> "IPTV.org Science"
            "/categories/weather.m3u" in url -> "IPTV.org Weather"
            else -> "IPTV.org Public"
        }
    }

    companion object {
        private val supportedMarkets = setOf(
            "us","ca","gb","au","nz","mx","br","ar","co","cl","pe","es","fr","de","it","pt","nl","be","ie","ch","at","se","no","dk","fi","pl","cz","gr","tr","in","jp","kr","ph","sg","my","za",
        )

        fun defaultMarketCountry(): String = sanitizeCountry(Locale.getDefault().country)
        fun sanitizeCountry(value: String): String = value.trim().lowercase(Locale.US).takeIf { it in supportedMarkets } ?: "us"

        const val DEFAULT_PLAYLIST_URL =
            "https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u"
        const val DEFAULT_NEXUS_US_PLAYLIST_URL =
            "https://dearbulut.github.io/iptv/playlists/country/us.m3u"
        const val DEFAULT_PUBLIC_BROADCASTER_PLAYLIST_URL =
            "https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u"
        const val DEFAULT_FREE_TV_PLAYLIST_URL =
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"

        val DEFAULT_IPTV_ORG_PLAYLIST_URLS = listOf(
            "https://iptv-org.github.io/iptv/countries/us.m3u",
            "https://iptv-org.github.io/iptv/countries/ca.m3u",
            "https://iptv-org.github.io/iptv/countries/gb.m3u",
            "https://iptv-org.github.io/iptv/countries/au.m3u",
            "https://iptv-org.github.io/iptv/countries/mx.m3u",
            "https://iptv-org.github.io/iptv/categories/sports.m3u",
            "https://iptv-org.github.io/iptv/categories/news.m3u",
            "https://iptv-org.github.io/iptv/categories/entertainment.m3u",
            "https://iptv-org.github.io/iptv/categories/movies.m3u",
            "https://iptv-org.github.io/iptv/categories/documentary.m3u",
            "https://iptv-org.github.io/iptv/categories/music.m3u",
            "https://iptv-org.github.io/iptv/categories/kids.m3u",
            "https://iptv-org.github.io/iptv/categories/science.m3u",
            "https://iptv-org.github.io/iptv/categories/weather.m3u",
        )

        const val DEFAULT_WORLD_IPTV_PLAYLIST_URL =
            "https://romaxa55.github.io/world_ip_tv/output/index.m3u"
    }
}

data class CombinedLiveTvSnapshot(
    val groups: List<LiveChannelGroup>,
    val handoffs: List<FreeTvHandoff>,
    val freeChannelCount: Int,
    val handoffCount: Int,
    val userChannelCount: Int,
    val totalChannelGroups: Int,
)

class CombinedLiveTvRepository(
    private val marketCountry: String = AstraWaveFreeTvRepository.defaultMarketCountry(),
    private val freeTv: AstraWaveFreeTvRepository = AstraWaveFreeTvRepository(marketCountry = marketCountry),
    private val handoffRepository: FreeTvHandoffRepository = FreeTvHandoffRepository(),
    private val userSources: IptvSourceRepository = IptvSourceRepository(),
    private val liveTv: LiveTvRepository = LiveTvRepository(),
) {
    private val market = AstraWaveFreeTvRepository.sanitizeCountry(marketCountry)
    private val publicEpgUrls = buildList {
        if (market == "us") add("https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-epg/us.xml.gz")
        add("https://dearbulut.github.io/iptv/epg/$market.xml")
        add("https://iptv-epg.org/files/epg-$market.xml")
    }

    private val publicProgrammes: List<XmlTvProgramme> by lazy {
        if (publicEpgUrls.isEmpty()) emptyList() else {
            val pool = Executors.newFixedThreadPool(publicEpgUrls.size.coerceAtMost(3))
            try {
                publicEpgUrls
                    .map { url -> pool.submit<List<XmlTvProgramme>> { runCatching { liveTv.loadXmlTv(url) }.getOrDefault(emptyList()) } }
                    .flatMap { future -> runCatching { future.get(12, TimeUnit.SECONDS) }.getOrDefault(emptyList()) }
                    .distinctBy { "${it.channelId}:${it.start}:${it.stop}:${it.title}" }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    fun load(
        userSourcesConfig: List<IptvSource>,
        epgOverrides: Map<String, String> = emptyMap(),
        includeEpg: Boolean = false,
        expandedPublicInventory: Boolean = false,
    ): CombinedLiveTvSnapshot {
        val free = runCatching {
            if (expandedPublicInventory) freeTv.loadExpandedChannels() else freeTv.loadChannels()
        }.getOrDefault(emptyList())
        val handoffs = runCatching { handoffRepository.load() }.getOrDefault(emptyList())
        val enabled = userSourcesConfig.filter { it.enabled }
        val userChannels = enabled.flatMap { source ->
            runCatching { userSources.loadChannels(source) }.getOrDefault(emptyList())
        }
        val userProgrammes = if (includeEpg) enabled.flatMap { source ->
            runCatching { userSources.loadGuide(source) }.getOrDefault(emptyList())
        } else emptyList()
        val programmes = if (includeEpg) {
            (publicProgrammes + userProgrammes).distinctBy { "${it.channelId}:${it.start}:${it.stop}:${it.title}" }
        } else emptyList()
        val baseGroups = liveTv.merge(channelLists = listOf(free, userChannels), programmes = programmes)
        val groups = if (includeEpg) applyEpgOverrides(baseGroups, programmes, epgOverrides) else baseGroups
        return CombinedLiveTvSnapshot(
            groups = groups,
            handoffs = handoffs,
            freeChannelCount = free.size,
            handoffCount = handoffs.size,
            userChannelCount = userChannels.size,
            totalChannelGroups = groups.size + handoffs.size,
        )
    }

    private fun applyEpgOverrides(
        groups: List<LiveChannelGroup>,
        programmes: List<XmlTvProgramme>,
        overrides: Map<String, String>,
    ): List<LiveChannelGroup> {
        if (overrides.isEmpty()) return groups
        val byChannel = programmes.groupBy { it.channelId }
        val now = System.currentTimeMillis()
        return groups.map { group ->
            val overrideId = overrides[group.canonicalName]?.trim().takeUnless { it.isNullOrBlank() } ?: return@map group
            val schedule = byChannel[overrideId].orEmpty()
                .distinctBy { "${it.start}:${it.stop}:${it.title}" }
                .sortedBy { LiveTvRepository.parseXmlTvEpochMs(it.start) ?: Long.MAX_VALUE }
            if (schedule.isEmpty()) return@map group
            val current = schedule.firstOrNull { programme ->
                val start = LiveTvRepository.parseXmlTvEpochMs(programme.start) ?: return@firstOrNull false
                val stop = LiveTvRepository.parseXmlTvEpochMs(programme.stop) ?: return@firstOrNull false
                now in start until stop
            }
            val next = schedule.firstOrNull { programme ->
                val start = LiveTvRepository.parseXmlTvEpochMs(programme.start) ?: return@firstOrNull false
                start > now && programme != current
            }
            group.copy(schedule = schedule, currentProgram = current, nextProgram = next)
        }
    }
}
