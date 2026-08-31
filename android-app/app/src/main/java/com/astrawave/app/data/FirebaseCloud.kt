package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

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

    fun saveProgress(mediaId: String, kind: String, title: String, positionMs: Long, durationMs: Long) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("progress")?.document(mediaId)
            ?.set(mapOf(
                "mediaId" to mediaId,
                "kind" to kind,
                "title" to title,
                "positionMs" to positionMs,
                "durationMs" to durationMs,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun addWatchlist(mediaId: String, kind: String, title: String) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("watchlist")?.document(mediaId)
            ?.set(mapOf(
                "mediaId" to mediaId,
                "kind" to kind,
                "title" to title,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }

    fun saveFavoriteTeam(teamId: String, name: String, league: String?) {
        val uid = currentUserId ?: return
        db?.collection("users")?.document(uid)?.collection("favoriteTeams")?.document(teamId)
            ?.set(mapOf(
                "teamId" to teamId,
                "name" to name,
                "league" to league,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
    }
}
