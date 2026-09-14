package com.astrawave.app.data

import android.content.Context

/** Shared per-profile playback policy used by VOD, Live TV, Guide and the player UI. */
enum class PlaybackPreset {
    AUTO,
    BEST_QUALITY,
    FASTEST_START,
    DATA_SAVER,
    HDR_PREFERRED,
    SURROUND_PREFERRED,
    DEBRID_ONLY,
    DIRECT_ONLY,
}

data class PlaybackPreferences(
    val preset: PlaybackPreset = PlaybackPreset.AUTO,
    val preferredAudioLanguage: String = "auto",
    val preferredSubtitleLanguage: String = "auto",
    val preferForcedSubtitles: Boolean = true,
    val preferHearingImpairedSubtitles: Boolean = false,
    val subtitlesEnabledByDefault: Boolean = false,
    val autoNextEnabled: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val skipRecapEnabled: Boolean = true,
    val skipCreditsEnabled: Boolean = true,
    val preferSurround: Boolean = true,
    val preferHdr: Boolean = true,
    val preferDebrid: Boolean = true,
    val guideDensity: Int = 2,
    val uiScalePercent: Int = 100,
)

class PlaybackPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_playback_preferences", Context.MODE_PRIVATE)

    fun load(profileId: String = "default"): PlaybackPreferences {
        val prefix = keyPrefix(profileId)
        val preset = runCatching {
            PlaybackPreset.valueOf(prefs.getString("${prefix}preset", PlaybackPreset.AUTO.name) ?: PlaybackPreset.AUTO.name)
        }.getOrDefault(PlaybackPreset.AUTO)
        return PlaybackPreferences(
            preset = preset,
            preferredAudioLanguage = prefs.getString("${prefix}audio_language", "auto") ?: "auto",
            preferredSubtitleLanguage = prefs.getString("${prefix}subtitle_language", "auto") ?: "auto",
            preferForcedSubtitles = prefs.getBoolean("${prefix}forced_subtitles", true),
            preferHearingImpairedSubtitles = prefs.getBoolean("${prefix}hearing_impaired_subtitles", false),
            subtitlesEnabledByDefault = prefs.getBoolean("${prefix}subtitles_default", false),
            autoNextEnabled = prefs.getBoolean("${prefix}auto_next", true),
            skipIntroEnabled = prefs.getBoolean("${prefix}skip_intro", true),
            skipRecapEnabled = prefs.getBoolean("${prefix}skip_recap", true),
            skipCreditsEnabled = prefs.getBoolean("${prefix}skip_credits", true),
            preferSurround = prefs.getBoolean("${prefix}prefer_surround", true),
            preferHdr = prefs.getBoolean("${prefix}prefer_hdr", true),
            preferDebrid = prefs.getBoolean("${prefix}prefer_debrid", true),
            guideDensity = prefs.getInt("${prefix}guide_density", 2).coerceIn(1, 3),
            uiScalePercent = prefs.getInt("${prefix}ui_scale", 100).coerceIn(85, 125),
        )
    }

    fun save(profileId: String = "default", value: PlaybackPreferences) {
        val prefix = keyPrefix(profileId)
        prefs.edit()
            .putString("${prefix}preset", value.preset.name)
            .putString("${prefix}audio_language", value.preferredAudioLanguage)
            .putString("${prefix}subtitle_language", value.preferredSubtitleLanguage)
            .putBoolean("${prefix}forced_subtitles", value.preferForcedSubtitles)
            .putBoolean("${prefix}hearing_impaired_subtitles", value.preferHearingImpairedSubtitles)
            .putBoolean("${prefix}subtitles_default", value.subtitlesEnabledByDefault)
            .putBoolean("${prefix}auto_next", value.autoNextEnabled)
            .putBoolean("${prefix}skip_intro", value.skipIntroEnabled)
            .putBoolean("${prefix}skip_recap", value.skipRecapEnabled)
            .putBoolean("${prefix}skip_credits", value.skipCreditsEnabled)
            .putBoolean("${prefix}prefer_surround", value.preferSurround)
            .putBoolean("${prefix}prefer_hdr", value.preferHdr)
            .putBoolean("${prefix}prefer_debrid", value.preferDebrid)
            .putInt("${prefix}guide_density", value.guideDensity.coerceIn(1, 3))
            .putInt("${prefix}ui_scale", value.uiScalePercent.coerceIn(85, 125))
            .apply()
    }

    fun update(profileId: String = "default", transform: (PlaybackPreferences) -> PlaybackPreferences): PlaybackPreferences {
        val next = transform(load(profileId))
        save(profileId, next)
        return next
    }

    private fun keyPrefix(profileId: String): String = "profile_${profileId.ifBlank { "default" }}_"
}
