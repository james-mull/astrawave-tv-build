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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.AudioItem
import com.astrawave.app.core.AudioLibrarySnapshot
import com.astrawave.app.core.AudioSubscription
import com.astrawave.app.core.RadioStation
import com.astrawave.app.data.AstraWaveAudioDiscoveryRepository
import com.astrawave.app.data.AudioLibraryRepository
import com.astrawave.app.data.AudioSourceStore
import com.astrawave.app.data.StreamHealthChecker
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AudioLoadState {
    data object Loading : AudioLoadState
    data class Ready(val snapshot: AudioLibrarySnapshot) : AudioLoadState
    data class Error(val message: String) : AudioLoadState
}

@Composable
fun AudioLibraryScreen(
    profileId: String = "default",
    repository: AudioLibraryRepository = remember { AudioLibraryRepository() },
    discovery: AstraWaveAudioDiscoveryRepository = remember { AstraWaveAudioDiscoveryRepository() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AudioSourceStore(context) }
    var sources by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var state by remember(sources) { mutableStateOf<AudioLoadState>(AudioLoadState.Loading) }
    var addPodcast by remember { mutableStateOf(false) }
    var addRadio by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var discoveryLoading by remember { mutableStateOf(false) }
    var discoveredMusic by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var discoveredPodcasts by remember { mutableStateOf<List<AudioSubscription>>(emptyList()) }
    var discoveredRadio by remember { mutableStateOf<List<RadioStation>>(emptyList()) }

    LaunchedEffect(sources) {
        state = AudioLoadState.Loading
        state = try {
            AudioLoadState.Ready(withContext(Dispatchers.IO) { repository.load(sources.subscriptions, sources.stations) })
        } catch (error: Exception) {
            AudioLoadState.Error(error.message ?: "Unable to load audio library")
        }
    }

    fun play(url: String) {
        context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
    }

    fun playChecked(url: String, unavailableMessage: String) {
        scope.launch {
            val healthy = withContext(Dispatchers.IO) {
                runCatching { StreamHealthChecker.check(url).reachable }.getOrDefault(false)
            }
            if (!healthy) Toast.makeText(context, unavailableMessage, Toast.LENGTH_LONG).show() else play(url)
        }
    }

    fun runDiscovery(mode: String) {
        discoveryLoading = true
        scope.launch {
            val q = query.trim()
            withContext(Dispatchers.IO) {
                when (mode) {
                    "music" -> discoveredMusic = if (q.isBlank()) discovery.discoverMusic() else discovery.searchMusic(q)
                    "podcasts" -> discoveredPodcasts = if (q.isBlank()) discovery.discoverPodcasts() else discovery.searchPodcasts(q)
                    "radio" -> discoveredRadio = if (q.isBlank()) discovery.discoverRadio() else discovery.searchRadio(q)
                }
            }
            discoveryLoading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            "Radio, Music & Podcasts",
            "Discover music previews, podcasts and worldwide radio, then keep your own subscriptions and stations in one persistent library.",
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search songs, artists, podcasts or radio") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { runDiscovery("music") }) { Text("Music") }
            Button(onClick = { runDiscovery("podcasts") }) { Text("Podcasts") }
            Button(onClick = { runDiscovery("radio") }) { Text("Radio") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addPodcast = true }) { Text("Add Podcast") }
            Button(onClick = { addRadio = true }) { Text("Add Radio") }
        }
        Spacer(Modifier.height(18.dp))

        if (discoveryLoading) {
            AstraWaveStatePanel("Discovering audio…", "Searching AstraWave's public music, podcast and radio catalogs.", loading = true)
            Spacer(Modifier.height(18.dp))
        }

        if (discoveredMusic.isNotEmpty()) {
            Text("Music", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Text("Official preview audio", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            discoveredMusic.take(40).forEach { track ->
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        track.mediaUrl?.let(::play)
                    },
                ) {
                    Column {
                        Text(track.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text(track.subtitle ?: "Music", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                        Text("Play preview", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (discoveredPodcasts.isNotEmpty()) {
            Text("Podcast Discovery", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Text("Tap a show to add its publisher RSS feed to My Audio.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            discoveredPodcasts.take(40).forEach { show ->
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        store.saveSubscription(profileId, show)
                        sources = store.load(profileId)
                        Toast.makeText(context, "Added ${show.title}", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Column {
                        Text(show.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text("Podcast • Add to My Audio", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (discoveredRadio.isNotEmpty()) {
            Text("Radio Discovery", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            discoveredRadio.take(50).forEach { station ->
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        playChecked(station.streamUrl, "This radio stream is not reachable right now.")
                    },
                ) {
                    Column {
                        Text(station.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(station.genre, station.country).joinToString(" • ").ifBlank { "Internet radio" }, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                        Text("Check & Play", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Text("My Audio", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        when (val current = state) {
            AudioLoadState.Loading -> Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
                CircularProgressIndicator(color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.padding(6.dp))
                Text("Loading audio library…", color = AstraWaveColors.SecondaryText)
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
                        val playable = !episode.mediaUrl.isNullOrBlank()
                        AstraWaveFocusableCard(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).then(
                                if (playable) Modifier.clickable { play(requireNotNull(episode.mediaUrl)) } else Modifier
                            ),
                        ) {
                            Column {
                                Text(episode.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text(episode.subtitle ?: "Podcast", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                                Text(if (playable) "Play" else "No playable enclosure", color = if (playable) AstraWaveColors.Success else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                if (snapshot.radioStations.isNotEmpty()) {
                    Text("Saved Radio", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    snapshot.radioStations.forEach { station ->
                        AstraWaveFocusableCard(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                playChecked(station.streamUrl, "This radio stream is not reachable right now.")
                            },
                        ) {
                            Column {
                                Text(station.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(station.genre, station.country).joinToString(" • ").ifBlank { "Internet radio" }, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                                Text("Play", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (snapshot.recentEpisodes.isEmpty() && snapshot.radioStations.isEmpty()) {
                    AudioMessage("Your audio library is empty", "Discover a podcast or radio station above, or add your own source.")
                }
            }
        }
    }

    if (addPodcast) {
        AddPodcastDialog(
            onDismiss = { addPodcast = false },
            onSave = { item ->
                store.saveSubscription(profileId, item)
                sources = store.load(profileId)
                addPodcast = false
            },
        )
    }
    if (addRadio) {
        AddRadioDialog(
            onDismiss = { addRadio = false },
            onSave = { item ->
                store.saveStation(profileId, item)
                sources = store.load(profileId)
                addRadio = false
            },
        )
    }
}

@Composable
private fun AddPodcastDialog(onDismiss: () -> Unit, onSave: (AudioSubscription) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Podcast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Podcast name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("RSS feed URL") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && url.startsWith("http"),
                onClick = { onSave(AudioSubscription(UUID.randomUUID().toString(), title.trim(), url.trim())) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddRadioDialog(onDismiss: () -> Unit, onSave: (RadioStation) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Radio Station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Station name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Stream URL") }, singleLine = true)
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.startsWith("http"),
                onClick = { onSave(RadioStation(UUID.randomUUID().toString(), name.trim(), url.trim(), genre.trim().ifBlank { null })) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AudioMessage(title: String, message: String) {
    Column(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
    }
}
