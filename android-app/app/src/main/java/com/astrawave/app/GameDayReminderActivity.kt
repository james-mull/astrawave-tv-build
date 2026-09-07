package com.astrawave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.IptvSourceStore
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveSportsScreen
import com.astrawave.app.ui.AstraWaveTheme

/** Opens directly into Game Day from a scheduled sports reminder notification. */
class GameDayReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = HouseholdProfileStore(this).activeProfileId()
        val sources = IptvSourceStore(this).load(profileId)
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()

        setContent {
            AstraWaveTheme {
                Surface(color = AstraWaveColors.Background) {
                    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
                        if (eventTitle.isNotBlank()) {
                            Text(
                                text = "GAME REMINDER • $eventTitle",
                                color = AstraWaveColors.Accent,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            )
                        }
                        AstraWaveSportsScreen(
                            sources = sources,
                            profileId = profileId,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "astrawave_event_id"
        const val EXTRA_EVENT_TITLE = "astrawave_event_title"
    }
}
