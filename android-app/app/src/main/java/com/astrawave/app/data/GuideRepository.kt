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
    val recordingSource: String? = null,
    val playableUrl: String?,
    val playableUrls: List<String> = emptyList(),
    val externalUrl: String? = null,
) {
    val hasRealEpg: Boolean get() = programmes.isNotEmpty()
}

data class GuideSnapshot(
    val rows: List<GuideChannelRow>,
    val sourceGroups: Int,
    val freeChannelCount: Int,
    val handoffCount: Int,
    val userChannelCount: Int,
    val epgChannelCount: Int = 0,
    val epgProgramCount: Int = 0,
)

/** Guide-facing projection over direct Live TV plus reviewed official-provider handoffs. */
class GuideRepository(private val combined: CombinedLiveTvRepository = CombinedLiveTvRepository()) {
    fun load(
        sources: List<IptvSource>,
        epgOverrides: Map<String, String> = emptyMap(),
    ): GuideSnapshot {
        val live = combined.load(
            userSourcesConfig = sources,
            epgOverrides = epgOverrides,
            includeEpg = true,
            expandedPublicInventory = false,
        )
        val customerSourceNames = sources.filter { it.enabled }.map { it.name }.toSet()
        val directRows = live.groups.map { group ->
            val preferred = group.bestCandidate
            val recordingCandidate = group.candidates.firstOrNull { it.source in customerSourceNames }
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
                recordingSource = recordingCandidate?.source,
                playableUrl = preferred?.url,
                playableUrls = group.candidates.map { it.url }.distinct(),
            )
        }
        val handoffRows = live.handoffs.map { handoff ->
            GuideChannelRow(
                id = "handoff:${handoff.id}", name = handoff.name, logo = null, group = handoff.group,
                now = null, next = null, programmes = emptyList(), playableCandidateCount = 0,
                preferredSource = handoff.provider ?: "Official provider", recordingSource = null,
                playableUrl = null, playableUrls = emptyList(), externalUrl = handoff.actionUrl,
            )
        }
        val orderedRows = (directRows + handoffRows).sortedWith(
            compareByDescending<GuideChannelRow> { it.hasRealEpg }
                .thenBy { it.group ?: "ZZZ" }
                .thenBy { it.name },
        )
        return GuideSnapshot(
            rows = orderedRows,
            sourceGroups = live.totalChannelGroups,
            freeChannelCount = live.freeChannelCount,
            handoffCount = live.handoffCount,
            userChannelCount = live.userChannelCount,
            epgChannelCount = directRows.count { it.hasRealEpg },
            epgProgramCount = directRows.sumOf { it.programmes.size },
        )
    }
}
