package com.astrawave.app

import android.content.Context

/**
 * Debug-only persistence for Phase 2 device verification.
 *
 * Results are keyed by explicit device class so phone/tablet/Android TV/Fire TV
 * checks cannot accidentally satisfy one another. Main visual verification,
 * modal-focus verification, and premium sports verification are all bound to the
 * exact commit being reviewed. This store is never compiled into release builds
 * because it lives under src/debug.
 */
internal class Phase2QaVerificationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isVerified(deviceClass: String): Boolean {
        val visualCommit = verifiedCommit(deviceClass)
        val modalCommit = modalVerifiedCommit(deviceClass)
        val sportsCommit = sportsVerifiedCommit(deviceClass)
        return preferences.getBoolean(verifiedKey(deviceClass), false) &&
            preferences.getBoolean(modalVerifiedKey(deviceClass), false) &&
            preferences.getBoolean(sportsVerifiedKey(deviceClass), false) &&
            !visualCommit.isNullOrBlank() &&
            visualCommit == modalCommit &&
            visualCommit == sportsCommit
    }

    fun verifiedCommit(deviceClass: String): String? =
        preferences.getString(commitKey(deviceClass), null)

    fun isModalVerified(deviceClass: String): Boolean =
        preferences.getBoolean(modalVerifiedKey(deviceClass), false)

    fun modalVerifiedCommit(deviceClass: String): String? =
        preferences.getString(modalCommitKey(deviceClass), null)

    fun isSportsVerified(deviceClass: String): Boolean =
        preferences.getBoolean(sportsVerifiedKey(deviceClass), false)

    fun sportsVerifiedCommit(deviceClass: String): String? =
        preferences.getString(sportsCommitKey(deviceClass), null)

    fun markVerified(deviceClass: String, commitSha: String) {
        // A Phase 2 device result is valid only if modal and sports visual QA were
        // both completed on this same device class and exact commit.
        val modalReady = isModalVerified(deviceClass) && modalVerifiedCommit(deviceClass) == commitSha
        val sportsReady = isSportsVerified(deviceClass) && sportsVerifiedCommit(deviceClass) == commitSha
        if (!modalReady || !sportsReady) return
        preferences.edit()
            .putBoolean(verifiedKey(deviceClass), true)
            .putString(commitKey(deviceClass), commitSha)
            .apply()
    }

    fun markModalVerified(deviceClass: String, commitSha: String) {
        preferences.edit()
            .putBoolean(modalVerifiedKey(deviceClass), true)
            .putString(modalCommitKey(deviceClass), commitSha)
            .apply()
    }

    fun markSportsVerified(deviceClass: String, commitSha: String) {
        preferences.edit()
            .putBoolean(sportsVerifiedKey(deviceClass), true)
            .putString(sportsCommitKey(deviceClass), commitSha)
            .apply()
    }

    fun clear(deviceClass: String) {
        preferences.edit()
            .remove(verifiedKey(deviceClass))
            .remove(commitKey(deviceClass))
            .remove(modalVerifiedKey(deviceClass))
            .remove(modalCommitKey(deviceClass))
            .remove(sportsVerifiedKey(deviceClass))
            .remove(sportsCommitKey(deviceClass))
            .apply()
    }

    private fun verifiedKey(deviceClass: String) = "verified_${deviceClass.normalized()}"
    private fun commitKey(deviceClass: String) = "commit_${deviceClass.normalized()}"
    private fun modalVerifiedKey(deviceClass: String) = "modal_verified_${deviceClass.normalized()}"
    private fun modalCommitKey(deviceClass: String) = "modal_commit_${deviceClass.normalized()}"
    private fun sportsVerifiedKey(deviceClass: String) = "sports_verified_${deviceClass.normalized()}"
    private fun sportsCommitKey(deviceClass: String) = "sports_commit_${deviceClass.normalized()}"

    private fun String.normalized(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private companion object {
        const val PREFS_NAME = "astrawave_phase2_device_qa"
    }
}
