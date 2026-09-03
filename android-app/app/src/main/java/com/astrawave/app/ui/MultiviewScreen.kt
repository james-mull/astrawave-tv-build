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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.MultiviewLayout
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.MultiviewSession

/**
 * Multiview control surface. Player rendering is intentionally callback-driven so the same
 * session contract can be used by Android phone/tablet, Android TV and Fire TV players.
 */
@Composable
fun MultiviewScreen(
    session: MultiviewSession,
    onActivateAudio: (String) -> Unit = {},
    onOpenPane: (MultiviewPane) -> Unit = {},
    onReplacePane: (MultiviewPane) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("Multiview", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Watch up to four live channels or sports events at once. Select a pane to control audio.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(18.dp))

        when (session.layout) {
            MultiviewLayout.TWO_UP -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneOrEmpty(session, 0, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
                PaneOrEmpty(session, 1, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
            }
            MultiviewLayout.THREE_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneOrEmpty(session, 0, Modifier.weight(1f).fillMaxWidth(), onActivateAudio, onOpenPane, onReplacePane)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(session, 1, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
                    PaneOrEmpty(session, 2, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
                }
            }
            MultiviewLayout.FOUR_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(session, 0, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
                    PaneOrEmpty(session, 1, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(session, 2, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
                    PaneOrEmpty(session, 3, Modifier.weight(1f), onActivateAudio, onOpenPane, onReplacePane)
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

    val activeAudio = session.activeAudioPaneId == pane.id
    Column(
        modifier
            .background(
                if (activeAudio) AstraWaveColors.SurfaceFocus else AstraWaveColors.Surface,
                MaterialTheme.shapes.large,
            )
            .clickable { onActivateAudio(pane.id) }
            .padding(18.dp),
    ) {
        Text(pane.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(pane.sourceName, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(
            if (activeAudio) "Audio active" else "Tap for audio",
            color = if (activeAudio) AstraWaveColors.Success else AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Open",
                color = AstraWaveColors.PrimaryText,
                modifier = Modifier.clickable { onOpenPane(pane) },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Replace",
                color = AstraWaveColors.SecondaryText,
                modifier = Modifier.clickable { onReplacePane(pane) },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
