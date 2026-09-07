package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/** Server-fed sports + channel projection. Falls back cleanly when the AstraWave backend is absent. */
class BackendSportsChannelCloudRepository(
    private val baseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trim().trimEnd('/'),
) {
    data class Candidate(
        val provider: String,
        val sourceId: String,
        val streamUrl: String,
        val group: String? = null,
        val quality: String? = null,
        val healthScore: Double? = null,
        val uptimePercent: Double? = null,
        val latencyMs: Int? = null,
    )

    data class ChannelMatch(
        val id: String,
        val name: String,
        val logoUrl: String?,
        val group: String?,
        val matchScore: Int,
        val matchReason: String,
        val sourceCount: Int,
        val healthScore: Double? = null,
        val candidates: List<Candidate>,
    )

    data class CloudEvent(
        val id: String,
        val league: String,
        val sport: String?,
        val title: String,
        val startTime: String,
        val status: String,
        val homeTeam: String?,
        val awayTeam: String?,
        val broadcasts: List<String>,
        val source: String,
        val channelMatches: List<ChannelMatch>,
    )

    fun available(): Boolean = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

    fun events(
        date: LocalDate,
        days: Int = 3,
    ): List<CloudEvent> {
        if (!available()) return emptyList()
        val encodedDate = URLEncoder.encode(date.toString(), StandardCharsets.UTF_8.name())
        val url = "$baseUrl/api/astrawave/sports-channel-cloud?date=$encodedDate&days=${days.coerceIn(1, 7)}"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "AstraWave/1.0")
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Sports Channel Cloud HTTP $code")
        val root = JSONObject(text)
        val items = root.optJSONArray("events") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val broadcasts = item.optJSONArray("broadcasts")?.let { array ->
                    buildList { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
                }.orEmpty()
                val channelMatches = item.optJSONArray("channelMatches")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val match = array.optJSONObject(index) ?: continue
                            val candidates = match.optJSONArray("candidates")?.let { cands ->
                                buildList {
                                    for (c in 0 until cands.length()) {
                                        val candidate = cands.optJSONObject(c) ?: continue
                                        val streamUrl = candidate.optString("streamUrl").takeIf(String::isNotBlank) ?: continue
                                        add(
                                            Candidate(
                                                provider = candidate.optString("provider").ifBlank { "AstraWave" },
                                                sourceId = candidate.optString("sourceId").ifBlank { "public" },
                                                streamUrl = streamUrl,
                                                group = candidate.optString("group").takeIf(String::isNotBlank),
                                                quality = candidate.optString("quality").takeIf(String::isNotBlank),
                                                healthScore = candidate.optDouble("healthScore").takeIf { !it.isNaN() },
                                                uptimePercent = candidate.optDouble("uptimePercent").takeIf { !it.isNaN() },
                                                latencyMs = candidate.optInt("latencyMs").takeIf { candidate.has("latencyMs") && it >= 0 },
                                            ),
                                        )
                                    }
                                }
                            }.orEmpty()
                            add(
                                ChannelMatch(
                                    id = match.optString("id"),
                                    name = match.optString("name").ifBlank { "Channel" },
                                    logoUrl = match.optString("logoUrl").takeIf(String::isNotBlank),
                                    group = match.optString("group").takeIf(String::isNotBlank),
                                    matchScore = match.optInt("matchScore", 0),
                                    matchReason = match.optString("matchReason"),
                                    sourceCount = match.optInt("sourceCount", candidates.size),
                                    healthScore = match.optDouble("healthScore").takeIf { !it.isNaN() },
                                    candidates = candidates,
                                ),
                            )
                        }
                    }
                }.orEmpty()
                add(
                    CloudEvent(
                        id = item.optString("id"),
                        league = item.optString("league").ifBlank { "Sports" },
                        sport = item.optString("sport").takeIf(String::isNotBlank),
                        title = item.optString("title").ifBlank { "Event" },
                        startTime = item.optString("startTime"),
                        status = item.optString("status").ifBlank { "scheduled" },
                        homeTeam = item.optString("homeTeam").takeIf(String::isNotBlank),
                        awayTeam = item.optString("awayTeam").takeIf(String::isNotBlank),
                        broadcasts = broadcasts,
                        source = item.optString("source").ifBlank { "AstraWave Sports Cloud" },
                        channelMatches = channelMatches,
                    ),
                )
            }
        }
    }
}
