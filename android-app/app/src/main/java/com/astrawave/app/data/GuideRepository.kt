package com.astrawave.app.data

import com.astrawave.app.core.IptvSource

data class GuideChannelRow(
    val id: String,
    val name: String,
    val logo: String?,
    val group: String?,
    val now: XmlTvProgramme?,
    val next: XmlTvProgramme?,
    val playableCandidateCount: Int,
    val preferredSource: String?,
)

data class GuideSnapshot(
    val rows: List<GuideChannelRow>,
    val sourceGroups: Int,
    val freeChannelCount: Int,
    val userChannelCount: Int,
)

/**
 * Guide-facing projection over the combined Live TV source model. The TV grid,
 * mini-guide and search can share this one normalized projection.
 */
class GuideRepository(
    private val combined: CombinedLiveTvRepository = CombinedLiveTvRepository(),
) {
    fun load(sources: List<IptvSource>): GuideSnapshot {
        val live = combined.load(sources)
        val rows = live.groups.map { group ->
            val preferred = group.bestCandidate
            GuideChannelRow(
                id = group.canonicalName,
                name = group.displayName,
                logo = preferred?.logo,
                group = preferred?.group,
                now = group.currentProgram,
                next = null,
                playableCandidateCount = group.candidates.size,
                preferredSource = preferred?.source,
            )
        }
        return GuideSnapshot(
            rows = rows,
            sourceGroups = live.totalChannelGroups,
            freeChannelCount = live.freeChannelCount,
            userChannelCount = live.userChannelCount,
        )
    }
}
