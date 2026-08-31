package com.astrawave.app.core

data class MediaRequest(
    val title: String,
    val year: Int? = null,
    val tmdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null
)

data class CandidateStream(
    val providerId: String,
    val label: String,
    val url: String,
    val quality: String? = null,
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val language: String? = null,
    val direct: Boolean = true,
    val authorized: Boolean = true,
    val uptimePercent: Double? = null,
    val latencyMs: Int? = null
)

interface ProviderAdapter {
    val id: String
    val displayName: String
    val type: SourceType

    suspend fun search(request: MediaRequest): List<CandidateStream>
    suspend fun healthCheck(): Boolean
}

object StreamRanker {
    fun rank(streams: List<CandidateStream>): List<CandidateStream> = streams
        .filter { it.authorized }
        .sortedWith(
            compareByDescending<CandidateStream> { qualityScore(it.quality) }
                .thenByDescending { it.uptimePercent ?: 0.0 }
                .thenBy { it.latencyMs ?: Int.MAX_VALUE }
                .thenByDescending { it.bitrateKbps ?: 0 }
        )

    private fun qualityScore(value: String?): Int = when (value?.uppercase()) {
        "8K" -> 5
        "4K", "2160P" -> 4
        "1440P" -> 3
        "1080P", "FHD" -> 2
        "720P", "HD" -> 1
        else -> 0
    }
}
