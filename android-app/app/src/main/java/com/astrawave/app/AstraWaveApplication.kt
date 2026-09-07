package com.astrawave.app

import android.app.Application
import android.content.Context
import android.media.AudioManager
import com.astrawave.app.data.AstraWaveFirebase
import com.astrawave.app.data.CloudDeviceSessionSync
import com.astrawave.app.data.RemoteCommandInbox
import com.google.firebase.auth.FirebaseAuth

/** Starts device presence/remote-command sync whenever an AstraWave user is signed in. */
class AstraWaveApplication : Application() {
    private var session: CloudDeviceSessionSync? = null

    override fun onCreate() {
        super.onCreate()
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
                        else -> inbox.offer(command)
                    }
                }
                next.heartbeat()
                session = next
            }
        }
    }

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
