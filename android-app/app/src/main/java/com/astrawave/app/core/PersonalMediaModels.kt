package com.astrawave.app.core

/** Authorized user-owned/personal media connections exposed through AstraWave. */
enum class PersonalMediaProvider {
    PLEX,
    JELLYFIN,
    EMBY,
    WEBDAV,
    NAS,
}

enum class PersonalMediaConnectionStatus {
    NOT_CONNECTED,
    CONNECTING,
    READY,
    DEGRADED,
    ERROR,
}

data class PersonalMediaConnection(
    val id: String,
    val profileId: String,
    val provider: PersonalMediaProvider,
    val name: String,
    val serverUrl: String,
    val enabled: Boolean = true,
    val status: PersonalMediaConnectionStatus = PersonalMediaConnectionStatus.NOT_CONNECTED,
    val libraryCount: Int = 0,
    val itemCount: Int = 0,
    val lastSyncEpochMs: Long = 0L,
    val lastError: String? = null,
)

data class PersonalMediaLibrary(
    val id: String,
    val connectionId: String,
    val name: String,
    val mediaTypes: Set<LibraryMediaType>,
    val itemCount: Int = 0,
)

data class PersonalMediaItem(
    val id: String,
    val connectionId: String,
    val libraryId: String,
    val type: LibraryMediaType,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val durationMs: Long? = null,
    val streamUrl: String? = null,
    val externalId: String? = null,
)

data class PersonalMediaSnapshot(
    val connections: List<PersonalMediaConnection>,
    val libraries: List<PersonalMediaLibrary>,
    val recentItems: List<PersonalMediaItem>,
)

interface PersonalMediaGateway {
    fun test(connection: PersonalMediaConnection): PersonalMediaConnection
    fun libraries(connection: PersonalMediaConnection): List<PersonalMediaLibrary>
    fun recent(connection: PersonalMediaConnection, limit: Int = 50): List<PersonalMediaItem>
    fun search(connection: PersonalMediaConnection, query: String, limit: Int = 50): List<PersonalMediaItem>
}

object PersonalMediaValidation {
    fun validate(connection: PersonalMediaConnection): List<String> = buildList {
        if (connection.name.isBlank()) add("Connection name is required")
        if (connection.serverUrl.isBlank()) add("Server URL is required")
        if (!connection.serverUrl.startsWith("http://") && !connection.serverUrl.startsWith("https://")) {
            add("Server URL must use http:// or https://")
        }
    }
}
