package com.astrawave.app

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
    var activationMessage by remember { mutableStateOf("Use touch or D-pad to exercise every control below.") }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
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
            title = "Phase 2 Visual QA",
            subtitle = "${configuration.screenWidthDp}×${configuration.screenHeightDp} dp • verify spacing, contrast, focus visibility, clipping, and 10-foot readability.",
        )

        AstraWaveStatePanel(
            title = "Device checklist",
            message = "Run this debug screen on Android phone, tablet, Android TV, and Fire TV. On TV, verify focus is obvious, directional movement is predictable, labels remain readable, and no control is clipped.",
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
                modifier = Modifier.focusRequester(firstFocus),
            )
            AstraWaveSecondaryButton(
                label = "Add to list",
                onClick = { activationMessage = "Secondary action activated." },
            )
            AstraWaveSecondaryButton(
                label = "Disabled",
                onClick = {},
                enabled = false,
            )
        }
        Text(activationMessage, color = AstraWaveColors.SecondaryText)

        AstraWaveSectionHeader(
            title = "D-pad card row",
            subtitle = "Move left/right through all three cards and confirm focus never disappears or jumps unexpectedly.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("Continue Watching", "Live Now", "Sports Starting Soon").forEach { title ->
                AstraWaveFocusableCard(Modifier.width(240.dp)) {
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
