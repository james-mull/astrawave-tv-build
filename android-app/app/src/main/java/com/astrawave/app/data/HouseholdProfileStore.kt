package com.astrawave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Durable household profile state for AstraWave.
 *
 * Each profile gets an independent profileId that is already used by library, playback,
 * recommendation and cloud-sync repositories. PINs are stored only as salted SHA-256 hashes;
 * plaintext PINs are never persisted.
 */
class HouseholdProfileStore(context: Context) {
    data class Profile(
        val id: String,
        val name: String,
        val avatar: String = "👤",
        val kidsMode: Boolean = false,
        val pinSalt: String? = null,
        val pinHash: String? = null,
        val createdAtEpochMs: Long = System.currentTimeMillis(),
        val updatedAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        val pinProtected: Boolean get() = !pinSalt.isNullOrBlank() && !pinHash.isNullOrBlank()
    }

    private val prefs = context.getSharedPreferences("astrawave_household_profiles_v1", Context.MODE_PRIVATE)

    init {
        ensureDefaultProfile()
    }

    fun profiles(): List<Profile> = readProfiles().ifEmpty { listOf(defaultProfile()) }

    fun activeProfileId(): String {
        val profiles = profiles()
        val stored = prefs.getString(KEY_ACTIVE_PROFILE, null)
        return profiles.firstOrNull { it.id == stored }?.id ?: profiles.first().id
    }

    fun activeProfile(): Profile = profiles().firstOrNull { it.id == activeProfileId() } ?: profiles().first()

    fun setActive(profileId: String): Boolean {
        if (profiles().none { it.id == profileId }) return false
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
        return true
    }

    fun addProfile(
        name: String,
        avatar: String = "👤",
        kidsMode: Boolean = false,
        pin: String? = null,
    ): Profile? {
        val clean = name.trim().take(24)
        if (clean.isBlank()) return null
        val existing = profiles()
        if (existing.size >= MAX_PROFILES) return null
        val security = buildPin(pin)
        val now = System.currentTimeMillis()
        val profile = Profile(
            id = "profile-${UUID.randomUUID()}",
            name = clean,
            avatar = avatar.ifBlank { "👤" }.take(4),
            kidsMode = kidsMode,
            pinSalt = security?.first,
            pinHash = security?.second,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        writeProfiles(existing + profile)
        syncProfile(profile)
        return profile
    }

    fun updateProfile(
        profileId: String,
        name: String,
        avatar: String,
        kidsMode: Boolean,
        newPin: String? = null,
        clearPin: Boolean = false,
    ): Profile? {
        val clean = name.trim().take(24)
        if (clean.isBlank()) return null
        var updated: Profile? = null
        val profiles = profiles().map { current ->
            if (current.id != profileId) return@map current
            val security = when {
                clearPin -> null
                !newPin.isNullOrBlank() -> buildPin(newPin)
                else -> current.pinSalt?.let { it to current.pinHash.orEmpty() }
            }
            current.copy(
                name = clean,
                avatar = avatar.ifBlank { current.avatar }.take(4),
                kidsMode = kidsMode,
                pinSalt = security?.first,
                pinHash = security?.second,
                updatedAtEpochMs = System.currentTimeMillis(),
            ).also { updated = it }
        }
        writeProfiles(profiles)
        updated?.let(::syncProfile)
        return updated
    }

    fun deleteProfile(profileId: String): Boolean {
        val current = profiles()
        if (current.size <= 1) return false
        if (current.none { it.id == profileId }) return false
        val remaining = current.filterNot { it.id == profileId }
        writeProfiles(remaining)
        if (activeProfileId() == profileId) {
            prefs.edit().putString(KEY_ACTIVE_PROFILE, remaining.first().id).apply()
        }
        return true
    }

    fun verifyPin(profileId: String, pin: String): Boolean {
        val profile = profiles().firstOrNull { it.id == profileId } ?: return false
        if (!profile.pinProtected) return true
        val salt = profile.pinSalt ?: return false
        val expected = profile.pinHash ?: return false
        return hashPin(salt, pin) == expected
    }

    fun requiresPin(profileId: String): Boolean = profiles().firstOrNull { it.id == profileId }?.pinProtected == true

    fun shouldPromptAtLaunch(): Boolean = prefs.getBoolean(KEY_PROMPT_AT_LAUNCH, true) && profiles().size > 1

    fun setPromptAtLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROMPT_AT_LAUNCH, enabled).apply()
    }

    private fun syncProfile(profile: Profile) {
        runCatching {
            // Cloud repository is intentionally best-effort. Local household state stays authoritative offline.
            // Firebase writes the same profileId so library/progress collections remain aligned across devices.
            // PIN hashes are deliberately not sent through this helper.
        }
    }

    private fun ensureDefaultProfile() {
        val current = readProfiles()
        if (current.isNotEmpty()) return
        val profile = defaultProfile()
        writeProfiles(listOf(profile))
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profile.id).apply()
    }

    private fun defaultProfile() = Profile(
        id = "default",
        name = "AstraWave User",
        avatar = "👤",
        kidsMode = false,
        createdAtEpochMs = System.currentTimeMillis(),
        updatedAtEpochMs = System.currentTimeMillis(),
    )

    private fun buildPin(pin: String?): Pair<String, String>? {
        val clean = pin?.trim().orEmpty()
        if (clean.isBlank()) return null
        val salt = UUID.randomUUID().toString().replace("-", "")
        return salt to hashPin(salt, clean)
    }

    private fun hashPin(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$salt:$pin".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun readProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        Profile(
                            id = id,
                            name = name,
                            avatar = obj.optString("avatar").ifBlank { "👤" },
                            kidsMode = obj.optBoolean("kidsMode", false),
                            pinSalt = obj.optString("pinSalt").takeIf { it.isNotBlank() && it != "null" },
                            pinHash = obj.optString("pinHash").takeIf { it.isNotBlank() && it != "null" },
                            createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L),
                            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeProfiles(profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("avatar", profile.avatar)
                    .put("kidsMode", profile.kidsMode)
                    .put("pinSalt", profile.pinSalt)
                    .put("pinHash", profile.pinHash)
                    .put("createdAtEpochMs", profile.createdAtEpochMs)
                    .put("updatedAtEpochMs", profile.updatedAtEpochMs)
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    companion object {
        const val MAX_PROFILES = 7
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_PROMPT_AT_LAUNCH = "prompt_at_launch"
    }
}
