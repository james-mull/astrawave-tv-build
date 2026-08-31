package com.astrawave.app.data

import android.content.Context

/**
 * Local configuration for the prototype. Production secrets should move to Android Keystore-backed
 * storage and OAuth/device authorization where the upstream provider supports it.
 */
class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_settings", Context.MODE_PRIVATE)

    var tmdbBearerToken: String?
        get() = prefs.getString("tmdb_bearer", null)
        set(value) = prefs.edit().putString("tmdb_bearer", value?.trim()).apply()

    var m3uUrl: String?
        get() = prefs.getString("m3u_url", null)
        set(value) = prefs.edit().putString("m3u_url", value?.trim()).apply()

    var xtreamServer: String?
        get() = prefs.getString("xtream_server", null)
        set(value) = prefs.edit().putString("xtream_server", value?.trim()).apply()

    var xtreamUsername: String?
        get() = prefs.getString("xtream_username", null)
        set(value) = prefs.edit().putString("xtream_username", value?.trim()).apply()

    var xtreamPassword: String?
        get() = prefs.getString("xtream_password", null)
        set(value) = prefs.edit().putString("xtream_password", value).apply()

    var realDebridAccessToken: String?
        get() = prefs.getString("rd_token", null)
        set(value) = prefs.edit().putString("rd_token", value?.trim()).apply()

    fun clearProviderSecrets() {
        prefs.edit()
            .remove("xtream_username")
            .remove("xtream_password")
            .remove("rd_token")
            .apply()
    }
}
