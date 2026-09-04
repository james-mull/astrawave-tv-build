package com.astrawave.app.data

import com.astrawave.app.core.IptvSource

/**
 * AstraWave Free TV client.
 *
 * Sources are intentionally layered so AstraWave can merge duplicates and
 * health-check the best candidate at playback time:
 * - AstraWave reviewed registry playlist (highest priority)
 * - FreeCastHub public-broadcaster playlist
 * - Free-TV/IPTV quality-focused officially-free playlist
 * - IPTV.org US-first and category playlists
 * - World IPTV frequently rechecked public playlist
 *
 * A playlist entry is discovery metadata, never proof that a stream is usable.
 */
class AstraWaveFreeTvRepository(
    private val playlistUrl: String = DEFAULT_PLAYLIST_URL,
    private val publicBroadcasterPlaylistUrl: String = DEFAULT_PUBLIC_BROADCASTER_PLAYLIST_URL,
    private val freeTvPlaylistUrl: String = DEFAULT_FREE_TV_PLAYLIST_URL,
    private val iptvOrgPlaylistUrls: List<String> = DEFAULT_IPTV_ORG_PLAYLIST_URLS,
    private val worldIptvPlaylistUrl: String = DEFAULT_WORLD_IPTV_PLAYLIST_URL,
) {
    fun loadChannels(): List<LiveChannel> {
        val liveTv = LiveTvRepository()
        val reviewed = runCatching {
            liveTv.loadM3u(
                url = playlistUrl,
                source = "AstraWave Free TV",
                priority = 5,
            )
        }.getOrDefault(emptyList())
        val publicBroadcasters = runCatching {
            liveTv.loadM3u(
                url = publicBroadcasterPlaylistUrl,
                source = "AstraWave Public TV",
                priority = 8,
            )
        }.getOrDefault(emptyList())
        val freeTvPublic = runCatching {
            liveTv.loadM3u(
                url = freeTvPlaylistUrl,
                source = "Free-TV Public",
                priority = 9,
            )
        }.getOrDefault(emptyList())
        val iptvOrg = iptvOrgPlaylistUrls.flatMap { url ->
            runCatching {
                liveTv.loadM3u(
                    url = url,
                    source = "IPTV.org Public",
                    priority = 10,
                )
            }.getOrDefault(emptyList())
        }
        val worldIptv = runCatching {
            liveTv.loadM3u(
                url = worldIptvPlaylistUrl,
                source = "World IPTV Verified",
                priority = 12,
            )
        }.getOrDefault(emptyList())
        return (reviewed + publicBroadcasters + freeTvPublic + iptvOrg + worldIptv)
            .distinctBy { "${it.source}:${it.id}:${it.url}" }
    }

    companion object {
        const val DEFAULT_PLAYLIST_URL =
            "https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u"

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
) {
    fun load(userSourcesConfig: List<IptvSource>): CombinedLiveTvSnapshot {
        val free = runCatching { freeTv.loadChannels() }.getOrDefault(emptyList())
        val handoffs = runCatching { handoffRepository.load() }.getOrDefault(emptyList())
        val enabled = userSourcesConfig.filter { it.enabled }
        val userChannels = enabled.flatMap { source ->
            runCatching { userSources.loadChannels(source) }.getOrDefault(emptyList())
        }
        val programmes = enabled.flatMap { source ->
            runCatching { userSources.loadGuide(source) }.getOrDefault(emptyList())
        }
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
