package com.astrawave.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.astrawave.app.data.AstraWaveFirebase
import com.astrawave.app.data.CloudDeviceSessionSync
import com.astrawave.app.data.RemoteCommandInbox
import com.astrawave.app.data.StremioAddonStore
import com.google.firebase.auth.FirebaseAuth
import kotlin.concurrent.thread

/** Starts public-source bootstrap and optional device sync without making Firebase a startup dependency. */
class AstraWaveApplication : Application() {
    private var session: CloudDeviceSessionSync? = null

    override fun onCreate() {
        super.onCreate()

        // Public/reviewed source bootstrap must work even when Firebase is intentionally unconfigured.
        val appContext = applicationContext
        thread(name = "astrawave-source-bootstrap", isDaemon = true) {
            runCatching { StremioAddonStore(appContext).bootstrapDefaults() }
        }

        if (!AstraWaveFirebase.initialize(this)) return
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            session?.stop()
            session = null
            if (auth.currentUser != null) {
                val next = CloudDeviceSessionSync(this)
                val inbox = RemoteCommandInbox(this)
                next.start { command ->
                    when (command.command) {
                        "VOLUME_UP" -> adjustVolume(AudioManager.ADJUST_RAISE)
                        "VOLUME_DOWN" -> adjustVolume(AudioManager.ADJUST_LOWER)
                        "MUTE" -> toggleMute()
                        "HOME" -> openHome()
                        "OPEN_GUIDE" -> openRemoteDestination(RemoteDestinationActivity.DEST_GUIDE)
                        "OPEN_SPORTS" -> openRemoteDestination(RemoteDestinationActivity.DEST_SPORTS)
                        else -> inbox.offer(command)
                    }
                }
                next.heartbeat()
                session = next
            }
        }
    }

    private fun openHome(): Boolean = runCatching {
        startActivity(Intent(this, RebuildMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        true
    }.getOrDefault(false)

    private fun openRemoteDestination(destination: String): Boolean = runCatching {
        startActivity(Intent(this, RemoteDestinationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RemoteDestinationActivity.EXTRA_DESTINATION, destination)
        })
        true
    }.getOrDefault(false)

    private fun adjustVolume(direction: Int): Boolean = runCatching {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        true
    }.getOrDefault(false)

    private fun toggleMute(): Boolean = runCatching {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        } else {
            @Suppress("DEPRECATION")
            audio.setStreamMute(AudioManager.STREAM_MUSIC, !audio.isStreamMute(AudioManager.STREAM_MUSIC))
        }
        true
    }.getOrDefault(false)
}
