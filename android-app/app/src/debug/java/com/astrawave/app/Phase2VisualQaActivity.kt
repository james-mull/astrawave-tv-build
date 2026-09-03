package com.astrawave.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.ui.AstraWaveArtwork
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveEmptyState
import com.astrawave.app.ui.AstraWaveEpgUnavailableState
import com.astrawave.app.ui.AstraWaveErrorState
import com.astrawave.app.ui.AstraWaveFocusableCard
import com.astrawave.app.ui.AstraWaveLoadingState
import com.astrawave.app.ui.AstraWaveNoSourceState
import com.astrawave.app.ui.AstraWaveOfflineState
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWavePartialDataState
import com.astrawave.app.ui.AstraWavePrimaryButton
import com.astrawave.app.ui.AstraWaveSecondaryButton
import com.astrawave.app.ui.AstraWaveSectionHeader
import com.astrawave.app.ui.AstraWaveStaleSourceState
import com.astrawave.app.ui.AstraWaveStatePanel
import com.astrawave.app.ui.AstraWaveTheme
import com.astrawave.app.ui.AstraWaveUnauthenticatedState

/**
 * Debug-only Phase 2 device QA surface.
 * Launch with:
 * adb shell am start -n com.astrawave.app/.Phase2VisualQaActivity
 *
 * This activity never ships in release builds. It exists so phone/tablet/TV/Fire TV
 * visual polish and D-pad focus can be checked against the Phase 2 exit gate.
 */
class Phase2VisualQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstraWaveTheme {
                Phase2VisualQaScreen()
            }
        }
    }
}

