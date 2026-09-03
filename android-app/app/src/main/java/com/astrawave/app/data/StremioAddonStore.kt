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

    fun load(profileId: String): List<InstalledAddon> = decodeAll()
        .filter { addon -> addon.enabledProfileIds.isEmpty() || profileId in addon.enabledProfileIds }
        .sortedBy { it.sortOrder }

    fun loadAll(): List<InstalledAddon> = decodeAll().sortedBy { it.sortOrder }

    fun save(addon: InstalledAddon) {
        val current = decodeAll().filterNot { it.manifest.id == addon.manifest.id }
        write(current + addon)
    }

    fun remove(addonId: String) {
        write(decodeAll().filterNot { it.manifest.id == addonId })
    }

    fun setEnabled(addonId: String, enabled: Boolean) {
        write(decodeAll().map { if (it.manifest.id == addonId) it.copy(enabled = enabled) else it })
    }

    fun install(manifestUrl: String, gateway: StremioHttpGateway = StremioHttpGateway()): InstalledAddon {
        val manifest = gateway.loadManifest(manifestUrl)
        require(manifest.id.isNotBlank()) { "Addon manifest is missing an id" }
        require(manifest.name.isNotBlank()) { "Addon manifest is missing a name" }
        val existing = decodeAll()
        val old = existing.firstOrNull { it.manifest.id == manifest.id }
        val installed = InstalledAddon(
            manifestUrl = normalizeManifestUrl(manifestUrl),
            manifest = manifest,
            enabled = old?.enabled ?: true,
            enabledProfileIds = old?.enabledProfileIds.orEmpty(),
            pinnedCatalogIds = old?.pinnedCatalogIds.orEmpty(),
            hiddenCatalogIds = old?.hiddenCatalogIds.orEmpty(),
            sortOrder = old?.sortOrder ?: existing.size,
        )
        save(installed)
        return installed
    }

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

    companion object {
        private const val KEY_ADDONS = "installed"
    }
}
