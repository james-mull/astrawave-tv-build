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
) {
    /** Local-only mode is authoritative and cannot leak state into cloud or telemetry paths. */
    fun effective(): PrivacyPreferences = if (localOnlyMode) {
        copy(
            analyticsEnabled = false,
            cloudSyncEnabled = false,
        )
    } else {
        this
    }
}

data class AccessibilityPreferences(
    val captionsEnabled: Boolean = false,
    val preferredCaptionLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val textScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val screenReaderOptimizedLabels: Boolean = true,
    val largerTvFocusIndicators: Boolean = true,
) {
    fun normalized(): AccessibilityPreferences = copy(
        textScale = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
        preferredCaptionLanguage = preferredCaptionLanguage?.trim()?.takeIf { it.isNotBlank() },
        preferredAudioLanguage = preferredAudioLanguage?.trim()?.takeIf { it.isNotBlank() },
    )

    companion object {
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.6f
    }
}

data class ProfileSafetyPreferences(
    val profileId: String,
    val kids: KidsProfilePolicy = KidsProfilePolicy(),
    val privacy: PrivacyPreferences = PrivacyPreferences(),
    val accessibility: AccessibilityPreferences = AccessibilityPreferences(),
) {
    fun normalized(): ProfileSafetyPreferences = copy(
        profileId = profileId.trim(),
        privacy = privacy.effective(),
        accessibility = accessibility.normalized(),
    )
}

object ProfileSafetyPolicy {
    fun externalAddonsAllowed(preferences: ProfileSafetyPreferences): Boolean =
        !preferences.kids.enabled || preferences.kids.allowExternalAddons

    fun liveTvAllowed(preferences: ProfileSafetyPreferences): Boolean =
        !preferences.kids.enabled || preferences.kids.allowLiveTv

    fun cloudSyncAllowed(preferences: ProfileSafetyPreferences): Boolean =
        preferences.privacy.effective().cloudSyncEnabled

    fun analyticsAllowed(preferences: ProfileSafetyPreferences): Boolean =
        preferences.privacy.effective().analyticsEnabled
}
