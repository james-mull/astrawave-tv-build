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
    val reviewed: Boolean = false,
    val allowPluginExecution: Boolean = false,
    val extensionHints: List<String> = emptyList(),
) {
    val executionEligible: Boolean
        get() = enabled && reviewed && allowPluginExecution && !custom
}

/** Profile-scoped CloudStream repo choices synced from the AstraWave web control center. */
class CloudStreamRepositoryPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_cloudstream_repos", Context.MODE_PRIVATE)

    fun load(profileId: String): List<CloudStreamRepositoryPreference> {
        val raw = prefs.getString(profileId, null) ?: return CloudStreamRepositoryRegistry.defaults.map {
            CloudStreamRepositoryPreference(
                id = it.id,
                name = it.name,
                url = it.repositoryUrl,
                enabled = it.enabledByDefault,
                custom = false,
                reviewed = it.reviewed,
                allowPluginExecution = false,
                extensionHints = it.extensionHints,
            )
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val definition = CloudStreamRepositoryRegistry.defaults.firstOrNull { it.id == id }
                    val custom = obj.optBoolean("custom", false)
                    val reviewed = definition?.reviewed == true && !custom
                    add(
                        CloudStreamRepositoryPreference(
                            id = id,
                            name = obj.optString("name").ifBlank { definition?.name ?: "CloudStream Repo" },
                            url = obj.optString("url").ifBlank { definition?.repositoryUrl.orEmpty() },
                            enabled = obj.optBoolean("enabled", definition?.enabledByDefault ?: false),
                            custom = custom,
                            reviewed = reviewed,
                            allowPluginExecution = reviewed && obj.optBoolean("allowPluginExecution", false),
                            extensionHints = definition?.extensionHints.orEmpty(),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun executionEligible(profileId: String): List<CloudStreamRepositoryPreference> =
        load(profileId).filter { it.executionEligible }

    fun save(profileId: String, repositories: List<CloudStreamRepositoryPreference>) {
        val array = JSONArray()
        repositories.forEach { repo ->
            val definition = CloudStreamRepositoryRegistry.defaults.firstOrNull { it.id == repo.id }
            val reviewed = definition?.reviewed == true && !repo.custom
            array.put(
                JSONObject()
                    .put("id", repo.id)
                    .put("name", repo.name)
                    .put("url", repo.url)
                    .put("enabled", repo.enabled)
                    .put("custom", repo.custom)
                    .put("reviewed", reviewed)
                    .put("allowPluginExecution", reviewed && repo.allowPluginExecution),
            )
        }
        prefs.edit().putString(profileId, array.toString()).apply()
    }
}
