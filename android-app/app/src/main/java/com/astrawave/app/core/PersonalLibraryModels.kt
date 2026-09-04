package com.astrawave.app.core

/** Personal library models shared by My AstraWave, cloud sync and future Nuvio UI adapters. */
enum class LibraryMediaType {
    MOVIE,
    SERIES,
    EPISODE,
    LIVE_CHANNEL,
    SPORTS_EVENT,
    PODCAST,
    PODCAST_EPISODE,
    MUSIC,
    RADIO,
}

data class LibraryItemRef(
    val id: String,
    val type: LibraryMediaType,
    val title: String,
    val posterUrl: String? = null,
    val sourceId: String? = null,
)

data class AstraWaveList(
    val id: String,
    val profileId: String,
    val name: String,
    val description: String = "",
    val items: List<LibraryItemRef> = emptyList(),
    val sortOrder: Int = 0,
    val isPinned: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

data class FavoriteEntry(
    val profileId: String,
    val item: LibraryItemRef,
    val createdAtEpochMs: Long = 0L,
)

data class WatchlistEntry(
    val profileId: String,
    val item: LibraryItemRef,
    val createdAtEpochMs: Long = 0L,
    val notifyWhenAvailable: Boolean = true,
)

data class AccountOverview(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val activeProfileId: String,
    val planName: String = "AstraWave Free",
    val deviceCount: Int = 0,
    val cloudSyncEnabled: Boolean = true,
)

enum class AccountSection(val title: String) {
    PROFILES("Profiles & Household"),
    SUBSCRIPTION("Subscription & Premium"),
    IPTV("Live TV Sources"),
    PERSONAL_MEDIA("Personal Media"),
    CLOUD_DEBRID("Cloud & Debrid"),
    ADDONS("Extensions & Addons"),
    DEVICES("Devices"),
    PLAYBACK("Playback"),
    SUBTITLES_AUDIO("Subtitles & Audio"),
    DOWNLOADS_STORAGE("Downloads & Storage"),
    NOTIFICATIONS("Notifications"),
    SPORTS("Teams & Sports"),
    APPEARANCE("Appearance & Layout"),
    BACKUP_SYNC("Sync, Backup & Restore"),
    PARENTAL_CONTROLS("Parental Controls"),
    PRIVACY("Privacy & Data"),
    DIAGNOSTICS("Diagnostics"),
}
