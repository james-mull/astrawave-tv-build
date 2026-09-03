package com.astrawave.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Canonical AstraWave operational-state surfaces.
 *
 * Feature modules should use these wrappers instead of rendering blank destinations or
 * inventing one-off state treatments. Actions use the same focus-aware controls as the rest
 * of the shell so recovery remains consistent on touch and D-pad devices.
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
fun AstraWaveOfflineState(
    message: String = "AstraWave cannot reach the network right now. Check your connection and try again.",
    modifier: Modifier = Modifier,
    retryLabel: String? = "Retry",
    onRetry: (() -> Unit)? = null,
) {
    AstraWaveActionableState(
        title = "You're offline",
        message = message,
        modifier = modifier,
        actionLabel = retryLabel,
        onAction = onRetry,
    )
}

@Composable
fun AstraWavePartialDataState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = "Refresh",
    onAction: (() -> Unit)? = null,
) {
    AstraWaveActionableState(
        title = "Some information is unavailable",
        message = message,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun AstraWaveNoSourceState(
    message: String = "No eligible playable source is available for this item.",
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AstraWaveActionableState(
        title = "No source available",
        message = message,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun AstraWaveStaleSourceState(
    message: String,
    modifier: Modifier = Modifier,
    refreshLabel: String? = "Refresh source",
    onRefresh: (() -> Unit)? = null,
) {
    AstraWaveActionableState(
        title = "Source needs refreshing",
        message = message,
        modifier = modifier,
        actionLabel = refreshLabel,
        onAction = onRefresh,
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
