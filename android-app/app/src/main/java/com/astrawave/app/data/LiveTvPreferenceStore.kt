package com.astrawave.app.data

import android.content.Context

/** Lightweight local Live TV personalization that works without requiring sign-in/cloud sync. */
class LiveTvPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun favoriteIds(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    fun isFavorite(channelId: String): Boolean = favoriteIds().contains(channelId)

    fun toggleFavorite(channelId: String): Set<String> {
        val updated = favoriteIds().toMutableSet().apply {
            if (!add(channelId)) remove(channelId)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
        return updated
    }

    fun recentIds(): List<String> = prefs.getString(KEY_RECENTS, "")
        .orEmpty()
        .split('|')
        .filter(String::isNotBlank)

    fun markWatched(channelId: String): List<String> {
        val updated = buildList {
            add(channelId)
            addAll(recentIds().filterNot { it == channelId }.take(MAX_RECENTS - 1))
        }
        prefs.edit().putString(KEY_RECENTS, updated.joinToString("|")).apply()
        return updated
    }

    companion object {
        private const val PREFS_NAME = "astrawave_live_tv_preferences"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
        private const val MAX_RECENTS = 30
    }
}
