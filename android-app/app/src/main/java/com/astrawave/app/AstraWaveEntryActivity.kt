package com.astrawave.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Lightweight launcher gate. Notification permission is requested once, then new
 * installs enter the simple onboarding flow while returning users open AstraWave directly.
 */
class AstraWaveEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean(KEY_PROMPTED, false)

        if (needsPermission) {
            prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            openDestination()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) openDestination()
    }

    private fun openDestination() {
        val onboardingSeen = getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE)
            .getBoolean(OnboardingActivity.KEY_ONBOARDING_SEEN, false)
        startActivity(
            Intent(
                this,
                if (onboardingSeen) RebuildMainActivity::class.java else OnboardingActivity::class.java,
            ),
        )
        finish()
    }

    private companion object {
        const val PREFS = "astrawave_permission_prompts"
        const val KEY_PROMPTED = "notifications_prompted"
        const val REQUEST_NOTIFICATIONS = 4103
    }
}
