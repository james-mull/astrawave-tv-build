package com.astrawave.app

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFocusDialog
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWavePrimaryButton
import com.astrawave.app.ui.AstraWaveStatePanel
import com.astrawave.app.ui.AstraWaveTheme

/** Debug-only Phase 2 modal/D-pad QA surface bound to the exact device/build. */
class Phase2ModalQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstraWaveTheme {
                Phase2ModalQaScreen()
            }
        }
    }
}

@Composable
private fun Phase2ModalQaScreen() {
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

    val openButtonFocus = remember { FocusRequester() }
    val expected = remember { setOf("Open modal", "Modal confirm", "Modal cancel") }
    var showDialog by remember { mutableStateOf(false) }
    var visited by remember { mutableStateOf(emptySet<String>()) }
    var focusTrail by remember { mutableStateOf(emptyList<String>()) }
    var recordedCommit by remember(deviceClass) { mutableStateOf(verificationStore.modalVerifiedCommit(deviceClass)) }
    var resultMessage by remember { mutableStateOf("Open the modal, move between both actions, then close it.") }

    fun recordFocus(label: String) {
        visited = visited + label
        focusTrail = (focusTrail + label).takeLast(8)
    }

    LaunchedEffect(showDialog) {
        if (!showDialog) {
            runCatching { openButtonFocus.requestFocus() }
        }
    }

    val missing = expected - visited
    val traversalComplete = missing.isEmpty()
    val exactBuildVerified = verificationStore.isModalVerified(deviceClass) && recordedCommit == commitSha

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AstraWavePageHeader(
            title = "Phase 2 Modal QA",
            subtitle = "$deviceClass • build ${commitSha.take(12)} • verify initial modal focus, action traversal, Back dismissal, focus-ring visibility, and focus restoration.",
        )
        AstraWaveStatePanel(
            title = if (traversalComplete) "Modal traversal PASS candidate" else "Modal traversal incomplete",
            message = if (traversalComplete) {
                "Open, confirm, and cancel controls were all reached. Visually verify ring visibility, clipping, readability, Back behavior, and focus restoration before recording this device/build."
            } else {
                "Missing: ${missing.joinToString()}. Trail: ${focusTrail.joinToString(" → ").ifBlank { "none yet" }}"
            },
        )
        AstraWaveStatePanel(
            title = "Modal verification evidence",
            message = when {
                exactBuildVerified -> "$deviceClass modal focus is recorded for this exact build (${commitSha.take(12)})."
                recordedCommit != null -> "$deviceClass modal focus was verified on ${recordedCommit!!.take(12)}, not this build (${commitSha.take(12)}). Re-run it."
                !commitKnown -> "This build has no trackable commit SHA, so modal QA cannot be recorded as Phase 2 evidence."
                else -> "$deviceClass modal focus is not yet verified for build ${commitSha.take(12)}."
            },
        )
        AstraWaveStatePanel(title = "Last result", message = resultMessage)
        AstraWavePrimaryButton(
            label = "Open focus-aware modal",
            onClick = { showDialog = true },
            modifier = Modifier
                .focusRequester(openButtonFocus)
                .onFocusChanged { if (it.isFocused) recordFocus("Open modal") },
        )
        AstraWavePrimaryButton(
            label = if (exactBuildVerified) "Modal QA verified" else "Record modal QA",
            enabled = traversalComplete && commitKnown && !exactBuildVerified,
            onClick = {
                verificationStore.markModalVerified(deviceClass, commitSha)
                recordedCommit = commitSha
                resultMessage = "$deviceClass modal focus recorded for build ${commitSha.take(12)}."
            },
        )
    }

    if (showDialog) {
        AstraWaveFocusDialog(
            title = "Test focus-aware modal",
            message = "Confirm should receive initial focus. Move to Cancel and back, test Back dismissal, and verify the visible focus treatment remains obvious from 10 feet.",
            confirmLabel = "Confirm",
            dismissLabel = "Cancel",
            onConfirm = {
                resultMessage = "Confirm activated; focus should return to the launcher control."
                showDialog = false
            },
            onDismiss = {
                resultMessage = "Modal dismissed; focus should return to the launcher control."
                showDialog = false
            },
            confirmModifier = Modifier.onFocusChanged { if (it.isFocused) recordFocus("Modal confirm") },
            dismissModifier = Modifier.onFocusChanged { if (it.isFocused) recordFocus("Modal cancel") },
        )
    }
}
