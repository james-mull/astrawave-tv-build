package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ChannelCustomization

/**
 * Shared read projection for Live and Guide. Keeping customization outside provider models means
 * the same underlying source can render differently for different household profiles.
 */
class ChannelCustomizationProjection(context: Context) {
    private val store = ChannelCustomizationStore(context)

    data class LivePresentation(
        val group: LiveChannelGroup,
        val channelId: String,
        val displayName: String,
        val displayGroup: String?,
        val channelNumber: Int?,
        val logoUrl: String?,
        val epgIdOverride: String?,
        val customized: Boolean,
    )

    fun guide(profileId: String, snapshot: GuideSnapshot): GuideSnapshot =
        snapshot.copy(rows = store.visible(profileId, snapshot.rows))

    fun live(profileId: String, groups: List<LiveChannelGroup>): List<LivePresentation> {
        val edits = store.load(profileId).associateBy { it.channelId }
        return groups.mapNotNull { group ->
            val edit = edits[group.canonicalName]
            if (edit?.hidden == true) return@mapNotNull null
            val best = group.bestCandidate
            LivePresentation(
                group = group,
                channelId = group.canonicalName,
                displayName = edit?.customName ?: group.displayName,
                displayGroup = edit?.customGroup ?: best?.group,
                channelNumber = edit?.customNumber,
                logoUrl = edit?.logoUrlOverride ?: best?.logo,
                epgIdOverride = edit?.epgIdOverride,
                customized = edit != null,
            )
        }.sortedWith(
            compareBy<LivePresentation> { presentation ->
                edits[presentation.channelId]?.sortOrder?.takeIf { it != 0 } ?: Int.MAX_VALUE
            }.thenBy { it.channelNumber ?: Int.MAX_VALUE }
                .thenBy { it.displayName.lowercase() },
        )
    }

    fun customization(profileId: String, channelId: String): ChannelCustomization? =
        store.load(profileId).firstOrNull { it.channelId == channelId }
}
