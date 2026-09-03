package com.astrawave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.SportsEvent
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFeaturedSportsCard
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWaveSectionHeader
import com.astrawave.app.ui.AstraWaveSportsScheduleCard
import com.astrawave.app.ui.AstraWaveStatePanel
import com.astrawave.app.ui.AstraWaveTheme

/** Debug-only Phase 2 surface for the real shared premium sports components. */
class Phase2SportsQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstraWaveTheme {
                Phase2SportsQaScreen()
            }
        }
    }
}

@Composable
private fun Phase2SportsQaScreen() {
    val featured = remember {
        SportsGuideItem(
            event = SportsEvent(
                id = "phase2-qa-featured",
                name = "Albuquerque Astras vs Santa Fe Comets — Championship Showcase",
                league = "AstraWave QA League",
                sport = "Basketball",
                date = "QA date",
                time = "7:30 PM",
            ),
            broadcasterNames = listOf("QA broadcaster metadata only"),
            resolution = null,
        )
    }
    val scheduleItem = remember {
        SportsGuideItem(
            event = SportsEvent(
                id = "phase2-qa-schedule",
                name = "Rio Grande United vs High Desert City with an intentionally long matchup title",
                league = "AstraWave QA Premier Division",
                sport = "Soccer",
                date = "QA date",
                time = "9:15 PM",
            ),
            broadcasterNames = emptyList(),
            resolution = null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AstraWavePageHeader(
            title = "Phase 2 Sports Visual QA",
            subtitle = "Inspect the real shared featured-game and schedule-card components on this device. Verify hierarchy, focus treatment, long-title wrapping, spacing, and 10-foot readability.",
        )
        AstraWaveStatePanel(
            title = "Synthetic QA metadata only",
            message = "These are fictional test matchups with no playback candidate attached. They must never be interpreted as real events, channels, or available streams.",
        )

        AstraWaveSectionHeader(
            title = "Featured matchup",
            subtitle = "This is the same reusable component intended for Sports, Home, and Game Day surfaces.",
        )
        AstraWaveFeaturedSportsCard(
            item = featured,
            multiviewCount = 0,
            onPlay = { _ -> },
            onAddToMultiview = { _ -> },
        )

        Spacer(Modifier.height(4.dp))
        AstraWaveSectionHeader(
            title = "Schedule card",
            subtitle = "Confirm dense schedule information remains premium rather than reading like a generic IPTV table.",
        )
        AstraWaveSportsScheduleCard(
            item = scheduleItem,
            multiviewCount = 0,
            onPlay = { _ -> },
            onAddToMultiview = { _ -> },
        )

        AstraWaveStatePanel(
            title = "Phase 2 benchmark reminder",
            message = "Reject this build if the sports components clip, look like generic debug/data cards, lose focus visibility, or fail the premium flagship presentation required by the master rebuild plan.",
        )
    }
}
