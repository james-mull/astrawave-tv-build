package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.astrawave.app.core.MultiviewLayout
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.MultiviewSession

/** Real 2-up, 3-up and 4-up Media3 multiview with exactly one audible pane. */
@Composable
fun MultiviewScreen(
    session: MultiviewSession,
    onActivateAudio: (String) -> Unit = {},
    onOpenPane: (MultiviewPane) -> Unit = {},
    onReplacePane: (MultiviewPane) -> Unit = {},
) {
    var activeAudioPaneId by remember(session.id) {
        mutableStateOf(session.activeAudioPaneId ?: session.panes.firstOrNull()?.id)
    }

    LaunchedEffect(session.activeAudioPaneId, session.panes) {
        val requested = session.activeAudioPaneId
        activeAudioPaneId = when {
            requested != null && session.panes.any { it.id == requested } -> requested
            activeAudioPaneId != null && session.panes.any { it.id == activeAudioPaneId } -> activeAudioPaneId
            else -> session.panes.firstOrNull()?.id
        }
    }

    fun activate(paneId: String) {
        activeAudioPaneId = paneId
        onActivateAudio(paneId)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("Multiview", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Watch up to four live channels or sports events at once. Only the selected pane plays audio.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(18.dp))

        when (session.layout) {
            MultiviewLayout.TWO_UP -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneOrEmpty(session, 0, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                PaneOrEmpty(session, 1, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
            }
            MultiviewLayout.THREE_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneOrEmpty(session, 0, Modifier.weight(1f).fillMaxWidth(), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(session, 1, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                    PaneOrEmpty(session, 2, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                }
            }
            MultiviewLayout.FOUR_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(session, 0, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                    PaneOrEmpty(session, 1, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(session, 2, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                    PaneOrEmpty(session, 3, Modifier.weight(1f), activeAudioPaneId, ::activate, onOpenPane, onReplacePane)
                }
            }
        }
    }
}

@Composable
private fun PaneOrEmpty(
    session: MultiviewSession,
    index: Int,
    modifier: Modifier,
    activeAudioPaneId: String?,
    onActivateAudio: (String) -> Unit,
    onOpenPane: (MultiviewPane) -> Unit,
    onReplacePane: (MultiviewPane) -> Unit,
) {
    val pane = session.panes.getOrNull(index)
    if (pane == null) {
        Box(
            modifier
                .background(AstraWaveColors.Surface, MaterialTheme.shapes.large)
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Add channel", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val activeAudio = activeAudioPaneId == pane.id
    AstraWaveFocusableCard(
        modifier.clickable { onActivateAudio(pane.id) },
    ) {
        Column {
            MultiviewPlayerSurface(pane = pane, audible = activeAudio, modifier = Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(pane.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(pane.sourceName, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (activeAudio) "Audio active" else "Select audio",
                    color = if (activeAudio) AstraWaveColors.Success else AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Open",
                    color = AstraWaveColors.PrimaryText,
                    modifier = Modifier.clickable { onOpenPane(pane) },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Replace",
                    color = AstraWaveColors.SecondaryText,
                    modifier = Modifier.clickable { onReplacePane(pane) },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun MultiviewPlayerSurface(
    pane: MultiviewPane,
    audible: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(pane.id, pane.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(pane.streamUrl))
            volume = if (audible) 1f else 0f
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(audible, player) {
        player.volume = if (audible) 1f else 0f
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.background(AstraWaveColors.Background),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                this.player = player
            }
        },
        update = { it.player = player },
    )
}
