package com.astrawave.app.data

import com.astrawave.app.core.IptvSource

data class GuideChannelRow(
    val id: String,
    val name: String,
    val logo: String?,
    val group: String?,
    val now: XmlTvProgramme?,
    val next: XmlTvProgramme?,
    val programmes: List<XmlTvProgramme> = emptyList(),
    val playableCandidateCount: Int,
    val preferredSource: String?,
    val playableUrl: String?,
    val playableUrls: List<String> = emptyList(),
    val externalUrl: String? = null,
)

data class GuideSnapshot(
    val rows: List<GuideChannelRow>,
    val sourceGroups: Int,
    val freeChannelCount: Int,
    val handoffCount: Int,
    val userChannelCount: Int,
)

/** Guide-facing projection over direct Live TV plus reviewed official-provider handoffs. */
class GuideRepository(private val combined: CombinedLiveTvRepository = CombinedLiveTvRepository()) {
    fun load(
        sources: List<IptvSource>,
        epgOverrides: Map<String, String> = emptyMap(),
    ): GuideSnapshot {
        val live = combined.load(sources, epgOverrides)
        val directRows = live.groups.map { group ->
            val preferred = group.bestCandidate
            GuideChannelRow(
                id = group.canonicalName,
                name = group.displayName,
                logo = preferred?.logo,
                group = preferred?.group,
                now = group.currentProgram,
                next = group.nextProgram,
                programmes = group.schedule,
                playableCandidateCount = group.candidates.size,
                preferredSource = preferred?.source,
                playableUrl = preferred?.url,
                playableUrls = group.candidates.map { it.url }.distinct(),
            )
        }
        val handoffRows = live.handoffs.map { handoff ->
            GuideChannelRow(
                id = "handoff:${handoff.id}", name = handoff.name, logo = null, group = handoff.group,
                now = null, next = null, programmes = emptyList(), playableCandidateCount = 0,
                preferredSource = handoff.provider ?: "Official provider", playableUrl = null, playableUrls = emptyList(), externalUrl = handoff.actionUrl,
            )
        }
        return GuideSnapshot(
            rows = (directRows + handoffRows).sortedWith(compareBy<GuideChannelRow> { it.group ?: "ZZZ" }.thenBy { it.name }),
            sourceGroups = live.totalChannelGroups,
            freeChannelCount = live.freeChannelCount,
            handoffCount = live.handoffCount,
            userChannelCount = live.userChannelCount,
        )
    }
}