package com.astrawave.app.data

import android.content.Context

/** Lightweight local state for returning to the last successfully selected live channel. */
data class LastLiveChannel(
    val id: String,
    val name: String,
    val source: String,
    val urls: List<String>,
    val watchedAtEpochMs: Long,
)

class LivePlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_live_playback", Context.MODE_PRIVATE)

    fun save(channel: LastLiveChannel) {
        prefs.edit()
            .putString(KEY_ID, channel.id)
            .putString(KEY_NAME, channel.name)
            .putString(KEY_SOURCE, channel.source)
            .putString(KEY_URLS, channel.urls.joinToString(SEPARATOR))
            .putLong(KEY_WATCHED_AT, channel.watchedAtEpochMs)
            .apply()
    }

    fun last(): LastLiveChannel? {
        val id = prefs.getString(KEY_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val urls = prefs.getString(KEY_URLS, null)
            ?.split(SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
        if (urls.isEmpty()) return null
        return LastLiveChannel(
            id = id,
            name = prefs.getString(KEY_NAME, "Last channel").orEmpty().ifBlank { "Last channel" },
            source = prefs.getString(KEY_SOURCE, "Live TV").orEmpty().ifBlank { "Live TV" },
            urls = urls,
            watchedAtEpochMs = prefs.getLong(KEY_WATCHED_AT, 0L),
        )
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_SOURCE = "source"
        private const val KEY_URLS = "urls"
        private const val KEY_WATCHED_AT = "watched_at"
        private const val SEPARATOR = "\u001f"
    }
}
