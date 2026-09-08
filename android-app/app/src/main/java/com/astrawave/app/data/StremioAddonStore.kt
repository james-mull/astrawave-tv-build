package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.InstalledAddon
import com.astrawave.app.core.StremioAddonManifest
import com.astrawave.app.core.StremioCatalogDescriptor
import com.astrawave.app.core.StremioResource
import org.json.JSONArray
import org.json.JSONObject

/** Persistent, profile-aware storage for user-installed Stremio-compatible addons. */
class StremioAddonStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_stremio_addons_v1", Context.MODE_PRIVATE)

    /** Local-only read. Network bootstrap is deliberately explicit and must run off the UI thread. */
    fun load(profileId: String): List<InstalledAddon> = decodeAll()
        .filter { addon -> addon.enabledProfileIds.isEmpty() || profileId in addon.enabledProfileIds }
        .sortedBy { it.sortOrder }

    /** Local-only read. Safe to call from Compose/main-thread rendering. */
    fun loadAll(): List<InstalledAddon> = decodeAll().sortedBy { it.sortOrder }

    @Synchronized
    fun save(addon: InstalledAddon) {
        val current = decodeAll().filterNot { it.manifest.id == addon.manifest.id }
        write(current + addon)
    }

    @Synchronized
    fun remove(addonId: String) {
        val existing = decodeAll()
        val removed = existing.firstOrNull { it.manifest.id == addonId }
        if (removed != null && normalizeManifestUrl(removed.manifestUrl) in normalizedDefaultManifestUrls) {
            val dismissed = dismissedDefaultUrls().toMutableSet()
            dismissed += normalizeManifestUrl(removed.manifestUrl)
            prefs.edit().putStringSet(KEY_DISMISSED_DEFAULT_URLS, dismissed).apply()
        }
        write(existing.filterNot { it.manifest.id == addonId })
    }

    @Synchronized
    fun setEnabled(addonId: String, enabled: Boolean) {
        write(decodeAll().map { if (it.manifest.id == addonId) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun install(manifestUrl: String, gateway: StremioHttpGateway = StremioHttpGateway()): InstalledAddon {
        val normalizedUrl = normalizeManifestUrl(manifestUrl)
        val manifest = gateway.loadManifest(normalizedUrl)
        require(manifest.id.isNotBlank()) { "Addon manifest is missing an id" }
        require(manifest.name.isNotBlank()) { "Addon manifest is missing a name" }
        val existing = decodeAll()
        val old = existing.firstOrNull { it.manifest.id == manifest.id }
        val installed = InstalledAddon(
            manifestUrl = normalizedUrl,
            manifest = manifest,
            enabled = old?.enabled ?: true,
            enabledProfileIds = old?.enabledProfileIds.orEmpty(),
            pinnedCatalogIds = old?.pinnedCatalogIds.orEmpty(),
            hiddenCatalogIds = old?.hiddenCatalogIds.orEmpty(),
            sortOrder = old?.sortOrder ?: existing.size,
        )
        if (normalizedUrl in normalizedDefaultManifestUrls) {
            val dismissed = dismissedDefaultUrls().toMutableSet().apply { remove(normalizedUrl) }
            prefs.edit().putStringSet(KEY_DISMISSED_DEFAULT_URLS, dismissed).apply()
        }
        save(installed)
        return installed
    }

    /**
     * Retryable default bootstrap. Call this on Dispatchers.IO/background threads only.
     *
     * The previous v2 seeder marked itself complete even when every network request failed.
     * v3 ignores that stale success bit, installs only missing defaults, preserves explicit
     * user removals, and only reports completion when every non-dismissed default is present.
     */
    @Synchronized
    fun bootstrapDefaults(gateway: StremioHttpGateway = StremioHttpGateway()): BootstrapReport {
        val dismissed = dismissedDefaultUrls()
        val before = decodeAll()
        val installedUrls = before.map { normalizeManifestUrl(it.manifestUrl) }.toMutableSet()
        val desired = normalizedDefaultManifestUrls.filterNot { it in dismissed }
        val missing = desired.filterNot { it in installedUrls }

        var installedNow = 0
        val failures = linkedMapOf<String, String>()
        missing.forEach { manifestUrl ->
            runCatching { install(manifestUrl, gateway) }
                .onSuccess {
                    installedUrls += manifestUrl
                    installedNow += 1
                }
                .onFailure { error -> failures[manifestUrl] = error.message ?: error::class.java.simpleName }
        }

        val remaining = desired.filterNot { it in installedUrls }
        prefs.edit()
            .putBoolean(KEY_DEFAULTS_COMPLETE_V3, remaining.isEmpty())
            .putLong(KEY_DEFAULTS_LAST_ATTEMPT_MS, System.currentTimeMillis())
            .apply()

        return BootstrapReport(
            installedNow = installedNow,
            totalInstalled = decodeAll().size,
            remainingDefaultCount = remaining.size,
            failures = failures,
        )
    }

    fun defaultsComplete(): Boolean = prefs.getBoolean(KEY_DEFAULTS_COMPLETE_V3, false)

    private fun dismissedDefaultUrls(): Set<String> =
        prefs.getStringSet(KEY_DISMISSED_DEFAULT_URLS, emptySet()).orEmpty().map(::normalizeManifestUrl).toSet()

    private fun decodeAll(): List<InstalledAddon> {
        val raw = prefs.getString(KEY_ADDONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) array.optJSONObject(i)?.let(::decodeAddon)?.let(::add)
            }
        }.getOrDefault(emptyList())
    }

    private fun write(addons: List<InstalledAddon>) {
        val array = JSONArray()
        addons.sortedBy { it.sortOrder }.forEach { array.put(encodeAddon(it)) }
        prefs.edit().putString(KEY_ADDONS, array.toString()).apply()
    }

    private fun encodeAddon(addon: InstalledAddon): JSONObject = JSONObject()
        .put("manifestUrl", addon.manifestUrl)
        .put("enabled", addon.enabled)
        .put("enabledProfileIds", JSONArray(addon.enabledProfileIds.toList()))
        .put("pinnedCatalogIds", JSONArray(addon.pinnedCatalogIds))
        .put("hiddenCatalogIds", JSONArray(addon.hiddenCatalogIds.toList()))
        .put("sortOrder", addon.sortOrder)
        .put("manifest", encodeManifest(addon.manifest))

    private fun decodeAddon(obj: JSONObject): InstalledAddon? = runCatching {
        InstalledAddon(
            manifestUrl = obj.getString("manifestUrl"),
            manifest = decodeManifest(obj.getJSONObject("manifest")),
            enabled = obj.optBoolean("enabled", true),
            enabledProfileIds = stringSet(obj.optJSONArray("enabledProfileIds")),
            pinnedCatalogIds = stringList(obj.optJSONArray("pinnedCatalogIds")),
            hiddenCatalogIds = stringSet(obj.optJSONArray("hiddenCatalogIds")),
            sortOrder = obj.optInt("sortOrder"),
        )
    }.getOrNull()

    private fun encodeManifest(manifest: StremioAddonManifest): JSONObject {
        val resources = JSONArray().apply { manifest.resources.forEach { put(it.name) } }
        val types = JSONArray().apply { manifest.types.forEach(::put) }
        val catalogs = JSONArray().apply {
            manifest.catalogs.forEach { catalog ->
                put(
                    JSONObject()
                        .put("id", catalog.id)
                        .put("type", catalog.type)
                        .put("name", catalog.name)
                        .put("extra", JSONArray(catalog.extra)),
                )
            }
        }
        return JSONObject()
            .put("id", manifest.id)
            .put("name", manifest.name)
            .put("version", manifest.version)
            .put("description", manifest.description)
            .put("baseUrl", manifest.baseUrl)
            .put("resources", resources)
            .put("types", types)
            .put("catalogs", catalogs)
    }

    private fun decodeManifest(obj: JSONObject): StremioAddonManifest = StremioAddonManifest(
        id = obj.getString("id"),
        name = obj.getString("name"),
        version = obj.optString("version"),
        description = obj.optString("description"),
        resources = stringSet(obj.optJSONArray("resources")).mapNotNull { runCatching { StremioResource.valueOf(it) }.getOrNull() }.toSet(),
        catalogs = decodeCatalogs(obj.optJSONArray("catalogs")),
        types = stringSet(obj.optJSONArray("types")),
        baseUrl = obj.getString("baseUrl"),
    )

    private fun decodeCatalogs(array: JSONArray?): List<StremioCatalogDescriptor> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    StremioCatalogDescriptor(
                        id = obj.optString("id"),
                        type = obj.optString("type"),
                        name = obj.optString("name"),
                        extra = stringList(obj.optJSONArray("extra")),
                    ),
                )
            }
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun stringSet(array: JSONArray?): Set<String> = stringList(array).toSet()

    private fun normalizeManifestUrl(url: String): String {
        val clean = url.trim().trimEnd('/')
        return if (clean.endsWith("manifest.json")) clean else "$clean/manifest.json"
    }

    data class BootstrapReport(
        val installedNow: Int,
        val totalInstalled: Int,
        val remainingDefaultCount: Int,
        val failures: Map<String, String>,
    ) {
        val complete: Boolean get() = remainingDefaultCount == 0
    }

    companion object {
        private const val KEY_ADDONS = "installed"
        private const val KEY_DEFAULTS_COMPLETE_V3 = "hardcoded_defaults_complete_v3"
        private const val KEY_DEFAULTS_LAST_ATTEMPT_MS = "hardcoded_defaults_last_attempt_v3"
        private const val KEY_DISMISSED_DEFAULT_URLS = "dismissed_default_manifest_urls_v3"

        const val USA_TV_MANIFEST = "https://848b3516657c-usatv.baby-beamup.club/manifest.json"
        const val NETFLIX_CATALOG_MANIFEST = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibSxwbXAscGNwLGhsdSxjcnUsbmZrLGN0cyxkcGUsc2hhLGlxaSxiYm86OlVTOjE3ODg3MzE0NjkxNzQ6MDowOlVT/manifest.json"

        val REVIEWED_DEFAULT_MANIFESTS = listOf(
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://v3-channels.strem.io/manifest.json",
            "https://watchhub.strem.io/manifest.json",
            "https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
        )

        val CUSTOMER_HARDCODED_MANIFESTS = listOf(
            USA_TV_MANIFEST,
            NETFLIX_CATALOG_MANIFEST,
        )

        val HARDCODED_DEFAULT_MANIFESTS = REVIEWED_DEFAULT_MANIFESTS + CUSTOMER_HARDCODED_MANIFESTS
        private val normalizedDefaultManifestUrls = HARDCODED_DEFAULT_MANIFESTS
            .map { url -> url.trim().trimEnd('/').let { if (it.endsWith("manifest.json")) it else "$it/manifest.json" } }
            .toSet()
    }
}
