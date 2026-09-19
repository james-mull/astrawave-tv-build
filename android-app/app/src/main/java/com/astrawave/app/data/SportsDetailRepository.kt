package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class SportsPeriodLine(
    val team: String,
    val abbreviation: String? = null,
    val score: Int? = null,
    val homeAway: String? = null,
    val periods: List<Pair<String, Int?>> = emptyList(),
)

data class SportsTeamStat(
    val team: String,
    val name: String,
    val value: String,
)

data class SportsLeader(
    val category: String,
    val athlete: String,
    val team: String? = null,
    val value: String,
)

data class SportsStandingRow(
    val team: String,
    val summary: String,
)

data class SportsEventDetail(
    val eventId: String,
    val source: String,
    val status: String? = null,
    val statusDetail: String? = null,
    val venue: String? = null,
    val lines: List<SportsPeriodLine> = emptyList(),
    val teamStats: List<SportsTeamStat> = emptyList(),
    val leaders: List<SportsLeader> = emptyList(),
    val standings: List<SportsStandingRow> = emptyList(),
)

class SportsDetailRepository(
    private val baseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trim().trimEnd('/'),
) {
    fun available(): Boolean = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

    fun load(eventId: String): SportsEventDetail? {
        if (!available() || eventId.isBlank()) return null
        val encoded = URLEncoder.encode(eventId, StandardCharsets.UTF_8.name())
        val connection = URL("$baseUrl/api/astrawave/sports-detail?eventId=$encoded").openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "AstraWave/1.0")
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299 || text.isBlank()) return null
        val root = JSONObject(text)
        if (root.has("error")) return null

        fun nullableInt(obj: JSONObject, key: String): Int? =
            obj.optInt(key).takeIf { obj.has(key) && !obj.isNull(key) }

        val lines = root.optJSONArray("lines")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val periods = item.optJSONArray("periods")?.let { values ->
                        buildList {
                            for (j in 0 until values.length()) {
                                val period = values.optJSONObject(j) ?: continue
                                add(period.optString("label") to nullableInt(period, "value"))
                            }
                        }
                    }.orEmpty()
                    add(
                        SportsPeriodLine(
                            team = item.optString("team").ifBlank { "Team" },
                            abbreviation = item.optString("abbreviation").takeIf(String::isNotBlank),
                            score = nullableInt(item, "score"),
                            homeAway = item.optString("homeAway").takeIf(String::isNotBlank),
                            periods = periods,
                        ),
                    )
                }
            }
        }.orEmpty()

        val stats = root.optJSONArray("teamStats")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        SportsTeamStat(
                            team = item.optString("team").ifBlank { "Team" },
                            name = item.optString("name").ifBlank { "Stat" },
                            value = item.optString("value"),
                        ),
                    )
                }
            }
        }.orEmpty()

        val leaders = root.optJSONArray("leaders")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        SportsLeader(
                            category = item.optString("category").ifBlank { "Leaders" },
                            athlete = item.optString("athlete").ifBlank { "Player" },
                            team = item.optString("team").takeIf(String::isNotBlank),
                            value = item.optString("value"),
                        ),
                    )
                }
            }
        }.orEmpty()

        val standings = root.optJSONArray("standings")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        SportsStandingRow(
                            team = item.optString("team").ifBlank { "Team" },
                            summary = item.optString("summary"),
                        ),
                    )
                }
            }
        }.orEmpty()

        return SportsEventDetail(
            eventId = root.optString("eventId", eventId),
            source = root.optString("source").ifBlank { "AstraWave" },
            status = root.optString("status").takeIf(String::isNotBlank),
            statusDetail = root.optString("statusDetail").takeIf(String::isNotBlank),
            venue = root.optString("venue").takeIf(String::isNotBlank),
            lines = lines,
            teamStats = stats,
            leaders = leaders,
            standings = standings,
        )
    }
}
