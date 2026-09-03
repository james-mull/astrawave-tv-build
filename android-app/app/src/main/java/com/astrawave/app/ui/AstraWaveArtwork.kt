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
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.astrawave.app.data.ArtworkRegistry

/** Shared artwork geometry, remote artwork rendering, and branded fallbacks. */
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
    val remoteArtwork = ArtworkRegistry.resolve(title)
    Box(
        modifier = modifier
            .aspectRatio(kind.aspectRatio)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(AstraWaveColors.SurfaceFocus, AstraWaveColors.BackgroundRaised),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            artworkAvailable && content != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
            }
            !remoteArtwork.isNullOrBlank() -> {
                AsyncImage(
                    model = remoteArtwork,
                    contentDescription = "$title artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                Text(
                    text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "AW",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}
