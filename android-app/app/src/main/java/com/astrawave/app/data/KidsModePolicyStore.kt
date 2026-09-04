package com.astrawave.app.data

import android.content.Context
import java.time.LocalTime

/**
 * Parent-controlled Kids Mode policy. HouseholdProfileStore owns profile identity/PINs;
 * this store owns content scope and time-of-day rules for a kids profile.
 */
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
        bedtimeEnabled = prefs.getBoolean(key(profileId, "bedtime_enabled"), false),
        bedtimeStartHour = prefs.getInt(key(profileId, "bedtime_start"), 21).coerceIn(0, 23),
        bedtimeEndHour = prefs.getInt(key(profileId, "bedtime_end"), 7).coerceIn(0, 23),
    )

    fun save(profileId: String, policy: Policy) {
        prefs.edit()
            .putString(key(profileId, "age"), policy.ageLevel.name)
            .putBoolean(key(profileId, "approved_only"), policy.approvedOnly)
            .putBoolean(key(profileId, "allow_search"), policy.allowSearch)
            .putBoolean(key(profileId, "bedtime_enabled"), policy.bedtimeEnabled)
            .putInt(key(profileId, "bedtime_start"), policy.bedtimeStartHour.coerceIn(0, 23))
            .putInt(key(profileId, "bedtime_end"), policy.bedtimeEndHour.coerceIn(0, 23))
            .apply()
    }

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

    fun isSearchTermAllowed(profileId: String, query: String): Boolean {
        val policy = load(profileId)
        if (!policy.allowSearch || bedtimeActive(profileId)) return false
        if (!policy.approvedOnly) return true
        val normalized = query.lowercase()
        val blocked = listOf("horror", "slasher", "sex", "porn", "gore", "serial killer", "true crime", "drug", "erotic")
        return blocked.none(normalized::contains)
    }

    private fun key(profileId: String, field: String) = "$profileId:$field"
}
