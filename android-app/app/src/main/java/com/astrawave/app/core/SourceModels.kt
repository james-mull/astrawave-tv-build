package com.astrawave.app.core

enum class SourceType {
    ASTRAWAVE_FREE,
    M3U,
    XTREAM,
    STREMIO,
    NUVIO,
    CLOUDSTREAM,
    PLEX,
    JELLYFIN,
    EMBY,
    DEBRID,
    PODCAST,
    RADIO
}

data class PlaybackSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val enabled: Boolean = true,
    val quality: String? = null,
    val uptimePercent: Double? = null,
    val latencyMs: Int? = null,
    val priority: Int = 100
)

data class GuideProgram(
    val channel: String,
    val title: String,
    val startLabel: String,
    val endLabel: String,
    val category: String
)

data class SportsEvent(
    val league: String,
    val title: String,
    val startLabel: String,
    val broadcaster: String?,
    val status: String
)

object DemoSources {
    val sources = listOf(
        PlaybackSource("astrawave", "AstraWave Free", SourceType.ASTRAWAVE_FREE, quality = "HD", uptimePercent = 99.2, latencyMs = 610, priority = 1),
        PlaybackSource("m3u", "My M3U", SourceType.M3U, enabled = false, priority = 20),
        PlaybackSource("xtream", "Xtream Codes", SourceType.XTREAM, enabled = false, priority = 20),
        PlaybackSource("stremio", "Stremio Add-ons", SourceType.STREMIO, enabled = false, priority = 30),
        PlaybackSource("debrid", "Debrid Providers", SourceType.DEBRID, enabled = false, priority = 10)
    )
}
