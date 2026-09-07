package com.astrawave.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.Recording
import com.astrawave.app.core.RecordingState
import com.astrawave.app.data.LocalDvrGateway
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingsScreen(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val gateway = remember { LocalDvrGateway(context) }
    var recordings by remember(profileId) { mutableStateOf(gateway.recordings(profileId)) }

    fun refresh() { recordings = gateway.recordings(profileId) }

    LaunchedEffect(profileId) {
        while (true) {
            refresh()
            delay(2_000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                AstraWavePageHeader(
                    "Recordings",
                    "Local DVR captures from customer-authorized IPTV sources. Public/free sources are never recording-enabled automatically.",
                )
            }
            AstraWaveSecondaryButton("Back", onBack)
        }

        if (recordings.isEmpty()) {
            AstraWaveEmptyState("No recordings yet", "Enable local DVR for an authorized IPTV source, then schedule a program from Guide.")
            return@Column
        }

        val active = recordings.filter { it.state == RecordingState.SCHEDULED || it.state == RecordingState.RECORDING }
        val completed = recordings.filter { it.state == RecordingState.COMPLETE }
        val issues = recordings.filter { it.state == RecordingState.FAILED || it.state == RecordingState.CANCELED }

        RecordingSection("Scheduled & recording", active, gateway, ::refresh)
        RecordingSection("Completed", completed, gateway, ::refresh)
        RecordingSection("Needs attention", issues, gateway, ::refresh)
    }
}

@Composable
private fun RecordingSection(
    title: String,
    items: List<Recording>,
    gateway: LocalDvrGateway,
    refresh: () -> Unit,
) {
    if (items.isEmpty()) return
    AstraWaveSectionHeader(title, "${items.size} item${if (items.size == 1) "" else "s"}")
    items.forEach { recording -> RecordingRow(recording, gateway, refresh) }
}

@Composable
private fun RecordingRow(recording: Recording, gateway: LocalDvrGateway, refresh: () -> Unit) {
    val context = LocalContext.current
    val request = recording.request
    Column(
        Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.large).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(request.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    "${formatTime(request.startEpochMs)} – ${formatTime(request.endEpochMs)} • ${request.sourceId}",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                recording.state.name.replace('_', ' '),
                color = when (recording.state) {
                    RecordingState.RECORDING -> AstraWaveColors.Live
                    RecordingState.COMPLETE -> AstraWaveColors.Success
                    RecordingState.FAILED -> AstraWaveColors.Warning
                    RecordingState.CANCELED -> AstraWaveColors.TertiaryText
                    RecordingState.SCHEDULED -> AstraWaveColors.Accent
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        recording.error?.takeIf(String::isNotBlank)?.let {
            Text(it, color = AstraWaveColors.Warning, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (recording.state == RecordingState.COMPLETE && !recording.playbackUrl.isNullOrBlank()) {
                Text(
                    "PLAY",
                    color = AstraWaveColors.Accent,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(context, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_URL, recording.playbackUrl)
                                .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, arrayListOf(recording.playbackUrl))
                                .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
                        )
                    },
                )
            }
            if (recording.state == RecordingState.SCHEDULED || recording.state == RecordingState.RECORDING) {
                Text(
                    "CANCEL",
                    color = AstraWaveColors.Warning,
                    modifier = Modifier.clickable {
                        gateway.cancel(request.id)
                        refresh()
                    },
                )
            }
            if (recording.state == RecordingState.FAILED && request.endEpochMs > System.currentTimeMillis()) {
                Text(
                    "RETRY",
                    color = AstraWaveColors.Accent,
                    modifier = Modifier.clickable {
                        if (gateway.retry(request.id) == null) {
                            Toast.makeText(context, "Recording can no longer be retried.", Toast.LENGTH_SHORT).show()
                        }
                        refresh()
                    },
                )
            }
            if (recording.state != RecordingState.RECORDING) {
                Text(
                    "DELETE",
                    color = AstraWaveColors.TertiaryText,
                    modifier = Modifier.clickable {
                        gateway.delete(request.id)
                        refresh()
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

private fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
