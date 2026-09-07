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

    @Synchronized
    fun consumeIf(commands:Set<String>):Pending? {
        val pending=peek()?:return null
        if(pending.command !in commands)return null
        prefs.edit().clear().commit()
        return pending
    }

    companion object {
        val PLAYER_COMMANDS=setOf("PLAY","PAUSE","SEEK_FORWARD","SEEK_BACK","VOLUME_UP","VOLUME_DOWN","MUTE")
        val SHELL_COMMANDS=setOf("CHANNEL_UP","CHANNEL_DOWN","BACK","HOME","OPEN_GUIDE","OPEN_SPORTS")
        val SUPPORTED=PLAYER_COMMANDS+SHELL_COMMANDS
        private const val KEY_ID="id";private const val KEY_COMMAND="command";private const val KEY_VALUE="value"
    }
}
