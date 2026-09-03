package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Reusable visual primitives for the AstraWave shell and feature modules. */
@Composable
fun AstraWavePageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AstraWaveStatePanel(
    title: String,
    message: String,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AstraWaveColors.Accent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(12.dp))
        }
        Column {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Focus primitive for Android TV / Fire TV. The card scales and gains a visible ring without
 * changing layout bounds, which keeps D-pad movement predictable and avoids clipped focus states.
 */
@Composable
fun AstraWaveFocusableCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) LocalAstraWaveSizing.current.focusScale else 1f

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) AstraWaveColors.FocusRing else AstraWaveColors.Surface,
                shape = MaterialTheme.shapes.medium,
            )
            .clip(MaterialTheme.shapes.medium)
            .background(if (focused) AstraWaveColors.SurfaceFocus else AstraWaveColors.Surface)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun AstraWavePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = AstraWaveColors.Accent,
            contentColor = AstraWaveColors.PrimaryText,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
