package com.astrawave.app.data

import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioItemType
import com.astrawave.app.core.AudioLibrarySnapshot
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import java.io.ByteArrayInputStream

/** Loads podcast feeds and radio stations into one AstraWave audio library projection. */
class AudioLibraryRepository {
    fun load(
        subscriptions: List<AudioSubscription>,
        radioStations: List<RadioStation>,
    ): AudioLibrarySnapshot {
        val recentEpisodes = subscriptions.flatMap { subscription ->
            runCatching {
                val xml = SimpleHttp.getText(subscription.feedUrl)
                PodcastRssParser.parse(ByteArrayInputStream(xml.toByteArray())).mapIndexed { index, episode ->
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
            }.getOrDefault(emptyList())
        }.sortedByDescending { it.publishedAt.orEmpty() }

        return AudioLibrarySnapshot(
            subscriptions = subscriptions,
            recentEpisodes = recentEpisodes,
            radioStations = radioStations,
        )
    }
}
