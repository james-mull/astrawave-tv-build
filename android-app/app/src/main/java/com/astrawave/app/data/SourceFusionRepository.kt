package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.SourceHealthSample
import com.astrawave.app.core.SourceHealthScore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Learns local source reliability from health checks and produces a deterministic failover order.
 * It scores only URLs AstraWave already received from an eligible/authorized provider.
 */
class SourceFusionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_source_health_v1", Context.MODE_PRIVATE)

    data class Candidate(
        val sourceKey: String,
        val url: String,
        val provider: String,
        val priority: Int = 50,
        val qualityLabel: String? = null,
    )

    data class RankedCandidate(
        val candidate: Candidate,
        val health: SourceHealthScore,
        val finalScore: Int,
    )

    fun record(sample: SourceHealthSample) {
        val all = samples(sample.sourceKey).toMutableList()
        all += sample
        val trimmed = all.takeLast(MAX_SAMPLES)
        prefs.edit().putString(key(sample.sourceKey), encode(trimmed)).apply()
    }

    fun probe(candidate: Candidate): SourceHealthSample {
        val health = StreamHealthChecker.check(candidate.url)
        val sample = SourceHealthSample(
            sourceKey = candidate.sourceKey,
            reachable = health.reachable,
            latencyMs = health.latencyMs,
            statusCode = health.statusCode,
            contentType = health.contentType,
            qualityLabel = candidate.qualityLabel,
        )
        record(sample)
        return sample
    }

    fun score(sourceKey: String): SourceHealthScore {
        val samples = samples(sourceKey)
        if (samples.isEmpty()) return SourceHealthScore(sourceKey, 50, 0.0, null, 0, 0)
        val reachable = samples.count { it.reachable }
        val uptime = reachable.toDouble() / samples.size.toDouble() * 100.0
        val latency = samples.filter { it.reachable }.map { it.latencyMs }.sorted().let { values ->
            if (values.isEmpty()) null else values[values.size / 2]
        }
        val failures = samples.takeLast(5).count { !it.reachable }
        val latencyPoints = when {
            latency == null -> 0
            latency < 500L -> 25
            latency < 1_000L -> 18
            latency < 2_000L -> 10
            latency < 4_000L -> 4
            else -> 0
        }
        val score = ((uptime * 0.7).toInt() + latencyPoints - failures * 8).coerceIn(0, 100)
        return SourceHealthScore(sourceKey, score, uptime, latency, failures, samples.size)
    }

    fun rank(candidates: List<Candidate>, probeUnknown: Boolean = true): List<RankedCandidate> =
        candidates.distinctBy { it.url }.map { candidate ->
            if (probeUnknown && samples(candidate.sourceKey).isEmpty()) runCatching { probe(candidate) }
            val health = score(candidate.sourceKey)
            val qualityBoost = when (candidate.qualityLabel?.lowercase()) {
                "4k", "2160p" -> 15
                "1080p" -> 10
                "720p" -> 5
                else -> 0
            }
            val priorityBoost = (100 - candidate.priority.coerceIn(0, 100)) / 5
            RankedCandidate(candidate, health, health.score + qualityBoost + priorityBoost)
        }.sortedWith(compareByDescending<RankedCandidate> { it.finalScore }.thenBy { it.candidate.provider })

    fun bestHealthy(candidates: List<Candidate>): RankedCandidate? =
        rank(candidates).firstOrNull { it.health.score >= 35 }

    fun samples(sourceKey: String): List<SourceHealthSample> {
        val raw = prefs.getString(key(sourceKey), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(SourceHealthSample(
                        sourceKey = sourceKey,
                        reachable = obj.optBoolean("reachable"),
                        latencyMs = obj.optLong("latencyMs"),
                        statusCode = obj.optInt("statusCode"),
                        contentType = obj.optString("contentType").takeIf { it.isNotBlank() && it != "null" },
                        qualityLabel = obj.optString("qualityLabel").takeIf { it.isNotBlank() && it != "null" },
                        bitrateKbps = obj.optInt("bitrateKbps").takeIf { !obj.isNull("bitrateKbps") },
                        checkedAtEpochMs = obj.optLong("checkedAtEpochMs"),
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(samples: List<SourceHealthSample>): String {
        val array = JSONArray()
        samples.forEach { sample ->
            array.put(JSONObject()
                .put("reachable", sample.reachable)
                .put("latencyMs", sample.latencyMs)
                .put("statusCode", sample.statusCode)
                .put("contentType", sample.contentType ?: JSONObject.NULL)
                .put("qualityLabel", sample.qualityLabel ?: JSONObject.NULL)
                .put("bitrateKbps", sample.bitrateKbps ?: JSONObject.NULL)
                .put("checkedAtEpochMs", sample.checkedAtEpochMs))
        }
        return array.toString()
    }

    private fun key(sourceKey: String) = "source_${sourceKey.hashCode()}"

    companion object { private const val MAX_SAMPLES = 40 }
}
