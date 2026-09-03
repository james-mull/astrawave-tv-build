package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaConnectionStatus
import com.astrawave.app.core.PersonalMediaProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists non-secret personal-media connection metadata only.
 * Authentication tokens/passwords are intentionally excluded and must live in a secure store.
 */
class PersonalMediaStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_personal_media_v1", Context.MODE_PRIVATE)

    fun load(profileId: String): List<PersonalMediaConnection> = decodeAll()
        .filter { it.profileId == profileId }
        .sortedBy { it.name.lowercase() }

    fun save(connection: PersonalMediaConnection) {
        val current = decodeAll().filterNot { it.id == connection.id }
        write(current + connection)
    }

    fun remove(connectionId: String) {
        write(decodeAll().filterNot { it.id == connectionId })
    }

    fun setEnabled(connectionId: String, enabled: Boolean) {
        write(
            decodeAll().map {
                if (it.id == connectionId) it.copy(enabled = enabled) else it
            },
        )
    }

    private fun decodeAll(): List<PersonalMediaConnection> {
        val raw = prefs.getString(KEY_CONNECTIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::decodeConnection)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(connections: List<PersonalMediaConnection>) {
        val array = JSONArray()
        connections.forEach { array.put(encodeConnection(it)) }
        prefs.edit().putString(KEY_CONNECTIONS, array.toString()).apply()
    }

    private fun encodeConnection(connection: PersonalMediaConnection): JSONObject = JSONObject()
        .put("id", connection.id)
        .put("profileId", connection.profileId)
        .put("provider", connection.provider.name)
        .put("name", connection.name)
        .put("serverUrl", connection.serverUrl)
        .put("enabled", connection.enabled)
        .put("status", connection.status.name)
        .put("libraryCount", connection.libraryCount)
        .put("itemCount", connection.itemCount)
        .put("lastSyncEpochMs", connection.lastSyncEpochMs)
        .put("lastError", connection.lastError)

    private fun decodeConnection(obj: JSONObject): PersonalMediaConnection? = runCatching {
        PersonalMediaConnection(
            id = obj.getString("id"),
            profileId = obj.getString("profileId"),
            provider = PersonalMediaProvider.valueOf(obj.getString("provider")),
            name = obj.getString("name"),
            serverUrl = obj.getString("serverUrl"),
            enabled = obj.optBoolean("enabled", true),
            status = runCatching {
                PersonalMediaConnectionStatus.valueOf(obj.optString("status"))
            }.getOrDefault(PersonalMediaConnectionStatus.NOT_CONNECTED),
            libraryCount = obj.optInt("libraryCount"),
            itemCount = obj.optInt("itemCount"),
            lastSyncEpochMs = obj.optLong("lastSyncEpochMs"),
            lastError = obj.optString("lastError").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    companion object {
        private const val KEY_CONNECTIONS = "connections"
    }
}
