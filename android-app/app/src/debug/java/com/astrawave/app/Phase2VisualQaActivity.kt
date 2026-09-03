package com.astrawave.app

import android.content.res.Configuration
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
import androidx.compose.ui.unit.dp
import com.astrawave.app.ui.AstraWaveArtwork
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFocusableCard
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWavePrimaryButton
import com.astrawave.app.ui.AstraWaveSecondaryButton
import com.astrawave.app.ui.AstraWaveSectionHeader
import com.astrawave.app.ui.AstraWaveStatePanel
import com.astrawave.app.ui.AstraWaveTheme

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
    val firstFocus = remember { FocusRequester() }
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val deviceClass = when {
        isTv -> "TV / Fire TV"
        configuration.screenWidthDp >= 840 -> "Tablet"
        else -> "Phone"
    }
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

    val visitedExpected = visitedControls.intersect(expectedFocusControls)
    val traversalComplete = visitedExpected.size == expectedFocusControls.size
    val remoteCoverageComplete = requiredRemoteKeys.all { it in remoteKeysSeen }
    val traversalPassed = traversalComplete && remoteCoverageComplete && !disabledFocusViolation
    val missingControls = expectedFocusControls - visitedExpected
    val missingRemoteKeys = requiredRemoteKeys - remoteKeysSeen

    fun recordFocus(label: String) {
        focusedControl = label
        if (label.startsWith("ERROR:")) {
            disabledFocusViolation = true
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
        runCatching { firstFocus.requestFocus() }
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
            subtitle = "$deviceClass • ${configuration.screenWidthDp}×${configuration.screenHeightDp} dp • verify spacing, contrast, focus visibility, clipping, and 10-foot readability.",
        )

        AstraWaveStatePanel(
            title = "Device checklist",
            message = "Run this debug screen on Android phone, tablet, Android TV, and Fire TV. On TV, verify focus is obvious, directional movement is predictable, labels remain readable, and no control is clipped.",
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
                traversalPassed -> "All expected controls were visited, required remote keys were observed, and no disabled-control focus violation occurred. Record the device result and still complete the visual readability/polish check before closing Phase 2."
                isTv && missingRemoteKeys.isNotEmpty() -> "Missing controls: ${missingControls.joinToString().ifBlank { "none" }}. Missing remote input: ${missingRemoteKeys.joinToString()}. TV/Fire TV cannot pass from touch or focus traversal alone; exercise Up, Down, Left, Right, and Select on the real remote."
                else -> "Missing: ${missingControls.joinToString()}. Visit every expected control and confirm the visible focus ring matches the telemetry."
            },
        )
        AstraWaveSecondaryButton(
            label = "Reset traversal test",
            onClick = ::resetTraversal,
        )

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
            title = "States",
            subtitle = "No loading, empty, error, or unavailable destination should degrade into a blank screen.",
        )
        AstraWaveStatePanel(
            title = "Refreshing catalogs",
            message = "Loading state remains readable and visually intentional.",
            loading = true,
        )
        AstraWaveStatePanel(
            title = "Nothing here yet",
            message = "Empty state communicates what happened and what the user can do next.",
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Phase 2 may be marked complete only after the app shell and this QA surface are visually checked on the required device classes and benchmarked against the Nuvio/TiviMate reference quality in the master rebuild plan.",
            color = AstraWaveColors.SecondaryText,
        )
        Spacer(Modifier.height(24.dp))
    }
}
