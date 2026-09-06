package com.astrawave.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.data.SportsGuideItem

/**
 * Public compatibility surfaces kept for the debug visual-QA activity.
 * They intentionally mirror the production Sports featured/schedule hierarchy so QA can
 * continue checking long titles, focus treatment and ten-foot readability after the
 * event-command-center rewrite.
 */
@Composable
fun AstraWaveFeaturedSportsCard(
    item: SportsGuideItem,
    multiviewCount: Int,
    onPlay: (List<String>) -> Unit,
    onAddToMultiview: (MultiviewPane) -> Unit,
) {
    val candidates = item.resolution?.candidates.orEmpty()
    val urls = candidates.map { it.streamUrl }.distinct()
    val best = item.watchCandidate
    AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        item.event.isLive -> "● LIVE"
                        item.event.isFinal -> "FINAL"
                        best != null -> "WATCH READY"
                        else -> "UPCOMING"
                    },
                    color = if (item.event.isLive) AstraWaveColors.Live else if (best != null) AstraWaveColors.Success else AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    item.event.time?.takeIf(String::isNotBlank) ?: item.event.date?.takeIf(String::isNotBlank) ?: "Scheduled",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(item.event.league, item.event.sport).joinToString(" • ").ifBlank { "Sports event" },
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(10.dp))
            SportsQaSourceLine(item)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (urls.isNotEmpty()) AstraWavePrimaryButton("▶ Watch", { onPlay(urls) })
                if (best != null) {
                    AstraWaveSecondaryButton(
                        label = if (multiviewCount >= 6) "Mosaic full" else "＋ Mosaic",
                        enabled = multiviewCount < 6,
                        onClick = {
                            onAddToMultiview(
                                MultiviewPane(
                                    id = "sports:${item.event.id}",
                                    title = item.event.name,
                                    streamUrl = best.streamUrl,
                                    sourceName = best.source,
                                    eventId = item.event.id,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AstraWaveSportsScheduleCard(
    item: SportsGuideItem,
    multiviewCount: Int,
    onPlay: (List<String>) -> Unit,
    onAddToMultiview: (MultiviewPane) -> Unit,
) {
    val candidates = item.resolution?.candidates.orEmpty()
    val urls = candidates.map { it.streamUrl }.distinct()
    val best = item.watchCandidate
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(118.dp)) {
                Text(
                    item.event.time?.takeIf(String::isNotBlank) ?: "Scheduled",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (item.event.isLive) "● LIVE" else if (item.event.isFinal) "FINAL" else "UPCOMING",
                    color = if (item.event.isLive) AstraWaveColors.Live else AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    listOfNotNull(item.event.league, item.event.sport).joinToString(" • "),
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(5.dp))
                SportsQaSourceLine(item)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (urls.isNotEmpty()) AstraWavePrimaryButton("Watch", { onPlay(urls) })
                if (best != null) {
                    AstraWaveSecondaryButton(
                        label = "Mosaic",
                        enabled = multiviewCount < 6,
                        onClick = {
                            onAddToMultiview(
                                MultiviewPane(
                                    id = "sports:${item.event.id}",
                                    title = item.event.name,
                                    streamUrl = best.streamUrl,
                                    sourceName = best.source,
                                    eventId = item.event.id,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SportsQaSourceLine(item: SportsGuideItem) {
    val candidate = item.watchCandidate
    when {
        candidate != null -> {
            val backups = (item.resolution?.candidates?.size ?: 1) - 1
            Text(
                "${candidate.channelName} • ${candidate.source}${if (backups > 0) " • $backups backups" else ""}",
                color = AstraWaveColors.Success,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        item.broadcasterNames.isNotEmpty() -> Text(
            "Broadcast metadata • ${item.broadcasterNames.joinToString()}",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.labelMedium,
        )
        else -> Text("Broadcast data pending", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
    }
}
