package com.astrawave.app

import android.content.Context

/**
 * Debug-only persistence for Phase 2 device verification.
 *
 * Results are keyed by explicit device class so phone/tablet/Android TV/Fire TV
 * checks cannot accidentally satisfy one another. This store is never compiled
 * into release builds because it lives under src/debug.
 */
internal class Phase2QaVerificationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isVerified(deviceClass: String): Boolean =
        preferences.getBoolean(verifiedKey(deviceClass), false)

    fun verifiedCommit(deviceClass: String): String? =
        preferences.getString(commitKey(deviceClass), null)

    fun markVerified(deviceClass: String, commitSha: String) {
        preferences.edit()
            .putBoolean(verifiedKey(deviceClass), true)
            .putString(commitKey(deviceClass), commitSha)
            .apply()
    }

    fun clear(deviceClass: String) {
        preferences.edit()
            .remove(verifiedKey(deviceClass))
            .remove(commitKey(deviceClass))
            .apply()
    }

    private fun verifiedKey(deviceClass: String) = "verified_${deviceClass.normalized()}"
    private fun commitKey(deviceClass: String) = "commit_${deviceClass.normalized()}"

    private fun String.normalized(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private companion object {
        const val PREFS_NAME = "astrawave_phase2_device_qa"
    }
}
