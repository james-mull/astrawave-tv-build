package com.astrawave.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private enum class AudioBrowseMode(val label: String) {
    MUSIC("Music"),
    PODCASTS("Podcasts"),
    RADIO("Radio"),
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
    var mode by remember { mutableStateOf(AudioBrowseMode.RADIO) }
    var discoveryLoading by remember { mutableStateOf(false) }
    var discoveredMusic by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var discoveredPodcasts by remember { mutableStateOf<List<AudioSubscription>>(emptyList()) }
    var discoveredRadio by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var visibleMusic by remember { mutableIntStateOf(60) }
    var visiblePodcasts by remember { mutableIntStateOf(60) }
    var visibleRadio by remember { mutableIntStateOf(80) }
    var selectedRadioGenre by remember { mutableStateOf<String?>(null) }
    var selectedCountry by remember { mutableStateOf<String?>(null) }

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

    fun discoverCurrent() {
        discoveryLoading = true
        scope.launch {
            val q = query.trim()
            val result = withContext(Dispatchers.IO) {
                when (mode) {
                    AudioBrowseMode.MUSIC -> Triple(
                        if (q.isBlank()) discovery.discoverMusic(maxItems = 500) else discovery.searchMusic(q, 200),
                        emptyList<AudioSubscription>(),
                        emptyList<RadioStation>(),
                    )
                    AudioBrowseMode.PODCASTS -> Triple(
                        emptyList<AudioItem>(),
                        if (q.isBlank()) discovery.discoverPodcasts(maxItems = 500) else discovery.searchPodcasts(q, 200),
                        emptyList<RadioStation>(),
                    )
                    AudioBrowseMode.RADIO -> Triple(
                        emptyList<AudioItem>(),
                        emptyList<AudioSubscription>(),
                        when {
                            q.isNotBlank() -> discovery.searchRadio(q, 300)
                            selectedRadioGenre != null -> discovery.radioByGenre(requireNotNull(selectedRadioGenre), 300)
                            selectedCountry != null -> discovery.radioByCountry(requireNotNull(selectedCountry), 300)
                            else -> discovery.discoverRadio(300)
                        },
                    )
                }
            }
            discoveredMusic = result.first
            discoveredPodcasts = result.second
            discoveredRadio = result.third
            visibleMusic = 60
            visiblePodcasts = 60
            visibleRadio = 80
            discoveryLoading = false
        }
    }

    fun browseMusicGenre(genre: String) {
        mode = AudioBrowseMode.MUSIC
        query = genre
        selectedRadioGenre = null
        selectedCountry = null
        discoverCurrent()
    }

    fun browsePodcastTopic(topic: String) {
        mode = AudioBrowseMode.PODCASTS
        query = topic
        selectedRadioGenre = null
        selectedCountry = null
        discoverCurrent()
    }

    fun browseRadioGenre(genre: String) {
        mode = AudioBrowseMode.RADIO
        query = ""
        selectedCountry = null
        selectedRadioGenre = genre
        discoverCurrent()
    }

    fun browseCountry(country: String) {
        mode = AudioBrowseMode.RADIO
        query = ""
        selectedRadioGenre = null
        selectedCountry = country
        discoverCurrent()
    }

    LaunchedEffect(profileId) {
        if (discoveredRadio.isEmpty()) discoverCurrent()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            "Music, Podcasts & Worldwide Radio",
            "Browse large public directories, search hundreds of results at a time, and keep your own subscriptions and stations in one persistent library.",
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AudioBrowseMode.entries.forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = {
                        mode = item
                        query = ""
                        selectedRadioGenre = null
                        selectedCountry = null
                    },
                    label = { Text(item.label) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = {
                Text(
                    when (mode) {
                        AudioBrowseMode.MUSIC -> "Search songs or artists"
                        AudioBrowseMode.PODCASTS -> "Search podcasts"
                        AudioBrowseMode.RADIO -> "Search radio stations"
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::discoverCurrent) { Text("Search / Browse") }
            if (mode == AudioBrowseMode.PODCASTS) Button(onClick = { addPodcast = true }) { Text("Add Podcast") }
            if (mode == AudioBrowseMode.RADIO) Button(onClick = { addRadio = true }) { Text("Add Radio") }
        }

        Spacer(Modifier.height(16.dp))
        when (mode) {
            AudioBrowseMode.MUSIC -> {
                AstraWaveSectionHeader("Browse Music", "Full commercial playback can be added through licensed provider connections")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    discovery.musicGenres.take(18).forEach { genre ->
                        FilterChip(selected = query.equals(genre, true), onClick = { browseMusicGenre(genre) }, label = { Text(genre) })
                    }
                }
            }
            AudioBrowseMode.PODCASTS -> {
                AstraWaveSectionHeader("Browse Podcasts", "Publisher RSS feeds can be saved directly to My Audio")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    discovery.podcastTopics.take(18).forEach { topic ->
                        FilterChip(selected = query.equals(topic, true), onClick = { browsePodcastTopic(topic) }, label = { Text(topic) })
                    }
                }
            }
            AudioBrowseMode.RADIO -> {
                AstraWaveSectionHeader("Browse Radio", "Worldwide internet radio with source-health checks")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    discovery.radioGenres.take(18).forEach { genre ->
                        FilterChip(selected = selectedRadioGenre == genre, onClick = { browseRadioGenre(genre) }, label = { Text(genre) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    discovery.radioCountries.take(18).forEach { country ->
                        FilterChip(selected = selectedCountry == country, onClick = { browseCountry(country) }, label = { Text(country) })
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        if (discoveryLoading) {
            AstraWaveStatePanel("Discovering audio…", "Searching large music, podcast and radio directories.", loading = true)
            Spacer(Modifier.height(18.dp))
        }

        if (discoveredMusic.isNotEmpty()) {
            AstraWaveSectionHeader("Music", "${discoveredMusic.size} catalog matches • official preview playback where available")
            Spacer(Modifier.height(8.dp))
            discoveredMusic.take(visibleMusic).forEach { track ->
                val playable = !track.mediaUrl.isNullOrBlank()
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).then(
                        if (playable) Modifier.clickable { play(requireNotNull(track.mediaUrl)) } else Modifier,
                    ),
                ) {
                    Column {
                        Text(track.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text(track.subtitle ?: "Music", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                        Text(if (playable) "Play preview" else "Catalog result • full playback requires provider connection", color = if (playable) AstraWaveColors.Success else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (visibleMusic < discoveredMusic.size) {
                AstraWaveSecondaryButton("Show ${minOf(60, discoveredMusic.size - visibleMusic)} more", { visibleMusic += 60 }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
        }

        if (discoveredPodcasts.isNotEmpty()) {
            AstraWaveSectionHeader("Podcast Discovery", "${discoveredPodcasts.size} shows found")
            Spacer(Modifier.height(8.dp))
            discoveredPodcasts.take(visiblePodcasts).forEach { show ->
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        store.saveSubscription(profileId, show)
                        sources = store.load(profileId)
                        Toast.makeText(context, "Added ${show.title}", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Column {
                        Text(show.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text("Podcast • Add publisher RSS feed to My Audio", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (visiblePodcasts < discoveredPodcasts.size) {
                AstraWaveSecondaryButton("Show ${minOf(60, discoveredPodcasts.size - visiblePodcasts)} more", { visiblePodcasts += 60 }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
        }

        if (discoveredRadio.isNotEmpty()) {
            AstraWaveSectionHeader("Radio Discovery", "${discoveredRadio.size} stations loaded")
            Spacer(Modifier.height(8.dp))
            discoveredRadio.take(visibleRadio).forEach { station ->
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
            if (visibleRadio < discoveredRadio.size) {
                AstraWaveSecondaryButton("Show ${minOf(80, discoveredRadio.size - visibleRadio)} more", { visibleRadio += 80 }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
        }

        AstraWaveSectionHeader("My Audio", "Saved podcasts, recent episodes and favorite radio")
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
                    snapshot.recentEpisodes.take(80).forEach { episode ->
                        val playable = !episode.mediaUrl.isNullOrBlank()
                        AstraWaveFocusableCard(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).then(
                                if (playable) Modifier.clickable { play(requireNotNull(episode.mediaUrl)) } else Modifier,
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
