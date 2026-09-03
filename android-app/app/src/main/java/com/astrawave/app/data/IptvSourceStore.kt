package com.astrawave.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceStatus
import com.astrawave.app.core.IptvSourceType
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Profile-scoped persistence for My IPTV sources.
 *
 * Non-secret source metadata lives in private app preferences. Xtream passwords are encrypted with
 * an Android Keystore AES/GCM key before they are persisted. This also migrates the original single
 * M3U/Xtream settings into the multi-source model on first load.
 */
class IptvSourceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacySettings = AppSettingsStore(context)

    fun load(profileId: String): List<IptvSource> {
        val raw = prefs.getString(key(profileId), null)
        if (raw.isNullOrBlank()) return migrateLegacy(profileId)

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    add(fromJson(obj, profileId))
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(profileId: String, sources: List<IptvSource>) {
        val array = JSONArray()
        sources.filter { it.profileId == profileId }.forEach { array.put(toJson(it)) }
        prefs.edit().putString(key(profileId), array.toString()).apply()
    }

    fun upsert(source: IptvSource) {
        val current = load(source.profileId).toMutableList()
        val index = current.indexOfFirst { it.id == source.id }
        if (index >= 0) current[index] = source else current += source
        save(source.profileId, current)
    }

    fun delete(profileId: String, sourceId: String) {
        save(profileId, load(profileId).filterNot { it.id == sourceId })
    }

    private fun migrateLegacy(profileId: String): List<IptvSource> {
        val migrated = buildList {
            legacySettings.m3uUrl?.takeIf { it.isNotBlank() }?.let { url ->
                add(
                    IptvSource(
                        id = "legacy-m3u",
                        profileId = profileId,
                        name = "My M3U",
                        type = IptvSourceType.M3U,
                        m3uUrl = url,
                    ),
                )
            }

            val server = legacySettings.xtreamServer?.takeIf { it.isNotBlank() }
            val username = legacySettings.xtreamUsername?.takeIf { it.isNotBlank() }
            val password = legacySettings.xtreamPassword?.takeIf { it.isNotBlank() }
            if (server != null && username != null && password != null) {
                add(
                    IptvSource(
                        id = "legacy-xtream",
                        profileId = profileId,
                        name = "My Xtream",
                        type = IptvSourceType.XTREAM,
                        xtreamServer = server,
                        xtreamUsername = username,
                        xtreamPassword = password,
                    ),
                )
            }
        }

        if (migrated.isNotEmpty()) save(profileId, migrated)
        return migrated
    }

    private fun toJson(source: IptvSource): JSONObject = JSONObject().apply {
        put("id", source.id)
        put("name", source.name)
        put("type", source.type.name)
        put("enabled", source.enabled)
        put("priority", source.priority)
        putNullable("m3uUrl", source.m3uUrl)
        putNullable("xtreamServer", source.xtreamServer)
        putNullable("xtreamUsername", source.xtreamUsername)
        putNullable("xtreamPasswordEncrypted", source.xtreamPassword?.let(::encrypt))
        putNullable("xmlTvUrl", source.xmlTvUrl)
        put("status", source.status.name)
        put("channelCount", source.channelCount)
        put("guideProgramCount", source.guideProgramCount)
        put("lastCheckedEpochMs", source.lastCheckedEpochMs)
        putNullable("lastError", source.lastError)
    }

    private fun fromJson(obj: JSONObject, profileId: String): IptvSource = IptvSource(
        id = obj.getString("id"),
        profileId = profileId,
        name = obj.optString("name").ifBlank { "IPTV Source" },
        type = runCatching { IptvSourceType.valueOf(obj.getString("type")) }.getOrDefault(IptvSourceType.M3U),
        enabled = obj.optBoolean("enabled", true),
        priority = obj.optInt("priority", 20),
        m3uUrl = obj.optNullable("m3uUrl"),
        xtreamServer = obj.optNullable("xtreamServer"),
        xtreamUsername = obj.optNullable("xtreamUsername"),
        xtreamPassword = obj.optNullable("xtreamPasswordEncrypted")?.let { runCatching { decrypt(it) }.getOrNull() },
        xmlTvUrl = obj.optNullable("xmlTvUrl"),
        status = runCatching { IptvSourceStatus.valueOf(obj.optString("status")) }.getOrDefault(IptvSourceStatus.NOT_TESTED),
        channelCount = obj.optInt("channelCount", 0),
        guideProgramCount = obj.optInt("guideProgramCount", 0),
        lastCheckedEpochMs = obj.optLong("lastCheckedEpochMs", 0L),
        lastError = obj.optNullable("lastError"),
    )

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$ciphertext"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted IPTV credential" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun key(profileId: String) = "sources_$profileId"

    private fun JSONObject.putNullable(name: String, value: String?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.optNullable(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private companion object {
        const val PREFS_NAME = "astrawave_iptv_sources"
        const val KEY_ALIAS = "astrawave_iptv_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
