package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

/**
 * Pulls the signed-in user's AstraWave web control-center configuration into the TV app.
 * Secrets are not invented or shared publicly; this reads the user's private Firestore paths.
 */
class DeviceConfigCloudSync(private val context: Context) {
    private val ready = AstraWaveFirebase.initialize(context)
    private val auth: FirebaseAuth? get() = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (ready) FirebaseFirestore.getInstance() else null

    data class RestoreReport(
        val configVersion: Long,
        val iptvSourcesImported: Int,
        val stremioAddonsApplied: Int,
        val cloudStreamReposApplied: Int,
    )

    fun restore(profileId: String = "default", onComplete: (Result<RestoreReport>) -> Unit) {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            onComplete(Result.success(RestoreReport(0, 0, 0, 0)))
            return
        }

        val configTask = database.collection("users").document(uid).collection("settings").document("app").get()
        val sourcesTask = database.collection("users").document(uid).collection("sources").get()

        Tasks.whenAllSuccess<Any>(configTask, sourcesTask)
            .addOnSuccessListener { values ->
                runCatching {
                    val config = values[0] as DocumentSnapshot
                    val sources = values[1] as QuerySnapshot
                    apply(profileId, config, sources)
                }.also(onComplete)
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun apply(profileId: String, config: DocumentSnapshot, sources: QuerySnapshot): RestoreReport {
        val importedSources = sources.documents.mapNotNull { doc ->
            val enabled = doc.getBoolean("enabled") ?: true
            val type = doc.getString("type").orEmpty().uppercase()
            val rawConfig = doc.get("config") as? Map<*, *> ?: emptyMap<Any, Any>()
            when (type) {
                "M3U", "PUBLIC" -> {
                    val url = rawConfig["m3uUrl"]?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
                    IptvSource(
                        id = doc.id,
                        profileId = profileId,
                        name = doc.getString("name").orEmpty().ifBlank { "Cloud IPTV" },
                        type = IptvSourceType.M3U,
                        enabled = enabled,
                        priority = (doc.getLong("priority") ?: 20L).toInt(),
                        m3uUrl = url,
                        xmlTvUrl = rawConfig["xmlTvUrl"]?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                    )
                }
                "XTREAM" -> {
                    val server = rawConfig["server"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val username = rawConfig["username"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val password = rawConfig["password"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    IptvSource(
                        id = doc.id,
                        profileId = profileId,
                        name = doc.getString("name").orEmpty().ifBlank { "Cloud Xtream" },
                        type = IptvSourceType.XTREAM,
                        enabled = enabled,
                        priority = (doc.getLong("priority") ?: 20L).toInt(),
                        xtreamServer = server,
                        xtreamUsername = username,
                        xtreamPassword = password,
                        xmlTvUrl = rawConfig["xmlTvUrl"]?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                    )
                }
                else -> null
            }
        }

        if (importedSources.isNotEmpty()) {
            val store = IptvSourceStore(context)
            val localOnly = store.load(profileId).filterNot { local -> importedSources.any { it.id == local.id } }
            store.save(profileId, localOnly + importedSources)
        }

        val addons = config.get("addons") as? List<*> ?: emptyList<Any>()
        val stremioStore = StremioAddonStore(context)
        var stremioApplied = 0
        val cloudRepos = mutableListOf<CloudStreamRepositoryPreference>()

        addons.forEach { raw ->
            val map = raw as? Map<*, *> ?: return@forEach
            val id = map["id"]?.toString().orEmpty()
            val name = map["name"]?.toString().orEmpty()
            val kind = map["kind"]?.toString().orEmpty()
            val url = map["url"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val enabled = map["enabled"] as? Boolean ?: false
            val custom = map["custom"] as? Boolean ?: false
            when (kind) {
                "stremio" -> if (url != null) {
                    runCatching {
                        val addon = stremioStore.install(url)
                        stremioStore.setEnabled(addon.manifest.id, enabled)
                        stremioApplied++
                    }
                }
                "cloudstream" -> if (url != null) {
                    cloudRepos += CloudStreamRepositoryPreference(
                        id = id.ifBlank { "cloud-${url.hashCode()}" },
                        name = name.ifBlank { "CloudStream Repo" },
                        url = url,
                        enabled = enabled,
                        custom = custom,
                    )
                }
            }
        }

        if (cloudRepos.isNotEmpty()) {
            CloudStreamRepositoryPreferenceStore(context).save(profileId, cloudRepos)
        }

        val experience = context.getSharedPreferences("astrawave_experience", Context.MODE_PRIVATE)
        val editor = experience.edit()
        config.getString("theme")?.let { editor.putString("$profileId:theme", if (it == "dark") "AstraWave" else it) }
        config.getString("homeDensity")?.let { density ->
            editor.putString("$profileId:density", if (density.equals("compact", true)) "Compact" else "Standard")
        }
        config.getBoolean("autoplayTrailers")?.let { editor.putBoolean("$profileId:autoplayTrailers", it) }
        config.getBoolean("aiDiscovery")?.let { editor.putBoolean("$profileId:aiDiscovery", it) }
        config.getString("preferredLanguage")?.let { editor.putString("$profileId:preferredLanguage", it) }
        config.getString("preferredRegion")?.let { editor.putString("$profileId:preferredRegion", it) }
        config.getString("activeLiveSource")?.let { editor.putString("$profileId:activeLiveSource", it) }
        editor.apply()

        return RestoreReport(
            configVersion = config.getLong("version") ?: 0L,
            iptvSourcesImported = importedSources.size,
            stremioAddonsApplied = stremioApplied,
            cloudStreamReposApplied = cloudRepos.size,
        )
    }
}
