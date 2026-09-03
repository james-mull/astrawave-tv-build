package com.astrawave.app

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
import androidx.compose.ui.unit.dp
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFocusDialog
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWavePrimaryButton
import com.astrawave.app.ui.AstraWaveStatePanel
import com.astrawave.app.ui.AstraWaveTheme

/** Debug-only Phase 2 modal/D-pad QA surface. */
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
    val openButtonFocus = remember { FocusRequester() }
    val expected = remember { setOf("Open modal", "Modal confirm", "Modal cancel") }
    var showDialog by remember { mutableStateOf(false) }
    var visited by remember { mutableStateOf(emptySet<String>()) }
    var focusTrail by remember { mutableStateOf(emptyList<String>()) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AstraWavePageHeader(
            title = "Phase 2 Modal QA",
            subtitle = "Verify initial modal focus, left/right action traversal, focus-ring visibility, Back dismissal, and focus restoration.",
        )
        AstraWaveStatePanel(
            title = if (traversalComplete) "Modal traversal PASS candidate" else "Modal traversal incomplete",
            message = if (traversalComplete) {
                "Open, confirm, and cancel controls were all reached. Still visually verify ring visibility, clipping, readability, Back behavior, and focus restoration on TV/Fire TV."
            } else {
                "Missing: ${missing.joinToString()}. Trail: ${focusTrail.joinToString(" → ").ifBlank { "none yet" }}"
            },
        )
        AstraWaveStatePanel(
            title = "Last result",
            message = resultMessage,
        )
        AstraWavePrimaryButton(
            label = "Open focus-aware modal",
            onClick = { showDialog = true },
            modifier = Modifier
                .focusRequester(openButtonFocus)
                .onFocusChanged { if (it.isFocused) recordFocus("Open modal") },
        )
    }

    if (showDialog) {
        AstraWaveFocusDialog(
            title = "Test focus-aware modal",
            message = "Confirm should receive initial focus. Move to Cancel and back, then verify the visible focus treatment remains obvious from 10 feet.",
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
