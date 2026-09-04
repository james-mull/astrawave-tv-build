package com.astrawave.app.data

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/** Household-wide parent PIN used to leave Kids Mode and edit parental controls. */
class ParentPinStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_parent_pin_v1", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = !prefs.getString(KEY_SALT, null).isNullOrBlank() && !prefs.getString(KEY_HASH, null).isNullOrBlank()

    fun setPin(pin: String): Boolean {
        val clean = pin.filter(Char::isDigit).take(6)
        if (clean.length < 4) return false
        val salt = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, clean)).apply()
        return true
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return hash(salt, pin.filter(Char::isDigit).take(6)) == expected
    }

    fun clear() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
    }

    private fun hash(salt: String, pin: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$salt:$pin".toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
    }
}
