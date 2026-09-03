package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush

/** Shared artwork geometry and branded fallbacks used before/while remote artwork is unavailable. */
enum class AstraWaveArtworkKind(val aspectRatio: Float) {
    Poster(2f / 3f),
    Backdrop(16f / 9f),
    Square(1f),
}

@Composable
fun AstraWaveArtwork(
    title: String,
    modifier: Modifier = Modifier,
    kind: AstraWaveArtworkKind = AstraWaveArtworkKind.Poster,
    artworkAvailable: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(kind.aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(
                    listOf(AstraWaveColors.SurfaceFocus, AstraWaveColors.BackgroundRaised),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkAvailable && content != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        } else {
            Text(
                text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "AW",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
