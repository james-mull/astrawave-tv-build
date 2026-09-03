package com.astrawave.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Focus-aware shared modal for TV/Fire TV and touch devices.
 *
 * The primary action receives initial focus and both actions reuse AstraWave's
 * shared focus scale, elevation, ring, typography, and disabled treatment.
 */
@Composable
fun AstraWaveFocusDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    confirmModifier: Modifier = Modifier,
    dismissModifier: Modifier = Modifier,
) {
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { confirmFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = AstraWaveColors.PrimaryText,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = message,
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            AstraWavePrimaryButton(
                label = confirmLabel,
                onClick = onConfirm,
                modifier = confirmModifier.focusRequester(confirmFocusRequester),
            )
        },
        dismissButton = {
            AstraWaveSecondaryButton(
                label = dismissLabel,
                onClick = onDismiss,
                modifier = dismissModifier,
            )
        },
        containerColor = AstraWaveColors.SurfaceRaised,
        titleContentColor = AstraWaveColors.PrimaryText,
        textContentColor = AstraWaveColors.SecondaryText,
    )
}
