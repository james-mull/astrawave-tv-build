package com.astrawave.app.core

class SourceRegistry(initial: List<PlaybackSource> = DemoSources.sources) {
    private val providers = linkedMapOf<String, PlaybackSource>().apply {
        initial.sortedBy { it.priority }.forEach { put(it.id, it) }
    }

    fun all(): List<PlaybackSource> = providers.values.sortedBy { it.priority }

    fun enabled(): List<PlaybackSource> = all().filter { it.enabled }

    fun upsert(source: PlaybackSource) {
        providers[source.id] = source
    }

    fun setEnabled(id: String, enabled: Boolean) {
        providers[id]?.let { providers[id] = it.copy(enabled = enabled) }
    }

    fun ranked(): List<PlaybackSource> = enabled().sortedWith(
        compareBy<PlaybackSource> { it.priority }
            .thenByDescending { it.uptimePercent ?: 0.0 }
            .thenBy { it.latencyMs ?: Int.MAX_VALUE }
    )
}
