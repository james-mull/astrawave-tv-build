package com.astrawave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.IptvSourceStore
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveGuideScreen
import com.astrawave.app.ui.AstraWaveSportsScreen
import com.astrawave.app.ui.AstraWaveTheme

/** Opens a real AstraWave surface for cloud/companion remote navigation commands. */
class RemoteDestinationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = HouseholdProfileStore(this).activeProfileId()
        val sources = IptvSourceStore(this).load(profileId)
        val destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty().lowercase()
        setContent {
            AstraWaveTheme {
                Surface(color = AstraWaveColors.Background) {
                    when (destination) {
                        DEST_GUIDE -> AstraWaveGuideScreen(sources = sources, profileId = profileId)
                        DEST_SPORTS -> AstraWaveSportsScreen(sources = sources, profileId = profileId)
                        else -> AstraWaveGuideScreen(sources = sources, profileId = profileId)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "remote_destination"
        const val DEST_GUIDE = "guide"
        const val DEST_SPORTS = "sports"
    }
}
