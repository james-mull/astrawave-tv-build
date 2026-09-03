package com.astrawave.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Canonical Phase 2 loading/empty/error surfaces.
 *
 * Feature modules should use these wrappers instead of rendering blank destinations or
 * inventing one-off state treatments. Empty and error states can expose a focused secondary
 * action that works consistently on touch and D-pad devices.
 */
@Composable
fun AstraWaveLoadingState(
    title: String = "Loading",
    message: String,
    modifier: Modifier = Modifier,
) {
    AstraWaveStatePanel(
        title = title,
        message = message,
        loading = true,
        modifier = modifier,
    )
}

@Composable
fun AstraWaveEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AstraWaveActionableState(
        title = title,
        message = message,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun AstraWaveErrorState(
    title: String = "Something went wrong",
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = "Try again",
    onRetry: (() -> Unit)? = null,
) {
    AstraWaveActionableState(
        title = title,
        message = message,
        modifier = modifier,
        actionLabel = retryLabel,
        onAction = onRetry,
    )
}

@Composable
private fun AstraWaveActionableState(
    title: String,
    message: String,
    modifier: Modifier,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AstraWaveStatePanel(
            title = title,
            message = message,
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            AstraWaveSecondaryButton(
                label = actionLabel,
                onClick = onAction,
            )
        }
    }
}
