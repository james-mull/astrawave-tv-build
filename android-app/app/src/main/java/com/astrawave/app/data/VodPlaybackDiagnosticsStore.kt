package com.astrawave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Profile-scoped audit trail for Play Best decisions.
 *
 * This stores only non-secret playback metadata. It intentionally never persists stream URLs,
 * provider credentials, debrid tokens, request headers, or personal-media locators.
 */
class VodPlaybackDiagnosticsStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_vod_playback_diagnostics_v1", Context.MODE_PRIVATE)

    data class Decision(
        val title: String,
        val mediaType: String,
        val provider: String,
        val quality: String?,
        val latencyMs: Long?,
        val providerCount: Int,
        val backupCount: Int,
        val personalMedia: Boolean,
        val debridOptimized: Boolean,
        val resolvedAtEpochMs: Long,
    )

    fun record(profileId: String, launch: VodPlaybackLaunch) {
        val decision = Decision(
            title = launch.request.playbackTitle(),
            mediaType = launch.request.mediaType,
            provider = launch.bestProviderLabel ?: "Unknown",
            quality = if (launch.personal != null) "Owned media" else launch.bestQuality,
            latencyMs = launch.bestSource?.latencyMs,
            providerCount = launch.providerCount,
            backupCount = launch.backupCount,
            personalMedia = launch.personal != null,
            debridOptimized = launch.plan.debridOptimized,
            resolvedAtEpochMs = System.currentTimeMillis(),
        )
        val updated = (listOf(decision) + load(profileId))
            .distinctBy { "${it.title}:${it.provider}:${it.resolvedAtEpochMs / 10_000L}" }
            .take(MAX_HISTORY)
        write(profileId, updated)
    }

    fun latest(profileId: String): Decision? = load(profileId).firstOrNull()

    fun load(profileId: String): List<Decision> {
        val raw = prefs.getString(profileId, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(
                        Decision(
                            title = obj.optString("title"),
                            mediaType = obj.optString("mediaType"),
                            provider = obj.optString("provider").ifBlank { "Unknown" },
                            quality = obj.optString("quality").takeIf(String::isNotBlank),
                            latencyMs = obj.optLong("latencyMs", -1L).takeIf { it >= 0L },
                            providerCount = obj.optInt("providerCount", 0),
                            backupCount = obj.optInt("backupCount", 0),
                            personalMedia = obj.optBoolean("personalMedia", false),
                            debridOptimized = obj.optBoolean("debridOptimized", false),
                            resolvedAtEpochMs = obj.optLong("resolvedAtEpochMs", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(profileId: String, values: List<Decision>) {
        val array = JSONArray()
        values.forEach { value ->
            array.put(
                JSONObject()
                    .put("title", value.title)
                    .put("mediaType", value.mediaType)
                    .put("provider", value.provider)
                    .put("quality", value.quality)
                    .put("latencyMs", value.latencyMs)
                    .put("providerCount", value.providerCount)
                    .put("backupCount", value.backupCount)
                    .put("personalMedia", value.personalMedia)
                    .put("debridOptimized", value.debridOptimized)
                    .put("resolvedAtEpochMs", value.resolvedAtEpochMs),
            )
        }
        prefs.edit().putString(profileId, array.toString()).apply()
    }

    companion object {
        private const val MAX_HISTORY = 40
    }
}
