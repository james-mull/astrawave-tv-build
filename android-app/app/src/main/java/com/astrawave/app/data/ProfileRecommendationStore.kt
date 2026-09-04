package com.astrawave.app.data

import android.content.Context

/**
 * Durable local preference/feedback layer for profile-aware recommendations.
 * Kept separate from watch history so a user can explicitly tune discovery without
 * deleting playback history or saved library items.
 */
class ProfileRecommendationStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_recommendation_feedback_v1", Context.MODE_PRIVATE)

    data class Snapshot(
        val hiddenItemIds: Set<String>,
        val notInterestedItemIds: Set<String>,
        val preferredGenres: Set<String>,
        val dislikedGenres: Set<String>,
    )

    fun snapshot(profileId: String): Snapshot = Snapshot(
        hiddenItemIds = stringSet("hidden", profileId),
        notInterestedItemIds = stringSet("not_interested", profileId),
        preferredGenres = stringSet("preferred_genres", profileId),
        dislikedGenres = stringSet("disliked_genres", profileId),
    )

    fun hide(profileId: String, itemId: String, hidden: Boolean = true) =
        mutateSet("hidden", profileId, itemId, hidden)

    fun notInterested(profileId: String, itemId: String, enabled: Boolean = true) =
        mutateSet("not_interested", profileId, itemId, enabled)

    fun preferGenre(profileId: String, genre: String, enabled: Boolean = true) {
        val normalized = canonicalGenre(genre) ?: return
        mutateSet("preferred_genres", profileId, normalized, enabled)
        if (enabled) mutateSet("disliked_genres", profileId, normalized, false)
    }

    fun dislikeGenre(profileId: String, genre: String, enabled: Boolean = true) {
        val normalized = canonicalGenre(genre) ?: return
        mutateSet("disliked_genres", profileId, normalized, enabled)
        if (enabled) mutateSet("preferred_genres", profileId, normalized, false)
    }

    fun clearItemFeedback(profileId: String, itemId: String) {
        mutateSet("hidden", profileId, itemId, false)
        mutateSet("not_interested", profileId, itemId, false)
    }

    private fun stringSet(kind: String, profileId: String): Set<String> =
        prefs.getStringSet(key(kind, profileId), emptySet()).orEmpty().toSet()

    private fun mutateSet(kind: String, profileId: String, value: String, enabled: Boolean) {
        if (value.isBlank()) return
        val updated = stringSet(kind, profileId).toMutableSet().apply {
            if (enabled) add(value) else remove(value)
        }
        prefs.edit().putStringSet(key(kind, profileId), updated).apply()
    }

    private fun key(kind: String, profileId: String) = "$kind:$profileId"

    private fun canonicalGenre(value: String): String? = value.trim().takeIf { it.isNotBlank() }
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }
}
