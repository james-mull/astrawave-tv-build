package com.astrawave.app.core

enum class UnifiedMediaKind { MOVIE, SERIES, EPISODE, LIVE_CHANNEL, PERSONAL_MEDIA }

data class UnifiedPlaybackCandidate(
    val sourceId: String,
    val providerName: String,
    val url: String?,
    val externalUrl: String? = null,
    val authorized: Boolean,
    val priority: Int = 100,
)

data class UnifiedMediaItem(
    val id: String,
    val kind: UnifiedMediaKind,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val sourceLabels: Set<String> = emptySet(),
    val playbackCandidates: List<UnifiedPlaybackCandidate> = emptyList(),
) {
    val playableCandidates: List<UnifiedPlaybackCandidate>
        get() = playbackCandidates
            .filter { it.authorized && (!it.url.isNullOrBlank() || !it.externalUrl.isNullOrBlank()) }
            .sortedBy { it.priority }
}

data class UnifiedCatalogRow(
    val id: String,
    val title: String,
    val items: List<UnifiedMediaItem>,
    val sourceLabels: Set<String> = items.flatMap { it.sourceLabels }.toSet(),
)

object UnifiedCatalogMerger {
    fun merge(rows: List<UnifiedCatalogRow>): List<UnifiedCatalogRow> = rows
        .groupBy { it.id.trim().lowercase() }
        .map { (id, matches) ->
            val items = matches.flatMap { it.items }
                .groupBy { "${it.kind}:${it.id}" }
                .map { (_, duplicates) ->
                    val preferred = duplicates.first()
                    preferred.copy(
                        sourceLabels = duplicates.flatMap { it.sourceLabels }.toSet(),
                        playbackCandidates = duplicates.flatMap { it.playbackCandidates }
                            .distinctBy { "${it.sourceId}:${it.url}:${it.externalUrl}" },
                    )
                }
            UnifiedCatalogRow(
                id = id,
                title = matches.first().title,
                items = items,
                sourceLabels = matches.flatMap { it.sourceLabels }.toSet(),
            )
        }
}
