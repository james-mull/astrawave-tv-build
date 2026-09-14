package com.astrawave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.astrawave.app.core.AccountSection
import com.astrawave.app.ui.*

class CustomerPageGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra(EXTRA_PAGE).orEmpty()
        setContent {
            AstraWaveTheme {
                Surface(color = AstraWaveColors.Background) {
                    when (page) {
                        "subscription" -> SubscriptionOverviewScreen("AstraWave Free", onBack = {}, profileId = "default")
                        "playback" -> AccountUtilityScreen(AccountSection.PLAYBACK, "default", onBack = {})
                        "audio-subtitles" -> AccountUtilityScreen(AccountSection.SUBTITLES_AUDIO, "default", onBack = {})
                        "downloads" -> AccountUtilityScreen(AccountSection.DOWNLOADS_STORAGE, "default", onBack = {})
                        "notifications" -> AccountUtilityScreen(AccountSection.NOTIFICATIONS, "default", onBack = {})
                        "appearance" -> AccountUtilityScreen(AccountSection.APPEARANCE, "default", onBack = {})
                        "backup" -> AccountUtilityScreen(AccountSection.BACKUP_SYNC, "default", onBack = {})
                        "devices" -> AccountUtilityScreen(AccountSection.DEVICES, "default", onBack = {})
                        "privacy-parental" -> SafetySettingsScreen("default", onBack = {})
                        "premium-hub" -> AstraWavePremiumPowerCenter("default")
                        "debrid" -> DebridAccountScreen("default", onBack = {})
                        "diagnostics" -> VodPlaybackDiagnosticsScreen("default", onBack = {})
                        else -> AstraWaveEmptyState("Gallery page unavailable", page.ifBlank { "No page requested" })
                    }
                }
            }
        }
    }

    companion object { const val EXTRA_PAGE = "page" }
}
