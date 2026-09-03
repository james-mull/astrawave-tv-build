package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import org.json.JSONArray
import org.json.JSONObject

/** Profile-scoped local persistence for podcast RSS feeds and internet radio stations. */
class AudioSourceStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_audio_sources_v1", Context.MODE_PRIVATE)

    data class Snapshot(
        val subscriptions: List<AudioSubscription>,
        val stations: List<RadioStation>,
    )

    fun load(profileId: String): Snapshot = Snapshot(
        subscriptions = readSubscriptions(profileId),
        stations = readStations(profileId),
    )

    fun saveSubscription(profileId: String, subscription: AudioSubscription) {
        val updated = readSubscriptions(profileId).filterNot { it.id == subscription.id } + subscription
        writeSubscriptions(profileId, updated.sortedBy { it.title.lowercase() })
    }

    fun deleteSubscription(profileId: String, id: String) {
        writeSubscriptions(profileId, readSubscriptions(profileId).filterNot { it.id == id })
    }

    fun saveStation(profileId: String, station: RadioStation) {
        val updated = readStations(profileId).filterNot { it.id == station.id } + station
        writeStations(profileId, updated.sortedBy { it.name.lowercase() })
    }

    fun deleteStation(profileId: String, id: String) {
        writeStations(profileId, readStations(profileId).filterNot { it.id == id })
    }

    private fun readSubscriptions(profileId: String): List<AudioSubscription> {
        val array = readArray("subscriptions:$profileId")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val title = obj.optString("title")
                val feedUrl = obj.optString("feedUrl")
                if (id.isBlank() || title.isBlank() || feedUrl.isBlank()) continue
                add(
                    AudioSubscription(
                        id = id,
                        title = title,
                        feedUrl = feedUrl,
                        artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
                        videoCapable = obj.optBoolean("videoCapable", false),
                    )
                )
            }
        }
    }

    private fun writeSubscriptions(profileId: String, items: List<AudioSubscription>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("feedUrl", item.feedUrl)
                    .put("artworkUrl", item.artworkUrl)
                    .put("videoCapable", item.videoCapable)
            )
        }
        prefs.edit().putString("subscriptions:$profileId", array.toString()).apply()
    }

    private fun readStations(profileId: String): List<RadioStation> {
        val array = readArray("stations:$profileId")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                val streamUrl = obj.optString("streamUrl")
                if (id.isBlank() || name.isBlank() || streamUrl.isBlank()) continue
                add(
                    RadioStation(
                        id = id,
                        name = name,
                        streamUrl = streamUrl,
                        genre = obj.optString("genre").takeIf { it.isNotBlank() && it != "null" },
                        country = obj.optString("country").takeIf { it.isNotBlank() && it != "null" },
                        logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" },
                    )
                )
            }
        }
    }

    private fun writeStations(profileId: String, items: List<RadioStation>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("streamUrl", item.streamUrl)
                    .put("genre", item.genre)
                    .put("country", item.country)
                    .put("logoUrl", item.logoUrl)
            )
        }
        prefs.edit().putString("stations:$profileId", array.toString()).apply()
    }

    private fun readArray(key: String): JSONArray = runCatching {
        JSONArray(prefs.getString(key, null) ?: "[]")
    }.getOrDefault(JSONArray())
}
