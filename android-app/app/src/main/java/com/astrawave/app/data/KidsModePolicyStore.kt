package com.astrawave.app.data

import android.content.Context
import java.time.LocalTime

/** Parent-controlled Kids Mode policy, title approvals and age-rating rules. */
class KidsModePolicyStore(context: Context) {
    enum class AgeLevel(val label: String) {
        LITTLE_KIDS("Little Kids"),
        KIDS("Kids"),
        OLDER_KIDS("Older Kids"),
    }

    data class Policy(
        val ageLevel: AgeLevel = AgeLevel.KIDS,
        val approvedOnly: Boolean = false,
        val allowSearch: Boolean = true,
        val allowUnrated: Boolean = false,
        val bedtimeEnabled: Boolean = false,
        val bedtimeStartHour: Int = 21,
        val bedtimeEndHour: Int = 7,
    )

    private val prefs = context.getSharedPreferences("astrawave_kids_mode_policy_v1", Context.MODE_PRIVATE)

    fun load(profileId: String): Policy = Policy(
        ageLevel = runCatching {
            AgeLevel.valueOf(prefs.getString(key(profileId, "age"), AgeLevel.KIDS.name) ?: AgeLevel.KIDS.name)
        }.getOrDefault(AgeLevel.KIDS),
        approvedOnly = prefs.getBoolean(key(profileId, "approved_only"), false),
        allowSearch = prefs.getBoolean(key(profileId, "allow_search"), true),
        allowUnrated = prefs.getBoolean(key(profileId, "allow_unrated"), false),
        bedtimeEnabled = prefs.getBoolean(key(profileId, "bedtime_enabled"), false),
        bedtimeStartHour = prefs.getInt(key(profileId, "bedtime_start"), 21).coerceIn(0, 23),
        bedtimeEndHour = prefs.getInt(key(profileId, "bedtime_end"), 7).coerceIn(0, 23),
    )

    fun save(profileId: String, policy: Policy) {
        prefs.edit()
            .putString(key(profileId, "age"), policy.ageLevel.name)
            .putBoolean(key(profileId, "approved_only"), policy.approvedOnly)
            .putBoolean(key(profileId, "allow_search"), policy.allowSearch)
            .putBoolean(key(profileId, "allow_unrated"), policy.allowUnrated)
            .putBoolean(key(profileId, "bedtime_enabled"), policy.bedtimeEnabled)
            .putInt(key(profileId, "bedtime_start"), policy.bedtimeStartHour.coerceIn(0, 23))
            .putInt(key(profileId, "bedtime_end"), policy.bedtimeEndHour.coerceIn(0, 23))
            .apply()
    }

    fun approveTitle(profileId: String, itemId: String, approved: Boolean = true) {
        mutateSet(profileId, "approved_titles", itemId, approved)
        if (approved) mutateSet(profileId, "blocked_titles", itemId, false)
    }

    fun blockTitle(profileId: String, itemId: String, blocked: Boolean = true) {
        mutateSet(profileId, "blocked_titles", itemId, blocked)
        if (blocked) mutateSet(profileId, "approved_titles", itemId, false)
    }

    fun approvedTitleIds(profileId: String): Set<String> = stringSet(profileId, "approved_titles")
    fun blockedTitleIds(profileId: String): Set<String> = stringSet(profileId, "blocked_titles")
    fun isApproved(profileId: String, itemId: String): Boolean = itemId in approvedTitleIds(profileId)
    fun isBlocked(profileId: String, itemId: String): Boolean = itemId in blockedTitleIds(profileId)

    fun bedtimeActive(profileId: String, now: LocalTime = LocalTime.now()): Boolean {
        val policy = load(profileId)
        if (!policy.bedtimeEnabled) return false
        val hour = now.hour
        val start = policy.bedtimeStartHour
        val end = policy.bedtimeEndHour
        return if (start == end) true
        else if (start < end) hour in start until end
        else hour >= start || hour < end
    }

    fun allowedGenres(profileId: String): List<String> = when (load(profileId).ageLevel) {
        AgeLevel.LITTLE_KIDS -> listOf("Animation", "Family")
        AgeLevel.KIDS -> listOf("Animation", "Family", "Adventure", "Fantasy", "Comedy")
        AgeLevel.OLDER_KIDS -> listOf("Animation", "Family", "Adventure", "Fantasy", "Comedy", "Science-Fiction")
    }

    fun ratingAllowed(profileId: String, rating: String?, itemId: String? = null): Boolean {
        if (!itemId.isNullOrBlank()) {
            if (isBlocked(profileId, itemId)) return false
            if (isApproved(profileId, itemId)) return true
        }
        val policy = load(profileId)
        if (policy.approvedOnly) return false
        val normalized = canonicalRating(rating)
        if (normalized == null) return policy.allowUnrated
        val allowed = when (policy.ageLevel) {
            AgeLevel.LITTLE_KIDS -> setOf("G", "TV-Y", "TV-Y7", "TV-G")
            AgeLevel.KIDS -> setOf("G", "PG", "TV-Y", "TV-Y7", "TV-G", "TV-PG")
            AgeLevel.OLDER_KIDS -> setOf("G", "PG", "PG-13", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14")
        }
        return normalized in allowed
    }

    fun isSearchTermAllowed(profileId: String, query: String): Boolean {
        val policy = load(profileId)
        if (!policy.allowSearch || bedtimeActive(profileId) || policy.approvedOnly) return false
        val normalized = query.lowercase()
        val blocked = listOf("horror", "slasher", "sex", "porn", "gore", "serial killer", "true crime", "drug", "erotic")
        return blocked.none(normalized::contains)
    }

    private fun canonicalRating(value: String?): String? {
        val rating = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        return when (rating) {
            "TVY" -> "TV-Y"
            "TVY7" -> "TV-Y7"
            "TVG" -> "TV-G"
            "TVPG" -> "TV-PG"
            "TV14" -> "TV-14"
            "PG13" -> "PG-13"
            else -> rating
        }
    }

    private fun stringSet(profileId: String, field: String): Set<String> =
        prefs.getStringSet(key(profileId, field), emptySet()).orEmpty().toSet()

    private fun mutateSet(profileId: String, field: String, value: String, enabled: Boolean) {
        if (value.isBlank()) return
        val updated = stringSet(profileId, field).toMutableSet().apply {
            if (enabled) add(value) else remove(value)
        }
        prefs.edit().putStringSet(key(profileId, field), updated).apply()
    }

    private fun key(profileId: String, field: String) = "$profileId:$field"
}
