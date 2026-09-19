package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
    flat: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val remoteArtwork = ArtworkRegistry.resolve(title)
    val artworkShape = if (flat) RectangleShape else MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .aspectRatio(kind.aspectRatio)
            .clip(artworkShape)
            .border(if (flat) 0.dp else 1.dp, if (flat) Color.Transparent else AstraWaveColors.Divider.copy(alpha = 0.58f), artworkShape)
            .background(
                Brush.linearGradient(
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
                // Keep the branded fallback underneath the network image. It remains visible while
                // artwork is loading and if Coil cannot decode/fetch the remote image, preventing
                // slow or broken artwork from degrading into an empty card.
                AstraWaveArtworkFallback(title)
                AsyncImage(
                    model = remoteArtwork,
                    contentDescription = "$title artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> AstraWaveArtworkFallback(title)
        }

        // A restrained cinematic vignette gives poster/backdrop rows consistent contrast without
        // baking text into the artwork component or obscuring the image itself.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            AstraWaveColors.Background.copy(alpha = if (kind == AstraWaveArtworkKind.Poster) 0.34f else 0.22f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun AstraWaveArtworkFallback(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(AstraWaveColors.AccentSoft.copy(alpha = 0.28f), Color.Transparent),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "AW",
            color = AstraWaveColors.AccentStrong.copy(alpha = 0.78f),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}