package com.astrawave.app.data

import android.content.Context

/**
 * Device-local consent for recording user-managed IPTV sources.
 * Nothing is authorized by default: the user must explicitly opt a source in after confirming
 * their provider permits local recording. Cloud/source imports never grant this permission.
 */
class DvrAuthorizationStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_dvr_authorization_v1", Context.MODE_PRIVATE)

    fun allowed(profileId: String, sourceId: String): Boolean =
        prefs.getBoolean(idKey(profileId, sourceId), false)

    fun allowedLabel(profileId: String, sourceName: String): Boolean =
        prefs.getBoolean(labelKey(profileId, sourceName), false)

    fun setAllowed(profileId: String, sourceId: String, allowed: Boolean, sourceName: String? = null) {
        val editor = prefs.edit().putBoolean(idKey(profileId, sourceId), allowed)
        sourceName?.takeIf(String::isNotBlank)?.let { editor.putBoolean(labelKey(profileId, it), allowed) }
        editor.apply()
    }

    fun clear(profileId: String, sourceId: String, sourceName: String? = null) {
        val editor = prefs.edit().remove(idKey(profileId, sourceId))
        sourceName?.takeIf(String::isNotBlank)?.let { editor.remove(labelKey(profileId, it)) }
        editor.apply()
    }

    private fun idKey(profileId: String, sourceId: String) =
        "id:${clean(profileId)}:${clean(sourceId)}"

    private fun labelKey(profileId: String, sourceName: String) =
        "label:${clean(profileId)}:${clean(sourceName.lowercase())}"

    private fun clean(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
}
