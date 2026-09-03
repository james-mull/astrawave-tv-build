package com.astrawave.app.core

/** Unified AstraWave audio/video-podcast library models. */
enum class AudioItemType {
    MUSIC,
    PODCAST,
    PODCAST_EPISODE,
    VIDEO_PODCAST,
    RADIO,
}

data class AudioItem(
    val id: String,
    val type: AudioItemType,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val mediaUrl: String? = null,
    val durationMs: Long? = null,
    val publishedAt: String? = null,
)

data class AudioSubscription(
    val id: String,
    val title: String,
    val feedUrl: String,
    val artworkUrl: String? = null,
    val videoCapable: Boolean = false,
)

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val genre: String? = null,
    val country: String? = null,
    val logoUrl: String? = null,
)

data class AudioQueue(
    val items: List<AudioItem> = emptyList(),
    val currentIndex: Int = -1,
) {
    val current: AudioItem? get() = items.getOrNull(currentIndex)
}

data class AudioLibrarySnapshot(
    val subscriptions: List<AudioSubscription> = emptyList(),
    val recentEpisodes: List<AudioItem> = emptyList(),
    val radioStations: List<RadioStation> = emptyList(),
    val favorites: List<AudioItem> = emptyList(),
    val queue: AudioQueue = AudioQueue(),
    /** Subscription id -> last load error. Empty means every requested feed loaded successfully. */
    val feedErrors: Map<String, String> = emptyMap(),
)
