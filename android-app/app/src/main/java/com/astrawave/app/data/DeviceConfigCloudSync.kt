package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.ChannelCustomization
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

/** Synchronizes the signed-in user's AstraWave Web Control Center configuration into the TV app. */
class DeviceConfigCloudSync(private val context: Context) {
    private val appContext = context.applicationContext
    private val ready = AstraWaveFirebase.initialize(appContext)
    private val auth: FirebaseAuth? get() = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (ready) FirebaseFirestore.getInstance() else null
    private val syncPrefs = appContext.getSharedPreferences("astrawave_device_cloud_sync", Context.MODE_PRIVATE)

    data class RestoreReport(
        val configVersion: Long,
        val iptvSourcesImported: Int,
        val stremioAddonsApplied: Int,
        val cloudStreamReposApplied: Int,
        val channelCustomizationsApplied: Int = 0,
    )

    fun startLiveSync(profileId: String = "default") {
        val uid = auth?.currentUser?.uid ?: return
        val database = db ?: return
        val key = "$uid:$profileId"
        synchronized(activeListeners) {
            if (activeListeners.containsKey(key)) return
            val settings = database.collection("users").document(uid).collection("settings").document("app")
                .addSnapshotListener { _, error -> if (error == null) restore(profileId) {} }
            val sources = database.collection("users").document(uid).collection("sources")
                .addSnapshotListener { _, error -> if (error == null) restore(profileId) {} }
            val channels = database.collection("users").document(uid).collection("channelCustomizations")
                .addSnapshotListener { _, error -> if (error == null) restore(profileId) {} }
            activeListeners[key] = listOf(settings, sources, channels)
        }
    }

    fun restore(profileId: String = "default", onComplete: (Result<RestoreReport>) -> Unit) {
        val uid = auth?.currentUser?.uid
        val database = db
        if (uid == null || database == null) {
            onComplete(Result.success(RestoreReport(0, 0, 0, 0, 0)))
            return
        }
        val configTask = database.collection("users").document(uid).collection("settings").document("app").get()
        val sourcesTask = database.collection("users").document(uid).collection("sources").get()
        val channelsTask = database.collection("users").document(uid).collection("channelCustomizations").get()
        Tasks.whenAllSuccess<Any>(configTask, sourcesTask, channelsTask).addOnSuccessListener { values ->
            val result = runCatching {
                apply(
                    profileId = profileId,
                    config = values[0] as DocumentSnapshot,
                    sources = values[1] as QuerySnapshot,
                    channelCustomizations = values[2] as QuerySnapshot,
                )
            }
            if (result.isSuccess) startLiveSync(profileId)
            onComplete(result)
        }.addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun apply(
        profileId: String,
        config: DocumentSnapshot,
        sources: QuerySnapshot,
        channelCustomizations: QuerySnapshot,
    ): RestoreReport {
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
                // Xtream credentials are intentionally device-local. IptvSourceStore encrypts the
                // password with Android Keystore; AstraWave does not import a plaintext password
                // from Firestore. Add/test Xtream accounts from the TV/device Source Manager.
                "XTREAM" -> null
                else -> null
            }
        }

        val sourceStore = IptvSourceStore(appContext)
        val previousCloudIds = syncPrefs.getStringSet("$profileId:cloudSourceIds", emptySet()).orEmpty()
        val newCloudIds = importedSources.map { it.id }.toSet()
        val localOnly = sourceStore.load(profileId).filterNot { it.id in previousCloudIds || it.id in newCloudIds }
        sourceStore.save(profileId, localOnly + importedSources)
        syncPrefs.edit().putStringSet("$profileId:cloudSourceIds", newCloudIds).apply()

        val channelStore = ChannelCustomizationStore(appContext)
        val incomingChannelDocs = channelCustomizations.documents.filter { doc ->
            val cloudProfile = doc.getString("profileId")
            cloudProfile.isNullOrBlank() || cloudProfile == profileId
        }
        val incomingChannels = incomingChannelDocs.mapNotNull { doc ->
            val channelId = doc.getString("channelId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            ChannelCustomization(
                channelId = channelId,
                customName = doc.getString("customName")?.takeIf(String::isNotBlank),
                customNumber = doc.getLong("customNumber")?.toInt(),
                customGroup = doc.getString("customGroup")?.takeIf(String::isNotBlank),
                hidden = doc.getBoolean("hidden") ?: false,
                sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0,
                epgIdOverride = doc.getString("epgIdOverride")?.takeIf(String::isNotBlank),
                logoUrlOverride = doc.getString("logoUrlOverride")?.takeIf(String::isNotBlank),
            )
        }
        val previousCloudChannelIds = syncPrefs.getStringSet("$profileId:cloudChannelCustomizationIds", emptySet()).orEmpty()
        val newCloudChannelIds = incomingChannels.map { it.channelId }.toSet()
        previousCloudChannelIds.filterNot { it in newCloudChannelIds }.forEach { channelStore.remove(profileId, it) }
        incomingChannels.forEach { channelStore.save(profileId, it) }
        syncPrefs.edit().putStringSet("$profileId:cloudChannelCustomizationIds", newCloudChannelIds).apply()

        val addons = config.get("addons") as? List<*> ?: emptyList<Any>()
        val stremioStore = StremioAddonStore(appContext)
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
                "stremio" -> if (url != null) runCatching {
                    val addon = stremioStore.install(url)
                    stremioStore.setEnabled(addon.manifest.id, enabled)
                    stremioApplied++
                }
                "cloudstream" -> if (url != null) cloudRepos += CloudStreamRepositoryPreference(
                    id = id.ifBlank { "cloud-${url.hashCode()}" },
                    name = name.ifBlank { "CloudStream Repo" },
                    url = url,
                    enabled = enabled,
                    custom = custom,
                )
            }
        }
        CloudStreamRepositoryPreferenceStore(appContext).save(profileId, cloudRepos)

        val editor = appContext.getSharedPreferences("astrawave_experience", Context.MODE_PRIVATE).edit()
        config.getString("theme")?.let { editor.putString("$profileId:theme", if (it == "dark") "AstraWave" else it) }
        config.getString("homeDensity")?.let { editor.putString("$profileId:density", if (it.equals("compact", true)) "Compact" else "Standard") }
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
            channelCustomizationsApplied = incomingChannels.size,
        )
    }

    companion object {
        private val activeListeners = mutableMapOf<String, List<ListenerRegistration>>()
    }
}
