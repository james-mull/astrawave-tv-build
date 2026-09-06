package com.astrawave.app.core

/** Provider families AstraWave can model. Credentials remain in secure provider stores. */
enum class LiveProviderType {
    M3U,
    XTREAM,
    STALKER,
    JELLYFIN_LIVE_TV,
    PLEX_LIVE_TV,
    HDHOMERUN,
    TVHEADEND,
    ENIGMA2,
    XMLTV,
    WEBDAV,
    SMB,
    LOCAL,
}

data class ProviderCapabilities(
    val type: LiveProviderType,
    val supportsLive: Boolean = true,
    val supportsVod: Boolean = false,
    val supportsEpg: Boolean = false,
    val supportsCatchUp: Boolean = false,
    val supportsDvr: Boolean = false,
    val supportsTimeshift: Boolean = false,
    val supportsHeaders: Boolean = false,
    val supportsRemoteRecording: Boolean = false,
)

data class SourceRequestHeaders(
    val userAgent: String? = null,
    val referrer: String? = null,
    val origin: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class SourceHealthSample(
    val sourceKey: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val statusCode: Int,
    val contentType: String? = null,
    val qualityLabel: String? = null,
    val bitrateKbps: Int? = null,
    val checkedAtEpochMs: Long = System.currentTimeMillis(),
)

data class SourceHealthScore(
    val sourceKey: String,
    val score: Int,
    val uptimePercent: Double,
    val medianLatencyMs: Long?,
    val recentFailures: Int,
    val samples: Int,
)

enum class DownloadMediaType { MOVIE, EPISODE, PODCAST, AUDIO, PERSONAL_MEDIA }
enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETE, FAILED, CANCELED }

data class DownloadRequest(
    val id: String,
    val profileId: String,
    val mediaId: String,
    val mediaType: DownloadMediaType,
    val title: String,
    val sourceUrl: String,
    val posterUrl: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val progressPercent: Int = 0,
    val localUri: String? = null,
    val error: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

data class TravelModePolicy(
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val maxStorageGb: Int = 10,
    val movieTarget: Int = 1,
    val episodeTarget: Int = 2,
    val podcastTarget: Int = 3,
)

enum class HouseholdVoteValue { LOVE, LIKE, MAYBE, PASS }

data class HouseholdVote(
    val profileId: String,
    val mediaId: String,
    val value: HouseholdVoteValue,
    val votedAtEpochMs: Long = System.currentTimeMillis(),
)

data class HouseholdCandidate(
    val mediaId: String,
    val mediaType: String,
    val title: String,
    val posterUrl: String? = null,
)

data class HouseholdWatchSession(
    val id: String,
    val name: String,
    val profileIds: List<String>,
    val candidates: List<HouseholdCandidate>,
    val votes: List<HouseholdVote> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    fun score(mediaId: String): Int = votes.filter { it.mediaId == mediaId }.sumOf {
        when (it.value) {
            HouseholdVoteValue.LOVE -> 4
            HouseholdVoteValue.LIKE -> 2
            HouseholdVoteValue.MAYBE -> 1
            HouseholdVoteValue.PASS -> -3
        }
    }

    fun winner(): HouseholdCandidate? = candidates.maxByOrNull { score(it.mediaId) }
}

enum class AstraIntentType {
    DISCOVER,
    FIND_MOVIE,
    FIND_SHOW,
    SPORTS_TONIGHT,
    PLAY_TEAM,
    FIND_SHORT_WATCH,
    SIMILAR_TO,
    NEW_THIS_WEEK,
    OPEN_GUIDE,
    UNKNOWN,
}

data class AstraIntent(
    val type: AstraIntentType,
    val rawQuery: String,
    val titleHint: String? = null,
    val teamHint: String? = null,
    val genreHint: String? = null,
    val maxRuntimeMinutes: Int? = null,
    val minRating: Double? = null,
)

data class ChannelCustomization(
    val channelId: String,
    val customName: String? = null,
    val customNumber: Int? = null,
    val customGroup: String? = null,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
    val epgIdOverride: String? = null,
    val logoUrlOverride: String? = null,
)
