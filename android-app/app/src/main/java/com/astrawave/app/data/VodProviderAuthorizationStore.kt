package com.astrawave.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Profile-scoped authorization policy for customer-owned or customer-authorized VOD providers.
 *
 * A catalog/addon being installed or hardcoded does not imply permission to play its streams.
 * Users (or a future managed-provider flow) must explicitly authorize a provider host/manifest.
 * AstraWave-reviewed public-domain providers remain handled separately by the resolver.
 */
class VodProviderAuthorizationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(profileId: String): List<VodProviderAuthorization> = decode(profileId)
        .sortedWith(compareByDescending<VodProviderAuthorization> { it.enabled }.thenBy { it.name.lowercase() })

    fun save(profileId: String, authorization: VodProviderAuthorization) {
        val normalized = authorization.normalized()
        require(normalized.host.isNotBlank()) { "Authorized VOD provider requires a valid HTTPS host" }
        val current = decode(profileId).filterNot { it.id == normalized.id || it.host.equals(normalized.host, true) }
        write(profileId, current + normalized)
    }

    fun remove(profileId: String, id: String) {
        write(profileId, decode(profileId).filterNot { it.id == id })
    }

    fun setEnabled(profileId: String, id: String, enabled: Boolean) {
        write(profileId, decode(profileId).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun isAuthorized(profileId: String, manifestUrl: String, streamUrl: String): Boolean {
        val manifestHost = httpsHost(manifestUrl) ?: return false
        val streamHost = httpsHost(streamUrl) ?: return false
        return decode(profileId).any { authorization ->
            authorization.enabled &&
                authorization.host.equals(manifestHost, ignoreCase = true) &&
                (authorization.allowedStreamHosts.isEmpty() || authorization.allowedStreamHosts.any { it.equals(streamHost, true) })
        }
    }

    private fun decode(profileId: String): List<VodProviderAuthorization> {
        val raw = prefs.getString(key(profileId), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val host = obj.optString("host").trim().lowercase()
                    if (host.isBlank()) continue
                    val allowed = obj.optJSONArray("allowedStreamHosts")
                    add(
                        VodProviderAuthorization(
                            id = obj.optString("id").ifBlank { host.replace(Regex("[^a-z0-9]+"), "-") },
                            name = obj.optString("name").ifBlank { host },
                            host = host,
                            manifestUrl = obj.optString("manifestUrl").takeIf(String::isNotBlank),
                            allowedStreamHosts = buildSet {
                                if (allowed != null) for (j in 0 until allowed.length()) {
                                    allowed.optString(j).trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
                                }
                            },
                            enabled = obj.optBoolean("enabled", true),
                            authorizationLabel = obj.optString("authorizationLabel").ifBlank { "Customer-authorized provider" },
                            updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(profileId: String, values: List<VodProviderAuthorization>) {
        val array = JSONArray()
        values.map(VodProviderAuthorization::normalized).forEach { value ->
            array.put(
                JSONObject()
                    .put("id", value.id)
                    .put("name", value.name)
                    .put("host", value.host)
                    .put("manifestUrl", value.manifestUrl)
                    .put("allowedStreamHosts", JSONArray(value.allowedStreamHosts.toList()))
                    .put("enabled", value.enabled)
                    .put("authorizationLabel", value.authorizationLabel)
                    .put("updatedAtEpochMs", value.updatedAtEpochMs),
            )
        }
        prefs.edit().putString(key(profileId), array.toString()).apply()
    }

    private fun key(profileId: String) = "providers_${profileId.ifBlank { "default" }}"

    companion object {
        private const val PREFS = "astrawave_vod_provider_authorizations_v1"

        fun httpsHost(url: String): String? = runCatching {
            Uri.parse(url.trim()).takeIf { it.scheme.equals("https", true) }?.host?.lowercase()?.takeIf(String::isNotBlank)
        }.getOrNull()
    }
}

data class VodProviderAuthorization(
    val id: String,
    val name: String,
    val host: String,
    val manifestUrl: String? = null,
    val allowedStreamHosts: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val authorizationLabel: String = "Customer-authorized provider",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    fun normalized(): VodProviderAuthorization {
        val normalizedHost = host.trim().lowercase()
        val normalizedAllowed = allowedStreamHosts.map(String::trim).filter(String::isNotBlank).map(String::lowercase).toSet()
        return copy(
            id = id.trim().ifBlank { normalizedHost.replace(Regex("[^a-z0-9]+"), "-") },
            name = name.trim().ifBlank { normalizedHost },
            host = normalizedHost,
            manifestUrl = manifestUrl?.trim()?.takeIf(String::isNotBlank),
            allowedStreamHosts = normalizedAllowed,
            authorizationLabel = authorizationLabel.trim().ifBlank { "Customer-authorized provider" },
        )
    }
}
