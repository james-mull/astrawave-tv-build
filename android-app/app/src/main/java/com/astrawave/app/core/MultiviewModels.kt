package com.astrawave.app.core

/** Stable state contract for AstraWave 2-up, 3-up and 4-up multiview. */
enum class MultiviewLayout(val paneCount: Int) {
    TWO_UP(2),
    THREE_UP(3),
    FOUR_UP(4),
}

data class MultiviewPane(
    val id: String,
    val title: String,
    val streamUrl: String,
    val sourceName: String,
    val channelId: String? = null,
    val eventId: String? = null,
    val muted: Boolean = true,
)

data class MultiviewSession(
    val id: String,
    val layout: MultiviewLayout,
    val panes: List<MultiviewPane>,
    val activeAudioPaneId: String? = panes.firstOrNull()?.id,
) {
    init {
        require(panes.size <= layout.paneCount) { "Too many panes for ${layout.name}" }
        require(panes.map { it.id }.distinct().size == panes.size) { "Pane IDs must be unique" }
        if (activeAudioPaneId != null) {
            require(panes.any { it.id == activeAudioPaneId }) { "Active audio pane must exist in the session" }
        }
    }

    fun withActiveAudio(paneId: String): MultiviewSession =
        copy(
            panes = panes.map { it.copy(muted = it.id != paneId) },
            activeAudioPaneId = paneId,
        )

    fun replacePane(paneId: String, replacement: MultiviewPane): MultiviewSession =
        copy(panes = panes.map { if (it.id == paneId) replacement.copy(id = paneId) else it })
}
