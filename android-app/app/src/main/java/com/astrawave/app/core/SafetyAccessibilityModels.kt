package com.astrawave.app.core

/** Profile-level content, privacy and accessibility preferences. */
data class KidsProfilePolicy(
    val enabled: Boolean = false,
    val maxContentRating: String? = null,
    val allowLiveTv: Boolean = false,
    val allowSports: Boolean = true,
    val allowExternalAddons: Boolean = false,
    val requirePinForProfileExit: Boolean = true,
)

data class PrivacyPreferences(
    val analyticsEnabled: Boolean = false,
    val recommendationLearningEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val localOnlyMode: Boolean = false,
    val savePlaybackHistory: Boolean = true,
    val saveSearchHistory: Boolean = true,
)

data class AccessibilityPreferences(
    val captionsEnabled: Boolean = false,
    val preferredCaptionLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val textScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val screenReaderOptimizedLabels: Boolean = true,
    val largerTvFocusIndicators: Boolean = true,
)

data class ProfileSafetyPreferences(
    val profileId: String,
    val kids: KidsProfilePolicy = KidsProfilePolicy(),
    val privacy: PrivacyPreferences = PrivacyPreferences(),
    val accessibility: AccessibilityPreferences = AccessibilityPreferences(),
)

object ProfileSafetyPolicy {
    fun externalAddonsAllowed(preferences: ProfileSafetyPreferences): Boolean =
        !preferences.kids.enabled || preferences.kids.allowExternalAddons

    fun liveTvAllowed(preferences: ProfileSafetyPreferences): Boolean =
        !preferences.kids.enabled || preferences.kids.allowLiveTv
}
