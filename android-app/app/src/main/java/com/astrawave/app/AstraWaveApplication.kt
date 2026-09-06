package com.astrawave.app

import android.app.Application
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
                next.start { command -> inbox.offer(command) }
                next.heartbeat()
                session = next
            }
        }
    }
}
