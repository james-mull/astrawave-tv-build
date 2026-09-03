package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.AstraWaveList
import com.astrawave.app.core.FavoriteEntry
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.WatchlistEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-first personal library persistence. SharedPreferences is intentionally used here as the
 * durable offline layer; Firebase can sync the same models when configured without being required
 * for normal library use.
 */
class LocalLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_library_v1", Context.MODE_PRIVATE)

    data class HistoryEntry(
        val profileId: String,
        val item: LibraryItemRef,
        val playedAtEpochMs: Long,
    )

    data class PlaybackProgress(
        val profileId: String,
        val item: LibraryItemRef,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAtEpochMs: Long,
    ) {
        val completed: Boolean get() = durationMs > 0L && positionMs >= (durationMs * 0.92).toLong()
    }

    data class Snapshot(
        val lists: List<AstraWaveList>,
        val favorites: List<FavoriteEntry>,
        val watchlist: List<WatchlistEntry>,
        val history: List<HistoryEntry>,
        val progress: List<PlaybackProgress>,
    )

    fun snapshot(profileId: String): Snapshot = Snapshot(
        lists = lists(profileId),
        favorites = favorites(profileId),
        watchlist = watchlist(profileId),
        history = history(profileId),
        progress = progress(profileId),
    )

    fun lists(profileId: String): List<AstraWaveList> =
        readArray(key("lists", profileId)).mapNotNull(::decodeList).sortedBy { it.sortOrder }

    fun saveList(list: AstraWaveList) {
        val all = lists(list.profileId).filterNot { it.id == list.id } + list.copy(
            updatedAtEpochMs = if (list.updatedAtEpochMs > 0L) list.updatedAtEpochMs else System.currentTimeMillis(),
        )
        writeArray(key("lists", list.profileId), all.map(::encodeList))
    }

    fun deleteList(profileId: String, listId: String) {
        writeArray(key("lists", profileId), lists(profileId).filterNot { it.id == listId }.map(::encodeList))
    }

    fun watchlist(profileId: String): List<WatchlistEntry> =
        readArray(key("watchlist", profileId)).mapNotNull(::decodeWatchlist).sortedByDescending { it.createdAtEpochMs }

    fun setWatchlist(entry: WatchlistEntry, enabled: Boolean) {
        val current = watchlist(entry.profileId).filterNot { it.item.id == entry.item.id }
        val updated = if (enabled) current + entry.copy(
            createdAtEpochMs = if (entry.createdAtEpochMs > 0L) entry.createdAtEpochMs else System.currentTimeMillis(),
        ) else current
        writeArray(key("watchlist", entry.profileId), updated.map(::encodeWatchlist))
    }

    fun favorites(profileId: String): List<FavoriteEntry> =
        readArray(key("favorites", profileId)).mapNotNull(::decodeFavorite).sortedByDescending { it.createdAtEpochMs }

    fun setFavorite(entry: FavoriteEntry, enabled: Boolean) {
        val current = favorites(entry.profileId).filterNot { it.item.id == entry.item.id }
        val updated = if (enabled) current + entry.copy(
            createdAtEpochMs = if (entry.createdAtEpochMs > 0L) entry.createdAtEpochMs else System.currentTimeMillis(),
        ) else current
        writeArray(key("favorites", entry.profileId), updated.map(::encodeFavorite))
    }

    fun history(profileId: String): List<HistoryEntry> =
        readArray(key("history", profileId)).mapNotNull(::decodeHistory).sortedByDescending { it.playedAtEpochMs }

    fun recordHistory(profileId: String, item: LibraryItemRef) {
        val entry = HistoryEntry(profileId, item, System.currentTimeMillis())
        val updated = listOf(entry) + history(profileId).filterNot { it.item.id == item.id }
        writeArray(key("history", profileId), updated.take(250).map(::encodeHistory))
    }

    fun progress(profileId: String): List<PlaybackProgress> =
        readArray(key("progress", profileId)).mapNotNull(::decodeProgress).sortedByDescending { it.updatedAtEpochMs }

    fun saveProgress(profileId: String, item: LibraryItemRef, positionMs: Long, durationMs: Long) {
        val entry = PlaybackProgress(profileId, item, positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L), System.currentTimeMillis())
        val updated = progress(profileId).filterNot { it.item.id == item.id } + entry
        writeArray(key("progress", profileId), updated.map(::encodeProgress))
    }

    fun continueWatching(profileId: String): List<PlaybackProgress> = progress(profileId).filter {
        it.positionMs > 0L && !it.completed
    }

    private fun key(kind: String, profileId: String) = "$kind:$profileId"

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) array.optJSONObject(i)?.let(::add)
            }
        }.getOrDefault(emptyList())
    }

    private fun writeArray(key: String, objects: List<JSONObject>) {
        val array = JSONArray()
        objects.forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun encodeItem(item: LibraryItemRef) = JSONObject()
        .put("id", item.id)
        .put("type", item.type.name)
        .put("title", item.title)
        .put("posterUrl", item.posterUrl)
        .put("sourceId", item.sourceId)

    private fun decodeItem(obj: JSONObject): LibraryItemRef? = runCatching {
        LibraryItemRef(
            id = obj.getString("id"),
            type = LibraryMediaType.valueOf(obj.getString("type")),
            title = obj.getString("title"),
            posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
            sourceId = obj.optString("sourceId").takeIf { it.isNotBlank() && it != "null" },
        )
    }.getOrNull()

    private fun encodeList(list: AstraWaveList): JSONObject {
        val items = JSONArray()
        list.items.forEach { items.put(encodeItem(it)) }
        return JSONObject()
            .put("id", list.id)
            .put("profileId", list.profileId)
            .put("name", list.name)
            .put("description", list.description)
            .put("items", items)
            .put("sortOrder", list.sortOrder)
            .put("isPinned", list.isPinned)
            .put("updatedAtEpochMs", list.updatedAtEpochMs)
    }

    private fun decodeList(obj: JSONObject): AstraWaveList? = runCatching {
        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArray.length()) itemsArray.optJSONObject(i)?.let(::decodeItem)?.let(::add)
        }
        AstraWaveList(
            id = obj.getString("id"),
            profileId = obj.getString("profileId"),
            name = obj.getString("name"),
            description = obj.optString("description"),
            items = items,
            sortOrder = obj.optInt("sortOrder"),
            isPinned = obj.optBoolean("isPinned"),
            updatedAtEpochMs = obj.optLong("updatedAtEpochMs"),
        )
    }.getOrNull()

    private fun encodeFavorite(entry: FavoriteEntry) = JSONObject()
        .put("profileId", entry.profileId)
        .put("item", encodeItem(entry.item))
        .put("createdAtEpochMs", entry.createdAtEpochMs)

    private fun decodeFavorite(obj: JSONObject): FavoriteEntry? = runCatching {
        FavoriteEntry(obj.getString("profileId"), decodeItem(obj.getJSONObject("item"))!!, obj.optLong("createdAtEpochMs"))
    }.getOrNull()

    private fun encodeWatchlist(entry: WatchlistEntry) = JSONObject()
        .put("profileId", entry.profileId)
        .put("item", encodeItem(entry.item))
        .put("createdAtEpochMs", entry.createdAtEpochMs)
        .put("notifyWhenAvailable", entry.notifyWhenAvailable)

    private fun decodeWatchlist(obj: JSONObject): WatchlistEntry? = runCatching {
        WatchlistEntry(
            obj.getString("profileId"),
            decodeItem(obj.getJSONObject("item"))!!,
            obj.optLong("createdAtEpochMs"),
            obj.optBoolean("notifyWhenAvailable", true),
        )
    }.getOrNull()

    private fun encodeHistory(entry: HistoryEntry) = JSONObject()
        .put("profileId", entry.profileId)
        .put("item", encodeItem(entry.item))
        .put("playedAtEpochMs", entry.playedAtEpochMs)

    private fun decodeHistory(obj: JSONObject): HistoryEntry? = runCatching {
        HistoryEntry(obj.getString("profileId"), decodeItem(obj.getJSONObject("item"))!!, obj.getLong("playedAtEpochMs"))
    }.getOrNull()

    private fun encodeProgress(entry: PlaybackProgress) = JSONObject()
        .put("profileId", entry.profileId)
        .put("item", encodeItem(entry.item))
        .put("positionMs", entry.positionMs)
        .put("durationMs", entry.durationMs)
        .put("updatedAtEpochMs", entry.updatedAtEpochMs)

    private fun decodeProgress(obj: JSONObject): PlaybackProgress? = runCatching {
        PlaybackProgress(
            obj.getString("profileId"),
            decodeItem(obj.getJSONObject("item"))!!,
            obj.optLong("positionMs"),
            obj.optLong("durationMs"),
            obj.optLong("updatedAtEpochMs"),
        )
    }.getOrNull()
}
