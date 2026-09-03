package com.astrawave.app.data

import com.astrawave.app.core.IptvSource

/**
 * AstraWave Free TV client. The published playlist is produced by the daily
 * rights/health workflow in astrwave-free-tv and then consumed like any other
 * live source. This keeps the app decoupled from discovery/checking logic.
 */
class AstraWaveFreeTvRepository(
    private val playlistUrl: String = DEFAULT_PLAYLIST_URL,
) {
    fun loadChannels(): List<LiveChannel> =
        LiveTvRepository().loadM3u(
            url = playlistUrl,
            source = "AstraWave Free TV",
            priority = 5,
        )

    companion object {
        const val DEFAULT_PLAYLIST_URL =
            "https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u"
    }
}

data class CombinedLiveTvSnapshot(
    val groups: List<LiveChannelGroup>,
    val freeChannelCount: Int,
    val userChannelCount: Int,
    val totalChannelGroups: Int,
)

class CombinedLiveTvRepository(
    private val freeTv: AstraWaveFreeTvRepository = AstraWaveFreeTvRepository(),
    private val userSources: IptvSourceRepository = IptvSourceRepository(),
    private val liveTv: LiveTvRepository = LiveTvRepository(),
) {
    fun load(userSourcesConfig: List<IptvSource>): CombinedLiveTvSnapshot {
        val free = runCatching { freeTv.loadChannels() }.getOrDefault(emptyList())
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
            freeChannelCount = free.size,
            userChannelCount = userChannels.size,
            totalChannelGroups = groups.size,
        )
    }
}
