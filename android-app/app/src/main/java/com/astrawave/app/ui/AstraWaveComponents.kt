package com.astrawave.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Shared premium primitives. Screens should stay content-first and avoid repeating chrome. */
@Composable
fun AstraWavePageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val device = LocalAstraWaveDeviceClass.current
    Column(modifier.fillMaxWidth()) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(if (device == AstraWaveDeviceClass.TV) 8.dp else 5.dp))
            Text(
                subtitle,
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(if (device == AstraWaveDeviceClass.TV) 0.78f else 1f),
            )
        }
    }
}

@Composable
fun AstraWaveSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        trailing?.let {
            Spacer(Modifier.width(LocalAstraWaveSpacing.current.md))
            it()
        }
    }
}

enum class AstraWaveStateTone { ACCENT, SUCCESS, WARNING, ERROR, NEUTRAL }

private fun stateToneColor(tone: AstraWaveStateTone): Color = when (tone) {
    AstraWaveStateTone.ACCENT -> AstraWaveColors.Accent
    AstraWaveStateTone.SUCCESS -> AstraWaveColors.Success
    AstraWaveStateTone.WARNING -> AstraWaveColors.Warning
    AstraWaveStateTone.ERROR -> AstraWaveColors.Error
    AstraWaveStateTone.NEUTRAL -> AstraWaveColors.TertiaryText
}

@Composable
fun AstraWaveStatePanel(
    title: String,
    message: String,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    tone: AstraWaveStateTone = AstraWaveStateTone.ACCENT,
) {
    val accent = if (loading) AstraWaveColors.AccentStrong else stateToneColor(tone)
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(AstraWaveColors.SurfaceRaised)
            .border(1.dp, accent.copy(alpha = 0.16f), MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(4.dp).height(42.dp).clip(MaterialTheme.shapes.small)
                .background(accent),
        )
        Spacer(Modifier.width(14.dp))
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AstraWaveFocusableCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val sizing = LocalAstraWaveSizing.current
    val elevationTokens = LocalAstraWaveElevation.current
    val motion = LocalAstraWaveMotion.current
    val scale by animateFloatAsState(
        targetValue = if (focused) sizing.focusScale else 1f,
        animationSpec = tween(durationMillis = motion.focusMs),
        label = "astrawave-focus-scale",
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) elevationTokens.focused else elevationTokens.resting,
        animationSpec = tween(durationMillis = motion.focusMs),
        label = "astrawave-focus-elevation",
    )

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = elevation, shape = MaterialTheme.shapes.large, clip = false)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AstraWaveColors.FocusRing else AstraWaveColors.Divider.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
            )
            .clip(MaterialTheme.shapes.large)
            .background(if (focused) AstraWaveColors.SurfaceFocus else AstraWaveColors.SurfaceRaised)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(16.dp),
    ) { content() }
}

@Composable
fun AstraWaveActionRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(AstraWaveColors.SurfaceRaised)
            .border(1.dp, AstraWaveColors.Divider.copy(alpha = 0.45f), MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.width(LocalAstraWaveSpacing.current.md))
        trailing()
    }
}

@Composable
fun AstraWavePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val sizing = LocalAstraWaveSizing.current
    val motion = LocalAstraWaveMotion.current
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) sizing.focusScale else 1f,
        animationSpec = tween(durationMillis = motion.focusMs),
        label = "astrawave-primary-action-focus-scale",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (focused && enabled) 2.dp else 0.dp,
                color = AstraWaveColors.FocusRing,
                shape = MaterialTheme.shapes.medium,
            )
            .onFocusChanged { focused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = AstraWaveColors.Accent,
            contentColor = AstraWaveColors.PrimaryText,
            disabledContainerColor = AstraWaveColors.SurfaceRaised,
            disabledContentColor = AstraWaveColors.TertiaryText,
        ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 13.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun AstraWaveSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val sizing = LocalAstraWaveSizing.current
    val motion = LocalAstraWaveMotion.current
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) sizing.focusScale else 1f,
        animationSpec = tween(durationMillis = motion.focusMs),
        label = "astrawave-secondary-action-focus-scale",
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(
                width = if (focused && enabled) 2.dp else 1.dp,
                color = if (focused && enabled) AstraWaveColors.FocusRing else AstraWaveColors.Divider,
                shape = MaterialTheme.shapes.medium,
            )
            .onFocusChanged { focused = it.isFocused },
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AstraWaveColors.PrimaryText,
            disabledContentColor = AstraWaveColors.TertiaryText,
        ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 19.dp, vertical = 12.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun AstraWaveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AstraWaveFocusDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = dismissLabel,
    )
}