@Composable
private fun Phase2VisualQaScreen() {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
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
    val expectedFocusControls = remember {
        setOf(
            "Actions → Watch now",
            "Actions → Add to list",
            "Cards → Continue Watching",
            "Cards → Live Now",
            "Cards → Sports Starting Soon",
        )
    }
    val requiredRemoteKeys = remember(isTv) {
        if (isTv) setOf("Up", "Down", "Left", "Right", "Select") else emptySet()
    }
    var activationMessage by remember { mutableStateOf("Use touch or D-pad to exercise every control below.") }
    var focusedControl by remember { mutableStateOf("Waiting for focus") }
    var visitedControls by remember { mutableStateOf(emptySet<String>()) }
    var focusTrail by remember { mutableStateOf(emptyList<String>()) }
    var lastRemoteKey by remember { mutableStateOf("None yet") }
    var remoteKeysSeen by remember { mutableStateOf(emptySet<String>()) }
    var disabledFocusViolation by remember { mutableStateOf(false) }
    var visualBenchmarkConfirmed by remember(deviceClass, commitSha) { mutableStateOf(false) }
    var recordedVerifiedCommit by remember(deviceClass) {
        mutableStateOf(verificationStore.verifiedCommit(deviceClass))
    }
    var modalVerifiedCommit by remember(deviceClass) {
        mutableStateOf(verificationStore.modalVerifiedCommit(deviceClass))
    }

    val visitedExpected = visitedControls.intersect(expectedFocusControls)
    val traversalComplete = visitedExpected.size == expectedFocusControls.size
    val remoteCoverageComplete = requiredRemoteKeys.all { it in remoteKeysSeen }
    val traversalPassed = traversalComplete && remoteCoverageComplete && !disabledFocusViolation
    val missingControls = expectedFocusControls - visitedExpected
    val missingRemoteKeys = requiredRemoteKeys - remoteKeysSeen
    val modalExactBuildVerified = verificationStore.isModalVerified(deviceClass) && modalVerifiedCommit == commitSha
    val exactBuildVerified = verificationStore.isVerified(deviceClass) && recordedVerifiedCommit == commitSha

    fun recordFocus(label: String) {
        focusedControl = label
        if (label.startsWith("ERROR:")) {
            disabledFocusViolation = true
            visualBenchmarkConfirmed = false
            return
        }
        visitedControls = visitedControls + label
        focusTrail = (focusTrail + label).takeLast(8)
    }

    fun resetTraversal() {
        activationMessage = "Traversal reset. Use the D-pad to visit every expected control."
        focusedControl = "Waiting for focus"
        visitedControls = emptySet()
        focusTrail = emptyList()
        lastRemoteKey = "None yet"
        remoteKeysSeen = emptySet()
        disabledFocusViolation = false
        visualBenchmarkConfirmed = false
        runCatching { firstFocus.requestFocus() }
    }

    fun refreshModalEvidence() {
        modalVerifiedCommit = verificationStore.modalVerifiedCommit(deviceClass)
        activationMessage = if (
            verificationStore.isModalVerified(deviceClass) && modalVerifiedCommit == commitSha
        ) {
            "Modal QA evidence refreshed: same-build prerequisite is satisfied."
        } else {
            "Modal QA evidence refreshed: same-build prerequisite is still incomplete."
        }
    }

    fun remoteKeyLabel(key: Key): String? = when (key) {
        Key.DirectionUp -> "Up"
        Key.DirectionDown -> "Down"
        Key.DirectionLeft -> "Left"
        Key.DirectionRight -> "Right"
        Key.DirectionCenter, Key.Enter -> "Select"
        Key.Back -> "Back"
        else -> null
    }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    remoteKeyLabel(event.key)?.let { label ->
                        lastRemoteKey = label
                        remoteKeysSeen = remoteKeysSeen + label
                    }
                }
                false
            }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AstraWavePageHeader(
            title = "Phase 2 Visual QA",
            subtitle = "$deviceClass • ${configuration.screenWidthDp}×${configuration.screenHeightDp} dp • build ${commitSha.take(12)} • verify spacing, contrast, focus visibility, clipping, and 10-foot readability.",
        )

        AstraWaveStatePanel(
            title = "Device checklist",
            message = "Run this debug screen separately on Android phone, tablet, Android TV, and Fire TV. On TV, verify focus is obvious, directional movement is predictable, labels remain readable, and no control is clipped.",
        )
        AstraWaveStatePanel(
            title = "Modal focus prerequisite",
            message = when {
                modalExactBuildVerified -> "$deviceClass modal focus QA is recorded for this exact build (${commitSha.take(12)})."
                modalVerifiedCommit != null -> "$deviceClass modal focus QA was recorded on ${modalVerifiedCommit!!.take(12)}, not this build (${commitSha.take(12)}). Open Modal QA and re-run it."
                !commitKnown -> "This local build has no trackable commit SHA, so modal QA cannot satisfy Phase 2 verification."
                else -> "$deviceClass modal focus QA is required for build ${commitSha.take(12)} before final device verification can be recorded."
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AstraWaveSecondaryButton(
                label = "Open Modal QA",
                onClick = { context.startActivity(Intent(context, Phase2ModalQaActivity::class.java)) },
            )
            AstraWaveSecondaryButton(
                label = "Refresh modal QA status",
                onClick = ::refreshModalEvidence,
            )
        }
        AstraWaveStatePanel(
            title = "Verification evidence",
            message = when {
                exactBuildVerified -> "$deviceClass is recorded as verified for this exact build (${commitSha.take(12)}). A newer build must be verified again."
                recordedVerifiedCommit != null -> "$deviceClass was verified on ${recordedVerifiedCommit!!.take(12)}, not this build (${commitSha.take(12)}). Re-run traversal and visual benchmark checks."
                !commitKnown -> "This local build has no trackable commit SHA, so it cannot be recorded as Phase 2 verification evidence."
                else -> "$deviceClass has not been verified for build ${commitSha.take(12)}. Complete modal QA, traversal, and the visual benchmark before recording evidence."
            },
        )
        AstraWaveStatePanel(
            title = "Current D-pad focus",
            message = focusedControl,
        )
        AstraWaveStatePanel(
            title = "D-pad traversal coverage",
            message = "${visitedExpected.size}/${expectedFocusControls.size} expected controls visited • last remote key: $lastRemoteKey • remote keys seen: ${remoteKeysSeen.sorted().joinToString().ifBlank { "none yet" }} • disabled-focus violation: ${if (disabledFocusViolation) "YES" else "no"}. Trail: ${focusTrail.joinToString(" → ").ifBlank { "none yet" }}",
        )
        AstraWaveStatePanel(
            title = when {
                disabledFocusViolation -> "Traversal FAILED"
                traversalPassed -> "Traversal PASS candidate"
                else -> "Traversal incomplete"
            },
            message = when {
                disabledFocusViolation -> "A disabled control received focus. Phase 2 D-pad verification must not pass on this device until that focus-path defect is fixed."
                traversalPassed -> "All expected controls were visited, required remote keys were observed, and no disabled-control focus violation occurred. Now compare hierarchy, readability, focus behavior, artwork, Guide/Live TV presentation, and sports presentation against the Phase 2 Nuvio/TiviMate benchmark before recording this device."
                isTv && missingRemoteKeys.isNotEmpty() -> "Missing controls: ${missingControls.joinToString().ifBlank { "none" }}. Missing remote input: ${missingRemoteKeys.joinToString()}. Android TV/Fire TV cannot pass from touch or focus traversal alone; exercise Up, Down, Left, Right, and Select on the real remote."
                else -> "Missing: ${missingControls.joinToString()}. Visit every expected control and confirm the visible focus ring matches the telemetry."
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AstraWaveSecondaryButton(
                label = "Reset traversal test",
                onClick = ::resetTraversal,
            )
            AstraWaveSecondaryButton(
                label = if (visualBenchmarkConfirmed) "Visual benchmark confirmed" else "Confirm visual benchmark",
                enabled = traversalPassed && !visualBenchmarkConfirmed,
                onClick = {
                    visualBenchmarkConfirmed = true
                    activationMessage = "Visual benchmark confirmed for this device session. Record verification only if the comparison was actually completed."
                },
            )
            AstraWavePrimaryButton(
                label = if (exactBuildVerified) "Build verified" else "Record device verification",
                enabled = traversalPassed && visualBenchmarkConfirmed && modalExactBuildVerified && commitKnown && !exactBuildVerified,
                onClick = {
                    verificationStore.markVerified(deviceClass, commitSha)
                    recordedVerifiedCommit = verificationStore.verifiedCommit(deviceClass)
                    activationMessage = if (verificationStore.isVerified(deviceClass)) {
                        "$deviceClass recorded for build ${commitSha.take(12)}."
                    } else {
                        "$deviceClass was not recorded because a required same-build QA prerequisite is missing."
                    }
                },
            )
            AstraWaveSecondaryButton(
                label = "Clear device verification",
                enabled = recordedVerifiedCommit != null || modalVerifiedCommit != null,
                onClick = {
                    verificationStore.clear(deviceClass)
                    recordedVerifiedCommit = null
                    modalVerifiedCommit = null
                    visualBenchmarkConfirmed = false
                    activationMessage = "$deviceClass verification evidence cleared."
                },
            )
        }

        AstraWaveSectionHeader(
            title = "Actions",
            subtitle = "Primary/secondary controls must share the same focus scale, elevation, ring, typography, and disabled treatment.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AstraWavePrimaryButton(
                label = "Watch now",
                onClick = { activationMessage = "Primary action activated." },
                modifier = Modifier
                    .focusRequester(firstFocus)
                    .onFocusChanged { if (it.isFocused) recordFocus("Actions → Watch now") },
            )
            AstraWaveSecondaryButton(
                label = "Add to list",
                onClick = { activationMessage = "Secondary action activated." },
                modifier = Modifier.onFocusChanged { if (it.isFocused) recordFocus("Actions → Add to list") },
            )
            AstraWaveSecondaryButton(
                label = "Disabled",
                onClick = {},
                enabled = false,
                modifier = Modifier.onFocusChanged { if (it.isFocused) recordFocus("ERROR: disabled action received focus") },
            )
        }
        Text(activationMessage, color = AstraWaveColors.SecondaryText)

        AstraWaveSectionHeader(
            title = "D-pad card row",
            subtitle = "Move left/right through all three cards and confirm the focus telemetry follows the visible ring without disappearing or jumping.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("Continue Watching", "Live Now", "Sports Starting Soon").forEach { title ->
                AstraWaveFocusableCard(
                    Modifier
                        .width(240.dp)
                        .onFocusChanged { if (it.isFocused) recordFocus("Cards → $title") },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AstraWaveArtwork(title = title, modifier = Modifier.fillMaxWidth())
                        Text(title, color = AstraWaveColors.PrimaryText)
                        Text(
                            "Confirm poster treatment, padding, text hierarchy, rounded corners, and focused elevation.",
                            color = AstraWaveColors.SecondaryText,
                        )
                    }
                }
            }
        }

        AstraWaveSectionHeader(
            title = "Canonical operational states",
            subtitle = "Every state below must remain readable, intentional, action-safe, and visually consistent on each required device class.",
        )
        AstraWaveLoadingState(
            title = "Refreshing catalogs",
            message = "Loading state remains readable and visually intentional.",
        )
        AstraWaveEmptyState(
            title = "Nothing here yet",
            message = "Empty state communicates what happened and offers a safe next action.",
            actionLabel = "Browse movies",
            onAction = { activationMessage = "Empty-state action activated." },
        )
        AstraWaveErrorState(
            title = "Source unavailable",
            message = "Errors explain failure without implying unavailable media is playable.",
            retryLabel = "Try again",
            onRetry = { activationMessage = "Retry action activated." },
        )
        AstraWaveOfflineState(
            message = "Network access is unavailable. Previously loaded local data may still be usable.",
        )
        AstraWavePartialDataState(
            message = "Some providers responded while others are unavailable; only confirmed data is shown.",
        )
        AstraWaveUnauthenticatedState(
            onAction = { activationMessage = "Sign-in action activated." },
        )
        AstraWaveNoSourceState(
            message = "No eligible playback source is currently available for this item.",
        )
        AstraWaveStaleSourceState(
            message = "This playlist has not refreshed successfully and may be out of date.",
        )
        AstraWaveEpgUnavailableState(
            onRefresh = { activationMessage = "Guide refresh action activated." },
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Phase 2 may be marked complete only after the app shell and this QA surface are visually checked on Android phone, tablet, Android TV, and Fire TV for the exact build being closed, and benchmarked against the Nuvio/TiviMate reference quality in the master rebuild plan.",
            color = AstraWaveColors.SecondaryText,
        )
        Spacer(Modifier.height(24.dp))
    }
}
