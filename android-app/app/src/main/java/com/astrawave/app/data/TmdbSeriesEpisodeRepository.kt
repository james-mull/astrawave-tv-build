package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Loads native TMDB season/episode metadata through AstraWave's server-side metadata proxy. */
class TmdbSeriesEpisodeRepository(
    private val apiBaseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/'),
) {
    fun load(seriesId: Long): List<StremioEpisode> {
        if (seriesId <= 0L || apiBaseUrl.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(seriesId.toString(), StandardCharsets.UTF_8.name())
        val root = JSONObject(SimpleHttp.getText("$apiBaseUrl/v1/tv/$encoded/episodes"))
        val episodes = root.optJSONArray("episodes") ?: return emptyList()
        return buildList {
            for (index in 0 until episodes.length()) {
                val item = episodes.optJSONObject(index) ?: continue
                val season = item.optInt("season", 0)
                val episode = item.optInt("episode", 0)
                val title = item.optString("title").ifBlank { "Episode $episode" }
                if (season <= 0 || episode <= 0) continue
                add(
                    StremioEpisode(
                        id = "tmdbtv:$seriesId:$season:$episode",
                        title = title,
                        season = season,
                        episode = episode,
                        released = item.optString("released").takeIf { it.isNotBlank() && it != "null" },
                        overview = item.optString("overview").takeIf { it.isNotBlank() && it != "null" },
                        thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
                    ),
                )
            }
        }.sortedWith(compareBy<StremioEpisode> { it.season }.thenBy { it.episode })
    }
}
