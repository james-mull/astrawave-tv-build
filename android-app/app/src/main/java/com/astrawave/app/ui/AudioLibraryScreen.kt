package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioLibrarySnapshot
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import com.astrawave.app.data.AudioLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface AudioLoadState {
    data object Loading : AudioLoadState
    data class Ready(val snapshot: AudioLibrarySnapshot) : AudioLoadState
    data class Error(val message: String) : AudioLoadState
}

@Composable
fun AudioLibraryScreen(
    subscriptions: List<AudioSubscription>,
    stations: List<RadioStation>,
    onPlayEpisode: (AudioItem) -> Unit = {},
    onPlayStation: (RadioStation) -> Unit = {},
    repository: AudioLibraryRepository = remember { AudioLibraryRepository() },
) {
    var state by remember(subscriptions, stations) { mutableStateOf<AudioLoadState>(AudioLoadState.Loading) }

    LaunchedEffect(subscriptions, stations) {
        state = try {
            val snapshot = withContext(Dispatchers.IO) { repository.load(subscriptions, stations) }
            AudioLoadState.Ready(snapshot)
        } catch (error: Exception) {
            AudioLoadState.Error(error.message ?: "Unable to load audio library")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("Music & Podcasts", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Podcasts, video podcasts, radio, favorites and listening progress in one place.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))

        when (val current = state) {
            AudioLoadState.Loading -> {
                Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
                    CircularProgressIndicator(color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.padding(6.dp))
                    Text("Loading audio library…", color = AstraWaveColors.SecondaryText)
                }
            }
            is AudioLoadState.Error -> AudioMessage("Audio unavailable", current.message)
            is AudioLoadState.Ready -> {
                val snapshot = current.snapshot
                AudioMessage(
                    "${snapshot.subscriptions.size} subscriptions • ${snapshot.radioStations.size} radio stations",
                    "${snapshot.recentEpisodes.size} recent episodes available",
                )
                Spacer(Modifier.height(18.dp))

                if (snapshot.recentEpisodes.isNotEmpty()) {
                    Text("Recent Episodes", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    snapshot.recentEpisodes.take(40).forEach { episode ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                .clickable(enabled = !episode.mediaUrl.isNullOrBlank()) { onPlayEpisode(episode) }
                                .padding(16.dp),
                        ) {
                            Text(episode.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                            Text(
                                episode.subtitle ?: "Podcast",
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                if (snapshot.radioStations.isNotEmpty()) {
                    Text("Radio", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    snapshot.radioStations.forEach { station ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                .clickable { onPlayStation(station) }
                                .padding(16.dp),
                        ) {
                            Text(station.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(station.genre, station.country).joinToString(" • ").ifBlank { "Internet radio" },
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                if (snapshot.recentEpisodes.isEmpty() && snapshot.radioStations.isEmpty()) {
                    AudioMessage(
                        "Your audio library is empty",
                        "Add podcast RSS feeds or radio stations from My AstraWave. They will appear here automatically.",
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMessage(title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .padding(18.dp),
    ) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
    }
}
