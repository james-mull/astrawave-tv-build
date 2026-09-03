package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.BuildConfig
import com.astrawave.app.core.AstraWaveList
import com.astrawave.app.core.FavoriteEntry
import com.astrawave.app.core.WatchlistEntry
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

object AstraWaveFirebase {
    fun configured(): Boolean = BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
        BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
        BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun initialize(context: Context): Boolean {
        if (!configured()) return false
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .apply {
                if (BuildConfig.FIREBASE_SENDER_ID.isNotBlank()) {
                    setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                }
            }
            .build()
        FirebaseApp.initializeApp(context, options)
        return true
    }
}

class FirebaseCloudRepository(context: Context) {
    private val ready = AstraWaveFirebase.initialize(context)
    private val auth: FirebaseAuth? get() = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (ready) FirebaseFirestore.getInstance() else null

    val currentUserId: String? get() = auth?.currentUser?.uid
    val configured: Boolean get() = ready
    val signedIn: Boolean get() = currentUserId != null

    fun signUpWithEmail(email: String, password: String): Task<AuthResult>? =
        auth?.createUserWithEmailAndPassword(email.trim(), password)

    fun signInWithEmail(email: String, password: String): Task<AuthResult>? =
        auth?.signInWithEmailAndPassword(email.trim(), password)

    fun signOut() {
        auth?.signOut()
    }

    fun readWatchlist(profileId: String): Task<QuerySnapshot>? = profileCollection(profileId, "watchlist")?.get()
    fun readFavorites(profileId: String): Task<QuerySnapshot>? = profileCollection(profileId, "favorites")?.get()
    fun readLists(profileId: String): Task<QuerySnapshot>? = profileCollection(profileId, "lists")?.get()
    fun readProgress(profileId: String): Task<QuerySnapshot>? = profileCollection(profileId, "progress")?.get()

    fun saveProgress(
        mediaId: String,
        kind: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        profileId: String = "default",
    ) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.collection("progress")?.document(mediaId)
            ?.set(mapOf(
                "mediaId" to mediaId,
                "kind" to kind,
                "title" to title,
                "positionMs" to positionMs,
                "durationMs" to durationMs,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun saveWatchlist(entry: WatchlistEntry) {
        val uid = currentUserId ?: return
        val item = entry.item
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(entry.profileId)
            ?.collection("watchlist")?.document(item.id)
            ?.set(mapOf(
                "mediaId" to item.id,
                "kind" to item.type.name,
                "title" to item.title,
                "posterUrl" to item.posterUrl,
                "sourceId" to item.sourceId,
                "notifyWhenAvailable" to entry.notifyWhenAvailable,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun removeWatchlist(profileId: String, mediaId: String) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.collection("watchlist")?.document(mediaId)?.delete()
    }

    fun addWatchlist(mediaId: String, kind: String, title: String) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document("default")
            ?.collection("watchlist")?.document(mediaId)
            ?.set(mapOf(
                "mediaId" to mediaId,
                "kind" to kind,
                "title" to title,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun saveFavorite(entry: FavoriteEntry) {
        val uid = currentUserId ?: return
        val item = entry.item
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(entry.profileId)
            ?.collection("favorites")?.document(item.id)
            ?.set(mapOf(
                "mediaId" to item.id,
                "kind" to item.type.name,
                "title" to item.title,
                "posterUrl" to item.posterUrl,
                "sourceId" to item.sourceId,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun removeFavorite(profileId: String, mediaId: String) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.collection("favorites")?.document(mediaId)?.delete()
    }

    fun saveList(list: AstraWaveList) {
        val uid = currentUserId ?: return
        val payload = mapOf(
            "id" to list.id,
            "name" to list.name,
            "description" to list.description,
            "sortOrder" to list.sortOrder,
            "isPinned" to list.isPinned,
            "items" to list.items.map { item ->
                mapOf(
                    "id" to item.id,
                    "type" to item.type.name,
                    "title" to item.title,
                    "posterUrl" to item.posterUrl,
                    "sourceId" to item.sourceId,
                )
            },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(list.profileId)
            ?.collection("lists")?.document(list.id)?.set(payload)
    }

    fun removeList(profileId: String, listId: String) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.collection("lists")?.document(listId)?.delete()
    }

    fun saveProfile(profileId: String, name: String, kidsMode: Boolean = false) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.set(mapOf(
                "profileId" to profileId,
                "name" to name,
                "kidsMode" to kidsMode,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun savePreference(profileId: String, key: String, value: Any) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.collection("preferences")?.document(key)
            ?.set(mapOf(
                "key" to key,
                "value" to value,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun addFavoriteTeam(teamId: String, name: String, league: String?, profileId: String = "default") {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)
            ?.collection("favoriteTeams")?.document(teamId)
            ?.set(mapOf(
                "teamId" to teamId,
                "name" to name,
                "league" to league,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun saveFavoriteTeam(teamId: String, name: String, league: String?) =
        addFavoriteTeam(teamId, name, league)

    private fun profileCollection(profileId: String, collection: String) = currentUserId?.let { uid ->
        db?.collection("users")?.document(uid)?.collection("profiles")?.document(profileId)?.collection(collection)
    }
}
