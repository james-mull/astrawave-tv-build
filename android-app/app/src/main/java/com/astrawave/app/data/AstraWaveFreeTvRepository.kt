package com.astrawave.app.data

import com.astrawave.app.core.IptvSource
import java.util.Locale

/**
 * AstraWave Free TV client.
 *
 * The public lineup is market-aware: the device country is prioritized first, while
 * U.S. and worldwide/public category sources remain available as fallbacks. This keeps
 * AstraWave useful for a commercial audience without hard-coding Albuquerque or one country.
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
    fun loadChannels(): List<LiveChannel> {
        val liveTv = LiveTvRepository()
        val market = sanitizeCountry(marketCountry)

        fun load(url: String, source: String, priority: Int): List<LiveChannel> =
            runCatching { liveTv.loadM3u(url = url, source = source, priority = priority) }
                .getOrDefault(emptyList())

        val marketChannels = load(
            "https://iptv-org.github.io/iptv/countries/$market.m3u",
            "IPTV.org ${market.uppercase(Locale.US)}",
            2,
        )
        val nexus = if (market == "us") load(nexusUsPlaylistUrl, "AstraWave Nexus US", 4) else emptyList()
        val reviewed = load(playlistUrl, "AstraWave Free TV", 5)
        val publicBroadcasters = load(publicBroadcasterPlaylistUrl, "AstraWave Public TV", 8)
        val freeTvPublic = load(freeTvPlaylistUrl, "Free-TV Public", 9)
        val iptvOrg = iptvOrgPlaylistUrls.distinct().flatMapIndexed { index, url ->
            load(url, iptvOrgSourceName(url), 10 + index.coerceAtMost(6))
        }
        val worldIptv = load(worldIptvPlaylistUrl, "World IPTV Verified", 18)

        val canonicalRows = if (nexus.isNotEmpty()) nexus else marketChannels
        val canonicalByName = canonicalRows.associateBy { it.normalizedName }
        fun canonicalize(channel: LiveChannel): LiveChannel {
            val canonical = canonicalByName[channel.normalizedName] ?: return channel
            val canonicalTvgId = canonical.tvgId?.takeIf(String::isNotBlank) ?: return channel
            return channel.copy(id = canonicalTvgId, tvgId = canonicalTvgId)
        }

        val alternates = (reviewed + publicBroadcasters + freeTvPublic + iptvOrg + worldIptv)
            .map(::canonicalize)

        return (marketChannels + nexus + alternates)
            .filter { it.url.isNotBlank() && it.normalizedName.isNotBlank() }
            .distinctBy { "${it.normalizedName}:${it.url}" }
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
        publicEpgUrls
            .flatMap { url -> runCatching { liveTv.loadXmlTv(url) }.getOrDefault(emptyList()) }
            .distinctBy { "${it.channelId}:${it.start}:${it.stop}:${it.title}" }
    }

    fun load(userSourcesConfig: List<IptvSource>): CombinedLiveTvSnapshot {
        val free = runCatching { freeTv.loadChannels() }.getOrDefault(emptyList())
        val handoffs = runCatching { handoffRepository.load() }.getOrDefault(emptyList())
        val enabled = userSourcesConfig.filter { it.enabled }
        val userChannels = enabled.flatMap { source ->
            runCatching { userSources.loadChannels(source) }.getOrDefault(emptyList())
        }
        val userProgrammes = enabled.flatMap { source ->
            runCatching { userSources.loadGuide(source) }.getOrDefault(emptyList())
        }
        val programmes = (publicProgrammes + userProgrammes)
            .distinctBy { "${it.channelId}:${it.start}:${it.stop}:${it.title}" }
        val groups = liveTv.merge(
            channelLists = listOf(free, userChannels),
            programmes = programmes,
        )
        return CombinedLiveTvSnapshot(
            groups = groups,
            handoffs = handoffs,
            freeChannelCount = free.size,
            handoffCount = handoffs.size,
            userChannelCount = userChannels.size,
            totalChannelGroups = groups.size + handoffs.size,
        )
    }
}
