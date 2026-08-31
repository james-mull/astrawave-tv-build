package com.astrawave.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SportsEvent(
    val id: String,
    val name: String,
    val league: String?,
    val sport: String?,
    val date: String?,
    val time: String?,
)

class TheSportsDbClient(private val apiKey: String = "123") {
    fun eventsForDay(date: String, sport: String? = null): List<SportsEvent> {
        val sportParam = sport?.let { "&s=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}" } ?: ""
        val json = SimpleHttp.getText("https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date$sportParam")
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return buildList {
            for (i in 0 until events.length()) {
                val item = events.optJSONObject(i) ?: continue
                add(
                    SportsEvent(
                        id = item.optString("idEvent"),
                        name = item.optString("strEvent").ifBlank { "Event" },
                        league = item.optString("strLeague").takeIf { it.isNotBlank() },
                        sport = item.optString("strSport").takeIf { it.isNotBlank() },
                        date = item.optString("dateEvent").takeIf { it.isNotBlank() },
                        time = item.optString("strTime").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }
}

data class StreamHealth(
    val reachable: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val contentType: String?,
)

object StreamHealthChecker {
    fun check(url: String): StreamHealth {
        val started = System.currentTimeMillis()
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Range", "bytes=0-1")
            conn.setRequestProperty("User-Agent", "AstraWave/0.1")
            val code = conn.responseCode
            StreamHealth(code in 200..399, code, System.currentTimeMillis() - started, conn.contentType)
        } catch (_: Exception) {
            StreamHealth(false, -1, System.currentTimeMillis() - started, null)
        }
    }
}

/**
 * User-authenticated Real-Debrid REST client. It only operates on a URL explicitly supplied
 * by the user/provider layer; it does not discover copyrighted content or search indexes.
 */
class RealDebridClient(private val accessToken: String) {
    private val base = "https://api.real-debrid.com/rest/1.0"

    fun user(): JSONObject = JSONObject(request("GET", "$base/user"))

    fun unrestrictLink(link: String): JSONObject {
        val body = "link=${URLEncoder.encode(link, StandardCharsets.UTF_8.name())}"
        return JSONObject(request("POST", "$base/unrestrict/link", body))
    }

    private fun request(method: String, url: String, body: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("User-Agent", "AstraWave/0.1")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream.bufferedReader().use { it.readText() }
    }
}
