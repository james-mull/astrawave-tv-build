package com.astrawave.app.data

import android.content.Context
import android.os.Build
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Reports this signed-in Android/TV client to AstraWave Cloud and listens for user-issued remote commands.
 * UI/playback layers choose which commands they support; unknown commands are rejected safely.
 */
class CloudDeviceSessionSync(private val context: Context) {
    private val ready = AstraWaveFirebase.initialize(context)
    private val auth: FirebaseAuth? get() = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (ready) FirebaseFirestore.getInstance() else null
    private var listener: ListenerRegistration? = null
    val deviceId: String = buildDeviceId()

    data class RemoteCommand(val id:String,val command:String,val value:Long?=null)

    fun heartbeat(currentTitle:String?=null,currentKind:String?=null,playbackState:String="idle",sourceName:String?=null) {
        val uid=auth?.currentUser?.uid?:return;val database=db?:return
        database.collection("users").document(uid).collection("devices").document(deviceId).set(mapOf(
            "name" to deviceName(),"type" to deviceType(),"online" to true,"appVersion" to runCatching{context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull(),
            "currentTitle" to currentTitle,"currentKind" to currentKind,"playbackState" to playbackState,"sourceName" to sourceName,
            "storageFreeMb" to (context.filesDir.freeSpace/1024L/1024L),"lastSeenAt" to FieldValue.serverTimestamp()
        ))
    }

    fun start(onCommand:(RemoteCommand)->Boolean) {
        stop();val uid=auth?.currentUser?.uid?:return;val database=db?:return;heartbeat()
        listener=database.collection("users").document(uid).collection("commands")
            .whereEqualTo("deviceId",deviceId).whereEqualTo("status","queued")
            .addSnapshotListener { snapshots,_ ->
                snapshots?.documentChanges?.filter{it.type==DocumentChange.Type.ADDED||it.type==DocumentChange.Type.MODIFIED}?.forEach { change ->
                    val doc=change.document;val command=RemoteCommand(doc.id,doc.getString("command").orEmpty(),doc.getLong("value"))
                    val handled=runCatching{onCommand(command)}.getOrDefault(false)
                    doc.reference.update(mapOf("status" to if(handled)"handled" else "failed","handledAt" to FieldValue.serverTimestamp()))
                }
            }
    }

    fun stop(){listener?.remove();listener=null}
    fun markOffline(){val uid=auth?.currentUser?.uid?:return;db?.collection("users")?.document(uid)?.collection("devices")?.document(deviceId)?.update(mapOf("online" to false,"lastSeenAt" to FieldValue.serverTimestamp()))}

    private fun buildDeviceId():String {
        val seed="${Build.MANUFACTURER}:${Build.MODEL}:${Build.DEVICE}:${android.provider.Settings.Secure.getString(context.contentResolver,android.provider.Settings.Secure.ANDROID_ID)}"
        return "android-${seed.hashCode().toUInt().toString(16)}"
    }
    private fun deviceName()="${Build.MANUFACTURER} ${Build.MODEL}".trim()
    private fun deviceType():String = if(context.packageManager.hasSystemFeature("android.software.leanback")) "android-tv" else "phone"
}
