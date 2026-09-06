package com.astrawave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CloudStreamRepositoryPreference(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val custom: Boolean = false,
)

/** Profile-scoped CloudStream repo choices synced from the AstraWave web control center. */
class CloudStreamRepositoryPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_cloudstream_repos", Context.MODE_PRIVATE)

    fun load(profileId: String): List<CloudStreamRepositoryPreference> {
        val raw = prefs.getString(profileId, null) ?: return CloudStreamRepositoryRegistry.defaults.map {
            CloudStreamRepositoryPreference(it.id, it.name, it.repositoryUrl, it.enabledByDefault, custom = false)
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        CloudStreamRepositoryPreference(
                            id = obj.optString("id"),
                            name = obj.optString("name").ifBlank { "CloudStream Repo" },
                            url = obj.optString("url"),
                            enabled = obj.optBoolean("enabled", false),
                            custom = obj.optBoolean("custom", false),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(profileId: String, repositories: List<CloudStreamRepositoryPreference>) {
        val array = JSONArray()
        repositories.forEach { repo ->
            array.put(
                JSONObject()
                    .put("id", repo.id)
                    .put("name", repo.name)
                    .put("url", repo.url)
                    .put("enabled", repo.enabled)
                    .put("custom", repo.custom),
            )
        }
        prefs.edit().putString(profileId, array.toString()).apply()
    }
}
