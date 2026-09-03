package com.astrawave.app.data

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
    private val userSources: MyIptvRepository = MyIptvRepository(),
    private val liveTv: LiveTvRepository = LiveTvRepository(),
) {
    fun load(userConfigs: List<IptvSourceConfig>): CombinedLiveTvSnapshot {
        val free = runCatching { freeTv.loadChannels() }.getOrDefault(emptyList())
        val loadedUser = userSources.loadEnabled(userConfigs)
        val userChannels = loadedUser.flatMap { it.channels }
        val programmes = loadedUser.flatMap { it.programmes }
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
