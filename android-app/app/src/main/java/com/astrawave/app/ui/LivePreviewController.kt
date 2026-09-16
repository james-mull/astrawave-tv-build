package com.astrawave.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class LivePreviewState(
    val contentId: String? = null,
    val title: String = "Choose something to watch",
    val subtitle: String = "Browse channels, the guide, or today's games.",
    val urls: List<String> = emptyList(),
    val provider: String? = null,
) {
    val activeUrl: String? get() = urls.firstOrNull()
    val isPlayable: Boolean get() = activeUrl != null
}

/** Shared selection and playback state used by Live TV, Guide, and Sports. */
class LivePreviewController {
    var state by mutableStateOf(LivePreviewState())
        private set

    /** Returns true when the same item was selected again and should open full screen. */
    fun select(
        contentId: String,
        title: String,
        subtitle: String,
        urls: List<String>,
        provider: String? = null,
    ): Boolean {
        if (state.contentId == contentId && state.urls.isNotEmpty()) return true
        state = LivePreviewState(
            contentId = contentId,
            title = title,
            subtitle = subtitle,
            urls = urls.distinct().filter(String::isNotBlank),
            provider = provider,
        )
        return false
    }

    fun clear() {
        state = LivePreviewState()
    }
}
