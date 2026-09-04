package com.astrawave.app.data

import com.astrawave.app.core.IptvSource

/**
 * AstraWave Free TV client.
 *
 * Sources are split deliberately:
 * - AstraWave's reviewed registry playlist (highest priority)
 * - FreeCastHub's public-broadcaster playlist, whose repository states that it
 *   only includes free, legal, official public streams. These remain lower
 *   priority and are still health-checked before playback.
 */
class AstraWaveFreeTvRepository(
    private val playlistUrl: String = DEFAULT_PLAYLIST_URL,
    private val publicBroadcasterPlaylistUrl: String = DEFAULT_PUBLIC_BROADCASTER_PLAYLIST_URL,
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
        return (reviewed + publicBroadcasters).distinctBy { "${it.source}:${it.id}:${it.url}" }
    }

    companion object {
        const val DEFAULT_PLAYLIST_URL =
            "https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u"

        const val DEFAULT_PUBLIC_BROADCASTER_PLAYLIST_URL =
            "https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u"
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
