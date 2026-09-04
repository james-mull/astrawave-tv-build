package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Zero-configuration metadata gateway for AstraWave.
 *
 * Primary path: AstraWave backend proxy (which holds provider credentials server-side).
 * Fallback path: Cinemeta metadata/catalog endpoints that do not require a user API key.
 *
 * This class intentionally never accepts or persists a TMDB credential from the user.
 */
class AstraWaveMetadataGateway(
    private val apiBaseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/'),
) {
    data class Item(
        val id: String,
        val type: String,
        val name: String,
        val description: String? = null,
        val posterUrl: String? = null,
        val backdropUrl: String? = null,
        val releaseInfo: String? = null,
    )

    enum class Catalog(val backendPath: String, val cinemetaPath: String) {
        TRENDING_MOVIES("/v1/catalog/movies/trending", "/catalog/movie/top.json"),
        TRENDING_SERIES("/v1/catalog/series/trending", "/catalog/series/top.json"),
    }

    fun load(catalog: Catalog): List<Item> {
        if (apiBaseUrl.isNotBlank()) {
            runCatching { loadFromBackend(catalog) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return loadFromCinemeta(catalog)
    }

    fun search(query: String): List<Item> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        if (apiBaseUrl.isNotBlank()) {
            runCatching {
                val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                parseBackendArray(SimpleHttp.getText("$apiBaseUrl/v1/search?q=$encoded"))
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val movie = runCatching {
            parseCinemeta(JSONObject(SimpleHttp.getText("https://v3-cinemeta.strem.io/catalog/movie/top/search=$encoded.json")))
        }.getOrDefault(emptyList())
        val series = runCatching {
            parseCinemeta(JSONObject(SimpleHttp.getText("https://v3-cinemeta.strem.io/catalog/series/top/search=$encoded.json")))
        }.getOrDefault(emptyList())

        return (movie + series).distinctBy { "${it.type}:${it.id}" }
    }

    private fun loadFromBackend(catalog: Catalog): List<Item> =
        parseBackendArray(SimpleHttp.getText("$apiBaseUrl${catalog.backendPath}"))

    private fun loadFromCinemeta(catalog: Catalog): List<Item> =
        parseCinemeta(JSONObject(SimpleHttp.getText("https://v3-cinemeta.strem.io${catalog.cinemetaPath}")))

    private fun parseBackendArray(json: String): List<Item> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("title").ifBlank { item.optString("name") }
                if (id.isBlank() || name.isBlank()) continue
                add(
                    Item(
                        id = id,
                        type = item.optString("kind").ifBlank { item.optString("type") },
                        name = name,
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        posterUrl = item.optString("posterUrl").takeIf { it.isNotBlank() },
                        backdropUrl = item.optString("backdropUrl").takeIf { it.isNotBlank() },
                        releaseInfo = item.optString("subtitle").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    private fun parseCinemeta(root: JSONObject): List<Item> {
        val metas = root.optJSONArray("metas") ?: return emptyList()
        return buildList {
            for (i in 0 until metas.length()) {
                val meta = metas.optJSONObject(i) ?: continue
                val id = meta.optString("id")
                val name = meta.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    Item(
                        id = id,
                        type = meta.optString("type"),
                        name = name,
                        description = meta.optString("description").takeIf { it.isNotBlank() },
                        posterUrl = meta.optString("poster").takeIf { it.isNotBlank() },
                        backdropUrl = meta.optString("background").takeIf { it.isNotBlank() },
                        releaseInfo = meta.optString("releaseInfo").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }
}
