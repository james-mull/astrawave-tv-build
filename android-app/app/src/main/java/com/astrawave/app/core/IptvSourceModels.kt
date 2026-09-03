package com.astrawave.app.core

/** User-managed IPTV source definitions for M3U, Xtream and XMLTV. */
enum class IptvSourceType {
    M3U,
    XTREAM,
}

enum class IptvSourceStatus {
    NOT_TESTED,
    CHECKING,
    READY,
    DEGRADED,
    ERROR,
    DISABLED,
}

data class IptvSource(
    val id: String,
    val profileId: String,
    val name: String,
    val type: IptvSourceType,
    val enabled: Boolean = true,
    val priority: Int = 20,
    val m3uUrl: String? = null,
    val xtreamServer: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val xmlTvUrl: String? = null,
    val status: IptvSourceStatus = IptvSourceStatus.NOT_TESTED,
    val channelCount: Int = 0,
    val guideProgramCount: Int = 0,
    val lastCheckedEpochMs: Long = 0L,
    val lastError: String? = null,
)

data class IptvSourceTestResult(
    val sourceId: String,
    val status: IptvSourceStatus,
    val channelCount: Int,
    val guideProgramCount: Int,
    val error: String? = null,
)

object IptvSourceValidation {
    fun validate(source: IptvSource): List<String> = buildList {
        if (source.name.isBlank()) add("Source name is required")
        when (source.type) {
            IptvSourceType.M3U -> if (source.m3uUrl.isNullOrBlank()) add("M3U URL is required")
            IptvSourceType.XTREAM -> {
                if (source.xtreamServer.isNullOrBlank()) add("Xtream server is required")
                if (source.xtreamUsername.isNullOrBlank()) add("Xtream username is required")
                if (source.xtreamPassword.isNullOrBlank()) add("Xtream password is required")
            }
        }
    }
}
