package com.astrawave.app.data

import com.astrawave.app.core.IptvSource

/**
 * AstraWave Free TV client.
 *
 * Sources are layered so AstraWave can merge duplicates and health-check the
 * best candidate at playback time. The Nexus US lineup is loaded first because
 * its tvg-id values are generated to match the public Nexus XMLTV guide.
 * Other reviewed/public playlists remain alternate stream candidates.
 */
class AstraWaveFreeTvRepository(
    private val playlistUrl: String = DEFAULT_PLAYLIST_URL,
    private val nexusUsPlaylistUrl: String = DEFAULT_NEXUS_US_PLAYLIST_URL,
    private val publicBroadcasterPlaylistUrl: String = DEFAULT_PUBLIC_BROADCASTER_PLAYLIST_URL,
    private val freeTvPlaylistUrl: String = DEFAULT_FREE_TV_PLAYLIST_URL,
    private val iptvOrgPlaylistUrls: List<String> = DEFAULT_IPTV_ORG_PLAYLIST_URLS,
    private val worldIptvPlaylistUrl: String = DEFAULT_WORLD_IPTV_PLAYLIST_URL,
) {
    fun loadChannels(): List<LiveChannel> {
        val liveTv = LiveTvRepository()

        fun load(url: String, source: String, priority: Int): List<LiveChannel> =
            runCatching {
                liveTv.loadM3u(url = url, source = source, priority = priority)
            }.getOrDefault(emptyList())

        val nexus = load(nexusUsPlaylistUrl, "AstraWave Nexus US", 4)
        val reviewed = load(playlistUrl, "AstraWave Free TV", 5)
        val publicBroadcasters = load(publicBroadcasterPlaylistUrl, "AstraWave Public TV", 8)
        val freeTvPublic = load(freeTvPlaylistUrl, "Free-TV Public", 9)
        val iptvOrg = iptvOrgPlaylistUrls.distinct().flatMapIndexed { index, url ->
            load(url, iptvOrgSourceName(url), 10 + index.coerceAtMost(4))
        }
        val worldIptv = load(worldIptvPlaylistUrl, "World IPTV Verified", 15)

        val nexusByName = nexus.associateBy { it.normalizedName }
        fun canonicalize(channel: LiveChannel): LiveChannel {
            val canonical = nexusByName[channel.normalizedName] ?: return channel
            val canonicalTvgId = canonical.tvgId?.takeIf(String::isNotBlank) ?: return channel
            return channel.copy(id = canonicalTvgId, tvgId = canonicalTvgId)
        }

        val alternates = (reviewed + publicBroadcasters + freeTvPublic + iptvOrg + worldIptv)
            .map(::canonicalize)

        return (nexus + alternates)
            .filter { it.url.isNotBlank() && it.normalizedName.isNotBlank() }
            .distinctBy { "${it.normalizedName}:${it.url}" }
    }

    private fun iptvOrgSourceName(url: String): String = when {
        "/countries/us.m3u" in url -> "IPTV.org US"
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

    companion object {
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
    private val freeTv: AstraWaveFreeTvRepository = AstraWaveFreeTvRepository(),
    private val handoffRepository: FreeTvHandoffRepository = FreeTvHandoffRepository(),
    private val userSources: IptvSourceRepository = IptvSourceRepository(),
    private val liveTv: LiveTvRepository = LiveTvRepository(),
    private val publicEpgUrl: String = DEFAULT_PUBLIC_EPG_URL,
) {
    private val publicProgrammes: List<XmlTvProgramme> by lazy {
        runCatching { liveTv.loadXmlTv(publicEpgUrl) }.getOrDefault(emptyList())
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

    companion object {
        const val DEFAULT_PUBLIC_EPG_URL =
            "https://dearbulut.github.io/iptv/epg/us.xml"
    }
}
