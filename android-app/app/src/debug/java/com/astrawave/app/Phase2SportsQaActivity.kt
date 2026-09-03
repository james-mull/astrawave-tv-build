package com.astrawave.app

import android.content.res.Configuration
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.SportsEvent
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFeaturedSportsCard
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWavePrimaryButton
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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val verificationStore = remember(context) { Phase2QaVerificationStore(context) }
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val isFireTv = isTv && (
        Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            Build.BRAND.equals("Amazon", ignoreCase = true)
        )
    val deviceClass = when {
        isFireTv -> "Fire TV"
        isTv -> "Android TV"
        configuration.screenWidthDp >= 600 -> "Tablet"
        else -> "Phone"
    }
    val commitSha = BuildConfig.GIT_SHA.ifBlank { "local-untracked" }
    val commitKnown = commitSha != "local-untracked" && commitSha.length >= 7
    var recordedCommit by remember(deviceClass) {
        mutableStateOf(verificationStore.sportsVerifiedCommit(deviceClass))
    }
    val exactBuildVerified = verificationStore.isSportsVerified(deviceClass) && recordedCommit == commitSha

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
            subtitle = "$deviceClass • ${configuration.screenWidthDp}×${configuration.screenHeightDp} dp • build ${commitSha.take(12)} • inspect the real shared featured-game and schedule-card components for hierarchy, focus treatment, long-title wrapping, spacing, and 10-foot readability.",
        )
        AstraWaveStatePanel(
            title = "Sports verification evidence",
            message = when {
                exactBuildVerified -> "$deviceClass sports visual QA is recorded for this exact build (${commitSha.take(12)})."
                recordedCommit != null -> "$deviceClass sports visual QA was recorded on ${recordedCommit!!.take(12)}, not this build (${commitSha.take(12)}). Re-run the benchmark."
                !commitKnown -> "This local build has no trackable commit SHA and must not be used as Phase 2 sports visual-verification evidence."
                else -> "Inspect every sports component below on this device, then record the benchmark only if it meets the Phase 2 premium-quality requirements."
            },
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
            message = "Reject this exact build if the sports components clip, look like generic debug/data cards, lose focus visibility, or fail the premium flagship presentation required by the master rebuild plan.",
        )
        AstraWavePrimaryButton(
            label = if (exactBuildVerified) "Sports QA verified" else "Record sports visual QA",
            enabled = commitKnown && !exactBuildVerified,
            onClick = {
                verificationStore.markSportsVerified(deviceClass, commitSha)
                recordedCommit = commitSha
            },
        )
    }
}
