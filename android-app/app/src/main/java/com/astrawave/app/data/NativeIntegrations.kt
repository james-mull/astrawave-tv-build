package com.astrawave.app.data

import android.util.Xml
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Small dependency-light integrations used by the first AstraWave Android build. */
data class M3uChannel(
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val tvgId: String? = null,
)

data class XmlTvProgramme(
    val channelId: String,
    val title: String,
    val start: String,
    val stop: String,
)

data class PodcastEpisode(
    val title: String,
    val mediaUrl: String?,
    val published: String? = null,
)

object M3uParser {
    private val attributeRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<M3uChannel> {
        val result = mutableListOf<M3uChannel>()
        var pendingInfo: String? = null
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = line
                line.startsWith("#") -> Unit
                pendingInfo != null -> {
                    val info = pendingInfo!!
                    val attrs = attributeRegex.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
                    val name = info.substringAfterLast(',').trim().ifBlank { "Channel" }
                    result += M3uChannel(
                        name = name,
                        url = line,
                        group = attrs["group-title"],
                        logo = attrs["tvg-logo"],
                        tvgId = attrs["tvg-id"],
                    )
                    pendingInfo = null
                }
            }
        }
        return result
    }
}

object XtreamEndpoints {
    private fun cleanServer(server: String) = server.trim().trimEnd('/')
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    fun playerApi(server: String, username: String, password: String): String =
        "${cleanServer(server)}/player_api.php?username=${enc(username)}&password=${enc(password)}"

    fun liveM3u(server: String, username: String, password: String): String =
        "${cleanServer(server)}/get.php?username=${enc(username)}&password=${enc(password)}&type=m3u_plus&output=m3u8"

    fun xmlTv(server: String, username: String, password: String): String =
        "${cleanServer(server)}/xmltv.php?username=${enc(username)}&password=${enc(password)}"
}

object XmlTvParser {
    fun parse(input: InputStream, maxItems: Int = 50_000): List<XmlTvProgramme> {
        val parser = Xml.newPullParser().apply { setInput(input, null) }
        val items = mutableListOf<XmlTvProgramme>()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && items.size < maxItems) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel") ?: ""
                val start = parser.getAttributeValue(null, "start") ?: ""
                val stop = parser.getAttributeValue(null, "stop") ?: ""
                var title = "Program"
                val depth = parser.depth
                while (true) {
                    event = parser.next()
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "title") {
                        title = parser.nextText().ifBlank { "Program" }
                    }
                    if (event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "programme" && parser.depth == depth) break
                }
                items += XmlTvProgramme(channel, title, start, stop)
            }
            event = parser.next()
        }
        return items
    }
}

object PodcastRssParser {
    fun parse(input: InputStream, maxItems: Int = 500): List<PodcastEpisode> {
        val parser = Xml.newPullParser().apply { setInput(input, null) }
        val episodes = mutableListOf<PodcastEpisode>()
        var event = parser.eventType
        var inItem = false
        var title: String? = null
        var published: String? = null
        var media: String? = null
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && episodes.size < maxItems) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "item" -> { inItem = true; title = null; published = null; media = null }
                    "title" -> if (inItem) title = parser.nextText()
                    "pubdate" -> if (inItem) published = parser.nextText()
                    "enclosure" -> if (inItem) media = parser.getAttributeValue(null, "url")
                }
            } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name.equals("item", true)) {
                episodes += PodcastEpisode(title ?: "Episode", media, published)
                inItem = false
            }
            event = parser.next()
        }
        return episodes
    }
}

object SimpleHttp {
    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "AstraWave/0.2")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.inputStream.use { input ->
            BufferedReader(InputStreamReader(input)).use { return it.readText() }
        }
    }
}

data class TmdbItem(
    val id: Long,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String? = null,
)

class TmdbClient(private val bearerToken: String) {
    private val base = "https://api.themoviedb.org/3"
    private val headers get() = mapOf("Authorization" to "Bearer $bearerToken", "accept" to "application/json")

    fun trendingMovies(): List<TmdbItem> = parseResults(SimpleHttp.getText("$base/trending/movie/day", headers), "movie")
    fun trendingShows(): List<TmdbItem> = parseResults(SimpleHttp.getText("$base/trending/tv/day", headers), "tv")
    fun search(query: String): List<TmdbItem> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return parseResults(SimpleHttp.getText("$base/search/multi?query=$encoded&include_adult=false", headers), null)
    }

    private fun parseResults(json: String, fallbackMediaType: String?): List<TmdbItem> {
        val array = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").ifBlank { obj.optString("name") }
                if (title.isBlank()) continue
                add(
                    TmdbItem(
                        id = obj.optLong("id"),
                        title = title,
                        overview = obj.optString("overview"),
                        posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                        backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                        mediaType = obj.optString("media_type").takeIf { it.isNotBlank() } ?: fallbackMediaType,
                    )
                )
            }
        }
    }
}
