package com.astrawave.app.data

import com.astrawave.app.core.InstalledAddon
import com.astrawave.app.core.StremioAddonGateway
import com.astrawave.app.core.StremioAddonManifest
import com.astrawave.app.core.StremioCatalogDescriptor
import com.astrawave.app.core.StremioMetaItem
import com.astrawave.app.core.StremioResource
import com.astrawave.app.core.StremioStreamCandidate
import com.astrawave.app.core.StremioSubtitleCandidate
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Dependency-light Stremio protocol client for manifests, catalogs, metadata and subtitles. */
class StremioHttpGateway(
    private val streamAuthorization: (InstalledAddon, JSONObject) -> Boolean = { _, _ -> false },
) : StremioAddonGateway {

    override fun loadManifest(manifestUrl: String): StremioAddonManifest {
        val normalizedManifestUrl = normalizeManifestUrl(manifestUrl)
        val root = JSONObject(SimpleHttp.getText(normalizedManifestUrl))
        val baseUrl = normalizedManifestUrl.substringBeforeLast("/manifest.json").trimEnd('/')

        return StremioAddonManifest(
            id = root.optString("id"),
            name = root.optString("name").ifBlank { root.optString("id") },
            version = root.optString("version"),
            description = root.optString("description"),
            resources = parseResources(root.optJSONArray("resources")),
            catalogs = parseCatalogs(root.optJSONArray("catalogs")),
            types = stringSet(root.optJSONArray("types")),
            baseUrl = baseUrl,
        )
    }

    override fun loadCatalog(
        addon: InstalledAddon,
        catalog: StremioCatalogDescriptor,
        extra: Map<String, String>,
    ): List<StremioMetaItem> {
        val extras = if (extra.isEmpty()) "" else "/${extra.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }}"
        val url = "${addon.manifest.baseUrl}/catalog/${encodePath(catalog.type)}/${encodePath(catalog.id)}$extras.json"
        return parseMetaArray(JSONObject(SimpleHttp.getText(url)).optJSONArray("metas"))
    }

    override fun loadMeta(addon: InstalledAddon, type: String, id: String): StremioMetaItem? {
        val url = "${addon.manifest.baseUrl}/meta/${encodePath(type)}/${encodePath(id)}.json"
        val obj = JSONObject(SimpleHttp.getText(url)).optJSONObject("meta") ?: return null
        return parseMeta(obj)
    }

    override fun loadStreams(addon: InstalledAddon, type: String, id: String): List<StremioStreamCandidate> {
        val url = "${addon.manifest.baseUrl}/stream/${encodePath(type)}/${encodePath(id)}.json"
        val streams = JSONObject(SimpleHttp.getText(url)).optJSONArray("streams") ?: return emptyList()
        return buildList {
            for (index in 0 until streams.length()) {
                val obj = streams.optJSONObject(index) ?: continue
                val behaviorHints = obj.optJSONObject("behaviorHints")?.let(::stringMap).orEmpty()
                add(
                    StremioStreamCandidate(
                        addonId = addon.manifest.id,
                        name = obj.optString("name").ifBlank { addon.manifest.name },
                        title = obj.optString("title").takeIf { it.isNotBlank() },
                        url = obj.optString("url").takeIf { it.isNotBlank() },
                        externalUrl = obj.optString("externalUrl").takeIf { it.isNotBlank() },
                        behaviorHints = behaviorHints,
                        authorized = streamAuthorization(addon, obj),
                    ),
                )
            }
        }
    }

    override fun loadSubtitles(addon: InstalledAddon, type: String, id: String): List<StremioSubtitleCandidate> {
        val url = "${addon.manifest.baseUrl}/subtitles/${encodePath(type)}/${encodePath(id)}.json"
        val subtitles = JSONObject(SimpleHttp.getText(url)).optJSONArray("subtitles") ?: return emptyList()
        return buildList {
            for (index in 0 until subtitles.length()) {
                val obj = subtitles.optJSONObject(index) ?: continue
                val subtitleUrl = obj.optString("url")
                if (subtitleUrl.isBlank()) continue
                add(
                    StremioSubtitleCandidate(
                        id = obj.optString("id").ifBlank { "$index" },
                        language = obj.optString("lang").ifBlank { obj.optString("language") },
                        url = subtitleUrl,
                    ),
                )
            }
        }
    }

    private fun parseResources(array: JSONArray?): Set<StremioResource> {
        if (array == null) return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val value = when (val item = array.opt(index)) {
                    is String -> item
                    is JSONObject -> item.optString("name")
                    else -> ""
                }
                when (value.lowercase()) {
                    "catalog" -> add(StremioResource.CATALOG)
                    "meta" -> add(StremioResource.META)
                    "stream" -> add(StremioResource.STREAM)
                    "subtitles" -> add(StremioResource.SUBTITLES)
                }
            }
        }
    }

    private fun parseCatalogs(array: JSONArray?): List<StremioCatalogDescriptor> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optString("id")
                val type = obj.optString("type")
                if (id.isBlank() || type.isBlank()) continue
                add(
                    StremioCatalogDescriptor(
                        id = id,
                        type = type,
                        name = obj.optString("name").ifBlank { id },
                        extra = parseExtraNames(obj.optJSONArray("extra")),
                    ),
                )
            }
        }
    }

    private fun parseExtraNames(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                val name = when (item) {
                    is String -> item
                    is JSONObject -> item.optString("name")
                    else -> ""
                }
                if (name.isNotBlank()) add(name)
            }
        }
    }

    private fun parseMetaArray(array: JSONArray?): List<StremioMetaItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                parseMeta(obj)?.let(::add)
            }
        }
    }

    private fun parseMeta(obj: JSONObject): StremioMetaItem? {
        val id = obj.optString("id")
        val type = obj.optString("type")
        val name = obj.optString("name")
        if (id.isBlank() || type.isBlank() || name.isBlank()) return null
        return StremioMetaItem(
            id = id,
            type = type,
            name = name,
            posterUrl = obj.optString("poster").takeIf { it.isNotBlank() },
            backgroundUrl = obj.optString("background").takeIf { it.isNotBlank() },
            description = obj.optString("description").takeIf { it.isNotBlank() },
            releaseInfo = obj.optString("releaseInfo").takeIf { it.isNotBlank() },
        )
    }

    private fun stringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun stringMap(obj: JSONObject): Map<String, String> = buildMap {
        obj.keys().forEach { key ->
            val value = obj.opt(key)?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
            put(key, value)
        }
    }

    private fun normalizeManifestUrl(url: String): String {
        val clean = url.trim().trimEnd('/')
        return if (clean.endsWith("manifest.json")) clean else "$clean/manifest.json"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun encodePath(value: String): String = encode(value).replace("+", "%20")
}
