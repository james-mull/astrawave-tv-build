package com.astrawave.app.data

import android.content.Context

/**
 * Small local bridge between cloud remote commands and active UI/playback surfaces.
 * The cloud listener writes only known commands; foreground screens decide when/how to consume them.
 */
class RemoteCommandInbox(context: Context) {
    private val prefs=context.getSharedPreferences("astrawave_remote_inbox",Context.MODE_PRIVATE)

    data class Pending(val id:String,val command:String,val value:Long?)

    fun offer(command: CloudDeviceSessionSync.RemoteCommand):Boolean {
        if(command.command !in SUPPORTED)return false
        prefs.edit().putString(KEY_ID,command.id).putString(KEY_COMMAND,command.command)
            .apply { if(command.value!=null)putLong(KEY_VALUE,command.value) else remove(KEY_VALUE) }.apply()
        return true
    }

    fun peek():Pending? {
        val id=prefs.getString(KEY_ID,null)?:return null
        val command=prefs.getString(KEY_COMMAND,null)?:return null
        return Pending(id,command,if(prefs.contains(KEY_VALUE))prefs.getLong(KEY_VALUE,0L) else null)
    }

    fun consume():Pending? { val pending=peek()?:return null;prefs.edit().clear().apply();return pending }

    companion object {
        val SUPPORTED=setOf("PLAY","PAUSE","SEEK_FORWARD","SEEK_BACK","CHANNEL_UP","CHANNEL_DOWN","VOLUME_UP","VOLUME_DOWN","MUTE","BACK","HOME","OPEN_GUIDE","OPEN_SPORTS")
        private const val KEY_ID="id";private const val KEY_COMMAND="command";private const val KEY_VALUE="value"
    }
}
