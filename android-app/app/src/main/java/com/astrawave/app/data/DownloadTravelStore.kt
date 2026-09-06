package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.DownloadMediaType
import com.astrawave.app.core.DownloadRequest
import com.astrawave.app.core.DownloadState
import com.astrawave.app.core.TravelModePolicy
import org.json.JSONArray
import org.json.JSONObject

/** Persistent queue for authorized direct downloads and Travel Mode planning. */
class DownloadTravelStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_downloads_v1", Context.MODE_PRIVATE)

    fun downloads(profileId: String): List<DownloadRequest> = decode().filter { it.profileId == profileId }.sortedByDescending { it.createdAtEpochMs }

    fun enqueue(request: DownloadRequest) {
        require(request.sourceUrl.startsWith("http://") || request.sourceUrl.startsWith("https://")) { "Download source must be HTTP(S)" }
        val all = decode().filterNot { it.id == request.id }
        write(all + request.copy(progressPercent = request.progressPercent.coerceIn(0, 100)))
    }

    fun update(id: String, state: DownloadState, progressPercent: Int, localUri: String? = null, error: String? = null) {
        write(decode().map { item ->
            if (item.id == id) item.copy(state = state, progressPercent = progressPercent.coerceIn(0, 100), localUri = localUri, error = error) else item
        })
    }

    fun remove(id: String) { write(decode().filterNot { it.id == id }) }

    fun travelPolicy(profileId: String): TravelModePolicy {
        val raw = prefs.getString("travel_$profileId", null) ?: return TravelModePolicy()
        return runCatching {
            val o = JSONObject(raw)
            TravelModePolicy(
                enabled = o.optBoolean("enabled"), wifiOnly = o.optBoolean("wifiOnly", true),
                maxStorageGb = o.optInt("maxStorageGb", 10), movieTarget = o.optInt("movieTarget", 1),
                episodeTarget = o.optInt("episodeTarget", 2), podcastTarget = o.optInt("podcastTarget", 3),
            )
        }.getOrDefault(TravelModePolicy())
    }

    fun saveTravelPolicy(profileId: String, policy: TravelModePolicy) {
        val o = JSONObject().put("enabled", policy.enabled).put("wifiOnly", policy.wifiOnly)
            .put("maxStorageGb", policy.maxStorageGb.coerceAtLeast(1)).put("movieTarget", policy.movieTarget.coerceAtLeast(0))
            .put("episodeTarget", policy.episodeTarget.coerceAtLeast(0)).put("podcastTarget", policy.podcastTarget.coerceAtLeast(0))
        prefs.edit().putString("travel_$profileId", o.toString()).apply()
    }

    fun travelSummary(profileId: String): String {
        val policy = travelPolicy(profileId)
        if (!policy.enabled) return "Travel Mode is off"
        return "${policy.movieTarget} movie • ${policy.episodeTarget} episodes • ${policy.podcastTarget} podcasts • ${policy.maxStorageGb} GB cap"
    }

    private fun decode(): List<DownloadRequest> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(DownloadRequest(
                        id=o.getString("id"), profileId=o.getString("profileId"), mediaId=o.getString("mediaId"),
                        mediaType=DownloadMediaType.valueOf(o.getString("mediaType")), title=o.getString("title"), sourceUrl=o.getString("sourceUrl"),
                        posterUrl=o.optString("posterUrl").takeIf{it.isNotBlank()}, state=runCatching{DownloadState.valueOf(o.getString("state"))}.getOrDefault(DownloadState.FAILED),
                        progressPercent=o.optInt("progressPercent"), localUri=o.optString("localUri").takeIf{it.isNotBlank()},
                        error=o.optString("error").takeIf{it.isNotBlank()}, createdAtEpochMs=o.optLong("createdAtEpochMs")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<DownloadRequest>) {
        val a=JSONArray();items.forEach { d -> a.put(JSONObject().put("id",d.id).put("profileId",d.profileId).put("mediaId",d.mediaId)
            .put("mediaType",d.mediaType.name).put("title",d.title).put("sourceUrl",d.sourceUrl).put("posterUrl",d.posterUrl?:JSONObject.NULL)
            .put("state",d.state.name).put("progressPercent",d.progressPercent).put("localUri",d.localUri?:JSONObject.NULL).put("error",d.error?:JSONObject.NULL)
            .put("createdAtEpochMs",d.createdAtEpochMs)) }
        prefs.edit().putString(KEY_DOWNLOADS,a.toString()).apply()
    }

    companion object { private const val KEY_DOWNLOADS="downloads" }
}
