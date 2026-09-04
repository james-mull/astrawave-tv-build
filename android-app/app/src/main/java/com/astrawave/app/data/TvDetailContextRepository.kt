package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** TV-detail enrichment for lists, networks, related shows, creators, cast and connected universes. */
class TvDetailContextRepository(
    private val apiBaseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/'),
) {
    data class ListMembership(
        val title: String,
        val category: String,
        val reason: String,
        val query: String? = null,
        val genre: String? = null,
    )

    data class ShowItem(
        val id: String,
        val title: String,
        val firstAirDate: String? = null,
        val posterUrl: String? = null,
        val backdropUrl: String? = null,
    )

    data class PersonShows(val name: String, val shows: List<ShowItem>)
    data class Network(val id: String, val name: String, val logoUrl: String? = null)
    data class Universe(val name: String, val query: String? = null, val parts: List<ShowItem> = emptyList())
    data class Related(
        val similar: List<ShowItem> = emptyList(),
        val creator: PersonShows? = null,
        val actor: PersonShows? = null,
    )
    data class Context(
        val lists: List<ListMembership> = emptyList(),
        val networks: List<Network> = emptyList(),
        val universe: Universe? = null,
        val related: Related = Related(),
    )

    fun load(seriesId: Long): Context {
        if (seriesId <= 0L || apiBaseUrl.isBlank()) return Context()
        val encoded = URLEncoder.encode(seriesId.toString(), StandardCharsets.UTF_8.name())
        val root = JSONObject(SimpleHttp.getText("$apiBaseUrl/tv-context/$encoded"))
        return Context(
            lists = parseLists(root),
            networks = parseNetworks(root),
            universe = parseUniverse(root.optJSONObject("universe")),
            related = parseRelated(root.optJSONObject("related")),
        )
    }

    private fun parseLists(root: JSONObject): List<ListMembership> {
        val array = root.optJSONArray("lists") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                add(ListMembership(
                    title = title,
                    category = item.optString("category").ifBlank { "AstraWave TV List" },
                    reason = item.optString("reason"),
                    query = item.optString("query").takeIf { it.isNotBlank() && it != "null" },
                    genre = item.optString("genre").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }
    }

    private fun parseNetworks(root: JSONObject): List<Network> {
        val array = root.optJSONArray("networks") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(Network(
                    id = item.optString("id"),
                    name = name,
                    logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }
    }

    private fun parseUniverse(obj: JSONObject?): Universe? {
        obj ?: return null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        return Universe(
            name = name,
            query = obj.optString("query").takeIf { it.isNotBlank() && it != "null" },
            parts = parseShows(obj.optJSONArray("parts")),
        )
    }

    private fun parseRelated(obj: JSONObject?): Related {
        obj ?: return Related()
        return Related(
            similar = parseShows(obj.optJSONArray("similar")),
            creator = parsePerson(obj.optJSONObject("creator")),
            actor = parsePerson(obj.optJSONObject("actor")),
        )
    }

    private fun parsePerson(obj: JSONObject?): PersonShows? {
        obj ?: return null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        return PersonShows(name, parseShows(obj.optJSONArray("shows")))
    }

    private fun parseShows(array: org.json.JSONArray?): List<ShowItem> {
        array ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                add(ShowItem(
                    id = id,
                    title = title,
                    firstAirDate = item.optString("firstAirDate").takeIf { it.isNotBlank() && it != "null" },
                    posterUrl = item.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
                    backdropUrl = item.optString("backdropUrl").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }
    }
}
