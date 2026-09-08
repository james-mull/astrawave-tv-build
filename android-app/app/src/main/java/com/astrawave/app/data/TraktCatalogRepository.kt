package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TraktCatalogRepository(
    private val baseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trim().trimEnd('/'),
) {
    enum class Mode(val wire: String) { TRENDING("trending"), POPULAR("popular"), ANTICIPATED("anticipated") }
    enum class Kind(val wire: String) { MOVIES("movies"), SHOWS("shows") }

    fun available(): Boolean = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

    fun load(mode: Mode, kind: Kind): List<AstraWaveMetadataGateway.Item> {
        if (!available()) return emptyList()
        val modeParam = URLEncoder.encode(mode.wire, StandardCharsets.UTF_8.name())
        val kindParam = URLEncoder.encode(kind.wire, StandardCharsets.UTF_8.name())
        val connection = URL("$baseUrl/api/astrawave/trakt-catalogs?mode=$modeParam&kind=$kindParam").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "AstraWave/1.0")
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Trakt catalog HTTP $code")
        val root = JSONObject(text)
        val array = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("tmdbId").takeIf(String::isNotBlank)
                    ?: item.optString("imdbId").takeIf(String::isNotBlank)
                    ?: item.optString("id").takeIf(String::isNotBlank)
                    ?: continue
                val title = item.optString("title").takeIf(String::isNotBlank) ?: continue
                add(
                    AstraWaveMetadataGateway.Item(
                        id = id,
                        type = if (kind == Kind.SHOWS) "series" else "movie",
                        name = title,
                        description = null,
                        posterUrl = item.optString("posterUrl").takeIf(String::isNotBlank),
                        backdropUrl = item.optString("backdropUrl").takeIf(String::isNotBlank),
                        releaseInfo = item.optInt("year").takeIf { it > 0 }?.toString(),
                    ),
                )
            }
        }
    }
}
