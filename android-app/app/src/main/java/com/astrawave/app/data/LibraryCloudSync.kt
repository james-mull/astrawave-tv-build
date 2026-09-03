package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.AstraWaveList
import com.astrawave.app.core.FavoriteEntry
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.WatchlistEntry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

/** Restores Firestore library state into the durable local-first store for the active profile. */
class LibraryCloudSync(context: Context) {
    private val cloud = FirebaseCloudRepository(context)
    private val local = LocalLibraryStore(context)

    data class RestoreReport(
        val watchlistImported: Int,
        val favoritesImported: Int,
        val listsImported: Int,
        val progressImported: Int,
    )

    fun restore(profileId: String = "default", onComplete: (Result<RestoreReport>) -> Unit) {
        if (!cloud.configured || !cloud.signedIn) {
            onComplete(Result.success(RestoreReport(0, 0, 0, 0)))
            return
        }

        val watchlistTask = cloud.readWatchlist(profileId)
        val favoritesTask = cloud.readFavorites(profileId)
        val listsTask = cloud.readLists(profileId)
        val progressTask = cloud.readProgress(profileId)
        if (watchlistTask == null || favoritesTask == null || listsTask == null || progressTask == null) {
            onComplete(Result.failure(IllegalStateException("Firebase library reads are unavailable")))
            return
        }

        Tasks.whenAllSuccess<QuerySnapshot>(watchlistTask, favoritesTask, listsTask, progressTask)
            .addOnSuccessListener { snapshots ->
                runCatching {
                    val watchlistCount = mergeWatchlist(profileId, snapshots[0])
                    val favoriteCount = mergeFavorites(profileId, snapshots[1])
                    val listCount = mergeLists(profileId, snapshots[2])
                    val progressCount = mergeProgress(profileId, snapshots[3])
                    RestoreReport(watchlistCount, favoriteCount, listCount, progressCount)
                }.also(onComplete)
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun mergeWatchlist(profileId: String, snapshot: QuerySnapshot): Int {
        val localIds = local.watchlist(profileId).map { it.item.id }.toMutableSet()
        var imported = 0
        snapshot.documents.forEach { doc ->
            val item = decodeItem(doc) ?: return@forEach
            if (localIds.add(item.id)) {
                local.setWatchlist(
                    WatchlistEntry(
                        profileId = profileId,
                        item = item,
                        createdAtEpochMs = timestampMs(doc, "updatedAt"),
                        notifyWhenAvailable = doc.getBoolean("notifyWhenAvailable") ?: true,
                    ),
                    enabled = true,
                )
                imported++
            }
        }
        return imported
    }

    private fun mergeFavorites(profileId: String, snapshot: QuerySnapshot): Int {
        val localIds = local.favorites(profileId).map { it.item.id }.toMutableSet()
        var imported = 0
        snapshot.documents.forEach { doc ->
            val item = decodeItem(doc) ?: return@forEach
            if (localIds.add(item.id)) {
                local.setFavorite(
                    FavoriteEntry(profileId, item, timestampMs(doc, "updatedAt")),
                    enabled = true,
                )
                imported++
            }
        }
        return imported
    }

    private fun mergeLists(profileId: String, snapshot: QuerySnapshot): Int {
        val localById = local.lists(profileId).associateBy { it.id }.toMutableMap()
        var imported = 0
        snapshot.documents.forEach { doc ->
            val remote = decodeList(profileId, doc) ?: return@forEach
            val existing = localById[remote.id]
            if (existing == null || remote.updatedAtEpochMs > existing.updatedAtEpochMs) {
                local.saveList(remote)
                localById[remote.id] = remote
                imported++
            }
        }
        return imported
    }

    private fun mergeProgress(profileId: String, snapshot: QuerySnapshot): Int {
        val localById = local.progress(profileId).associateBy { it.item.id }.toMutableMap()
        var imported = 0
        snapshot.documents.forEach { doc ->
            val item = decodeItem(doc) ?: return@forEach
            val updatedAt = timestampMs(doc, "updatedAt")
            val existing = localById[item.id]
            if (existing == null || updatedAt > existing.updatedAtEpochMs) {
                local.saveProgress(
                    profileId = profileId,
                    item = item,
                    positionMs = doc.getLong("positionMs") ?: 0L,
                    durationMs = doc.getLong("durationMs") ?: 0L,
                )
                imported++
            }
        }
        return imported
    }

    private fun decodeList(profileId: String, doc: DocumentSnapshot): AstraWaveList? = runCatching {
        val rawItems = doc.get("items") as? List<*> ?: emptyList<Any>()
        val items = rawItems.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            decodeItemMap(map)
        }
        AstraWaveList(
            id = doc.getString("id") ?: doc.id,
            profileId = profileId,
            name = doc.getString("name").orEmpty().ifBlank { "List" },
            description = doc.getString("description").orEmpty(),
            items = items,
            sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
            isPinned = doc.getBoolean("isPinned") ?: false,
            updatedAtEpochMs = timestampMs(doc, "updatedAt"),
        )
    }.getOrNull()

    private fun decodeItem(doc: DocumentSnapshot): LibraryItemRef? = decodeItemMap(
        mapOf(
            "id" to (doc.getString("mediaId") ?: doc.id),
            "type" to doc.getString("kind"),
            "title" to doc.getString("title"),
            "posterUrl" to doc.getString("posterUrl"),
            "sourceId" to doc.getString("sourceId"),
        )
    )

    private fun decodeItemMap(map: Map<*, *>): LibraryItemRef? = runCatching {
        val rawType = map["type"]?.toString() ?: map["kind"]?.toString() ?: return null
        LibraryItemRef(
            id = map["id"]?.toString() ?: map["mediaId"]?.toString() ?: return null,
            type = LibraryMediaType.valueOf(rawType),
            title = map["title"]?.toString().orEmpty().ifBlank { "Untitled" },
            posterUrl = map["posterUrl"]?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            sourceId = map["sourceId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        )
    }.getOrNull()

    private fun timestampMs(doc: DocumentSnapshot, field: String): Long =
        (doc.get(field) as? Timestamp)?.toDate()?.time ?: 0L
}
