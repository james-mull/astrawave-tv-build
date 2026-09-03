package com.astrawave.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWavePrimaryButton
import com.astrawave.app.ui.AstraWaveSecondaryButton
import com.astrawave.app.ui.AstraWaveStatePanel
import com.astrawave.app.ui.AstraWaveTheme

/** Debug-only launcher for all Phase 2 visual/focus QA surfaces. */
class Phase2QaHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstraWaveTheme {
                Phase2QaHubScreen()
            }
        }
    }
}

@Composable
private fun Phase2QaHubScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
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

    var modalCommit by remember(deviceClass) { mutableStateOf(verificationStore.modalVerifiedCommit(deviceClass)) }
    var sportsCommit by remember(deviceClass) { mutableStateOf(verificationStore.sportsVerifiedCommit(deviceClass)) }
    var verifiedCommit by remember(deviceClass) { mutableStateOf(verificationStore.verifiedCommit(deviceClass)) }

    fun refreshEvidence() {
        modalCommit = verificationStore.modalVerifiedCommit(deviceClass)
        sportsCommit = verificationStore.sportsVerifiedCommit(deviceClass)
        verifiedCommit = verificationStore.verifiedCommit(deviceClass)
    }

    val modalReady = verificationStore.isModalVerified(deviceClass) && modalCommit == commitSha
    val sportsReady = verificationStore.isSportsVerified(deviceClass) && sportsCommit == commitSha
    val deviceReady = verificationStore.isVerified(deviceClass) && verifiedCommit == commitSha

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AstraWavePageHeader(
            title = "Phase 2 QA Hub",
            subtitle = "$deviceClass • ${configuration.screenWidthDp}×${configuration.screenHeightDp} dp • build ${commitSha.take(12)}",
        )
        AstraWaveStatePanel(
            title = if (commitKnown) "Exact-build QA ready" else "Untracked build",
            message = if (commitKnown) {
                "Run every required Phase 2 visual/focus surface on this $deviceClass build. Evidence must match this exact commit."
            } else {
                "This build has no trackable commit SHA. It can be inspected locally, but it cannot satisfy the Phase 2 exit gate."
            },
        )
        AstraWaveStatePanel(
            title = "Modal Focus QA • ${if (modalReady) "PASS" else "REQUIRED"}",
            message = if (modalReady) {
                "Same-device, same-build modal focus evidence is recorded for ${commitSha.take(12)}."
            } else {
                "Open Modal Focus QA and record it on this $deviceClass build before final device verification."
            },
        )
        AstraWaveStatePanel(
            title = "Sports Visual QA • ${if (sportsReady) "PASS" else "REQUIRED"}",
            message = if (sportsReady) {
                "Same-device, same-build sports visual evidence is recorded for ${commitSha.take(12)}."
            } else {
                "Open Sports Visual QA and record the premium sports benchmark on this $deviceClass build."
            },
        )
        AstraWaveStatePanel(
            title = "Final device verification • ${if (deviceReady) "PASS" else "INCOMPLETE"}",
            message = if (deviceReady) {
                "$deviceClass is recorded as verified for this exact build. A newer commit invalidates this evidence."
            } else {
                "Final verification remains blocked until same-build Modal QA, Sports QA, core traversal, and visual benchmark checks are complete."
            },
        )
        AstraWaveStatePanel(
            title = "Exit-gate reminder",
            message = "Phase 2 is not complete until the exact build is visually benchmarked on Phone, Tablet, Android TV, and Fire TV. TV-class checks must use a real remote for D-pad and Back behavior.",
        )
        AstraWavePrimaryButton(
            label = "Open Core Visual / D-pad QA",
            onClick = { context.startActivity(Intent(context, Phase2VisualQaActivity::class.java)) },
        )
        AstraWaveSecondaryButton(
            label = "Open Modal Focus QA",
            onClick = { context.startActivity(Intent(context, Phase2ModalQaActivity::class.java)) },
        )
        AstraWaveSecondaryButton(
            label = "Open Sports Visual QA",
            onClick = { context.startActivity(Intent(context, Phase2SportsQaActivity::class.java)) },
        )
        AstraWaveSecondaryButton(
            label = "Refresh QA status",
            onClick = ::refreshEvidence,
        )
        Text(
            text = "Use docs/PHASE2_DEVICE_VISUAL_QA_CHECKLIST.md to record the exact-build benchmark. Do not use this hub as evidence by itself.",
            color = AstraWaveColors.SecondaryText,
        )
    }
}
