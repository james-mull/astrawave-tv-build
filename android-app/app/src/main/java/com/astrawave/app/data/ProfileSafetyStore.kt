package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.AccessibilityPreferences
import com.astrawave.app.core.KidsProfilePolicy
import com.astrawave.app.core.PrivacyPreferences
import com.astrawave.app.core.ProfileSafetyPreferences
import org.json.JSONObject

/** Persists privacy-safe profile policy, kids restrictions, and accessibility preferences. */
class ProfileSafetyStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_profile_safety_v1", Context.MODE_PRIVATE)

    fun load(profileId: String = "default"): ProfileSafetyPreferences {
        val raw = prefs.getString(key(profileId), null) ?: return ProfileSafetyPreferences(profileId = profileId)
        return runCatching {
            val root = JSONObject(raw)
            val kids = root.optJSONObject("kids") ?: JSONObject()
            val privacy = root.optJSONObject("privacy") ?: JSONObject()
            val accessibility = root.optJSONObject("accessibility") ?: JSONObject()
            ProfileSafetyPreferences(
                profileId = profileId,
                kids = KidsProfilePolicy(
                    enabled = kids.optBoolean("enabled", false),
                    maxContentRating = kids.optString("maxContentRating").takeIf { it.isNotBlank() },
                    allowLiveTv = kids.optBoolean("allowLiveTv", false),
                    allowSports = kids.optBoolean("allowSports", true),
                    allowExternalAddons = kids.optBoolean("allowExternalAddons", false),
                    requirePinForProfileExit = kids.optBoolean("requirePinForProfileExit", false),
                ),
                privacy = PrivacyPreferences(
                    analyticsEnabled = privacy.optBoolean("analyticsEnabled", false),
                    recommendationLearningEnabled = privacy.optBoolean("recommendationLearningEnabled", true),
                    cloudSyncEnabled = privacy.optBoolean("cloudSyncEnabled", false),
                    localOnlyMode = privacy.optBoolean("localOnlyMode", false),
                    savePlaybackHistory = privacy.optBoolean("savePlaybackHistory", true),
                    saveSearchHistory = privacy.optBoolean("saveSearchHistory", true),
                ),
                accessibility = AccessibilityPreferences(
                    captionsEnabled = accessibility.optBoolean("captionsEnabled", false),
                    preferredCaptionLanguage = accessibility.optString("preferredCaptionLanguage").takeIf { it.isNotBlank() },
                    preferredAudioLanguage = accessibility.optString("preferredAudioLanguage").takeIf { it.isNotBlank() },
                    textScale = accessibility.optDouble("textScale", 1.0).toFloat().coerceIn(0.85f, 1.5f),
                    highContrast = accessibility.optBoolean("highContrast", false),
                    reduceMotion = accessibility.optBoolean("reduceMotion", false),
                    screenReaderOptimizedLabels = accessibility.optBoolean("screenReaderOptimizedLabels", true),
                    largerTvFocusIndicators = accessibility.optBoolean("largerTvFocusIndicators", true),
                ),
            )
        }.getOrDefault(ProfileSafetyPreferences(profileId = profileId))
    }

    fun save(preferences: ProfileSafetyPreferences) {
        val root = JSONObject()
            .put("kids", JSONObject()
                .put("enabled", preferences.kids.enabled)
                .put("maxContentRating", preferences.kids.maxContentRating)
                .put("allowLiveTv", preferences.kids.allowLiveTv)
                .put("allowSports", preferences.kids.allowSports)
                .put("allowExternalAddons", preferences.kids.allowExternalAddons)
                .put("requirePinForProfileExit", preferences.kids.requirePinForProfileExit))
            .put("privacy", JSONObject()
                .put("analyticsEnabled", preferences.privacy.analyticsEnabled)
                .put("recommendationLearningEnabled", preferences.privacy.recommendationLearningEnabled)
                .put("cloudSyncEnabled", preferences.privacy.cloudSyncEnabled)
                .put("localOnlyMode", preferences.privacy.localOnlyMode)
                .put("savePlaybackHistory", preferences.privacy.savePlaybackHistory)
                .put("saveSearchHistory", preferences.privacy.saveSearchHistory))
            .put("accessibility", JSONObject()
                .put("captionsEnabled", preferences.accessibility.captionsEnabled)
                .put("preferredCaptionLanguage", preferences.accessibility.preferredCaptionLanguage)
                .put("preferredAudioLanguage", preferences.accessibility.preferredAudioLanguage)
                .put("textScale", preferences.accessibility.textScale.toDouble())
                .put("highContrast", preferences.accessibility.highContrast)
                .put("reduceMotion", preferences.accessibility.reduceMotion)
                .put("screenReaderOptimizedLabels", preferences.accessibility.screenReaderOptimizedLabels)
                .put("largerTvFocusIndicators", preferences.accessibility.largerTvFocusIndicators))
        prefs.edit().putString(key(preferences.profileId), root.toString()).apply()
    }

    fun reset(profileId: String = "default") {
        prefs.edit().remove(key(profileId)).apply()
    }

    private fun key(profileId: String) = "profile_$profileId"
}
