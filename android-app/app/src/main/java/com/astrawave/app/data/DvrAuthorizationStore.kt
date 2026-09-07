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
        prefs.getBoolean(key(profileId, sourceId), false)

    fun setAllowed(profileId: String, sourceId: String, allowed: Boolean) {
        prefs.edit().putBoolean(key(profileId, sourceId), allowed).apply()
    }

    fun clear(profileId: String, sourceId: String) {
        prefs.edit().remove(key(profileId, sourceId)).apply()
    }

    private fun key(profileId: String, sourceId: String) =
        "${profileId.replace(':', '_')}:${sourceId.replace(':', '_')}"
}
