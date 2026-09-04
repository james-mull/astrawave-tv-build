package com.astrawave.app.data

import org.json.JSONObject

data class StremioEpisode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val released: String? = null,
    val overview: String? = null,
    val thumbnail: String? = null,
)

class StremioSeriesEpisodeRepository {
    fun load(seriesId: String): List<StremioEpisode> {
        if (seriesId.isBlank()) return emptyList()
        val hosts = listOf("https://v3-cinemeta.strem.io", "https://cinemeta.strem.io")
        hosts.forEach { host ->
            val result = runCatching {
                val root = JSONObject(SimpleHttp.getText("$host/meta/series/$seriesId.json"))
                val videos = root.optJSONObject("meta")?.optJSONArray("videos") ?: return@runCatching emptyList()
                buildList {
                    for (index in 0 until videos.length()) {
                        val obj = videos.optJSONObject(index) ?: continue
                        val id = obj.optString("id")
                        if (id.isBlank()) continue
                        add(
                            StremioEpisode(
                                id = id,
                                title = obj.optString("title").ifBlank { obj.optString("name").ifBlank { "Episode ${index + 1}" } },
                                season = obj.optInt("season", 0),
                                episode = obj.optInt("episode", index + 1),
                                released = obj.optString("released").takeIf { it.isNotBlank() },
                                overview = obj.optString("overview").ifBlank { obj.optString("description") }.takeIf { it.isNotBlank() },
                                thumbnail = obj.optString("thumbnail").ifBlank { obj.optString("poster") }.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}
