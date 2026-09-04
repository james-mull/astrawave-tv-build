package com.astrawave.app.data

import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioItemType
import com.astrawave.app.core.AudioLibrarySnapshot
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import java.io.ByteArrayInputStream

/** Loads AstraWave-discovered and user-added podcasts/radio into one audio library. */
class AudioLibraryRepository(
    private val discovery: AstraWaveAudioDiscoveryRepository = AstraWaveAudioDiscoveryRepository(),
) {
    fun load(
        subscriptions: List<AudioSubscription>,
        radioStations: List<RadioStation>,
        includeBuiltInDiscovery: Boolean = true,
    ): AudioLibrarySnapshot {
        val discoveredSubscriptions = if (includeBuiltInDiscovery) {
            runCatching { discovery.discoverPodcasts() }.getOrDefault(emptyList())
        } else emptyList()
        val discoveredRadio = if (includeBuiltInDiscovery) {
            runCatching { discovery.discoverRadio() }.getOrDefault(emptyList())
        } else emptyList()

        val mergedSubscriptions = (subscriptions + discoveredSubscriptions)
            .distinctBy { it.feedUrl.lowercase() }
        val mergedRadio = (radioStations + discoveredRadio)
            .distinctBy { it.streamUrl.lowercase() }

        val feedErrors = linkedMapOf<String, String>()
        val recentEpisodes = mergedSubscriptions.take(30).flatMap { subscription ->
            runCatching {
                val xml = SimpleHttp.getText(subscription.feedUrl)
                PodcastRssParser.parse(ByteArrayInputStream(xml.toByteArray())).take(10).mapIndexed { index, episode ->
                    AudioItem(
                        id = "${subscription.id}-$index-${episode.title.hashCode()}",
                        type = if (subscription.videoCapable) AudioItemType.VIDEO_PODCAST else AudioItemType.PODCAST_EPISODE,
                        title = episode.title,
                        subtitle = subscription.title,
                        artworkUrl = subscription.artworkUrl,
                        mediaUrl = episode.mediaUrl,
                        publishedAt = episode.published,
                    )
                }
            }.onFailure { error ->
                feedErrors[subscription.id] = error.message ?: error::class.java.simpleName
            }.getOrDefault(emptyList())
        }.distinctBy { it.id }
            .sortedByDescending { it.publishedAt.orEmpty() }

        return AudioLibrarySnapshot(
            subscriptions = mergedSubscriptions,
            recentEpisodes = recentEpisodes,
            radioStations = mergedRadio,
            feedErrors = feedErrors,
        )
    }
}
