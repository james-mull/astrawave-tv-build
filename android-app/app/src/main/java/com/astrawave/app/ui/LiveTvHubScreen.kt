package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.CombinedLiveTvSnapshot
import com.astrawave.app.data.LiveChannelGroup
import com.astrawave.app.data.LiveTvPreferenceStore
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LiveTvMode { CHANNELS, SOURCES }
private enum class LiveTvFilter { ALL, FAVORITES, RECENTS }

private sealed interface LiveTvLoadState {
    data object Loading : LiveTvLoadState
    data class Ready(val snapshot: CombinedLiveTvSnapshot) : LiveTvLoadState
    data class Error(val message: String) : LiveTvLoadState
}

@Composable
fun LiveTvHubScreen(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit,
    multiviewCount: Int = 0,
    onAddToMultiview: (MultiviewPane) -> Unit = {},
    onOpenMultiview: () -> Unit = {},
    profileId: String = "default",
    repository: CombinedLiveTvRepository = remember { CombinedLiveTvRepository() },
) {
    val context = LocalContext.current
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (!ProfileSafetyPolicy.liveTvAllowed(safety)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader("Live TV", "Live TV is disabled for this kids profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel(
                "Restricted by profile settings",
                "A household administrator can enable Live TV for this profile from Privacy & Parental Controls.",
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val preferences = remember { LiveTvPreferenceStore(context) }
    var mode by remember { mutableStateOf(LiveTvMode.CHANNELS) }
    var filter by remember { mutableStateOf(LiveTvFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var favoriteIds by remember { mutableStateOf(preferences.favoriteIds()) }
    var recentIds by remember { mutableStateOf(preferences.recentIds()) }
    var state by remember(sources) { mutableStateOf<LiveTvLoadState>(LiveTvLoadState.Loading) }

    LaunchedEffect(sources) {
        state = LiveTvLoadState.Loading
        state = try {
            LiveTvLoadState.Ready(withContext(Dispatchers.IO) { repository.load(sources) })
        } catch (error: Exception) {
            LiveTvLoadState.Error(error.message ?: "Unable to load Live TV")
        }
    }

    fun play(group: LiveChannelGroup) {
        scope.launch {
            val healthyCandidates = withContext(Dispatchers.IO) {
                group.candidates.filter { candidate ->
                    runCatching { StreamHealthChecker.check(candidate.url).reachable }.getOrDefault(false)
                }
            }
            if (healthyCandidates.isEmpty()) {
                Toast.makeText(context, "No working stream is available for this channel right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val urls = ArrayList(healthyCandidates.map { it.url }.distinct())
            recentIds = preferences.markWatched(group.canonicalName)
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, urls)
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    fun openOfficial(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "No app is available to open this official provider.", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            AstraWavePageHeader(
                title = "Live TV",
                subtitle = "Free channels, official provider options, and your IPTV are merged with automatic stream failover.",
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiveModeButton("Watch", mode == LiveTvMode.CHANNELS) { mode = LiveTvMode.CHANNELS }
                LiveModeButton("My Sources", mode == LiveTvMode.SOURCES) { mode = LiveTvMode.SOURCES }
                if (multiviewCount > 0) {
                    LiveModeButton("Multiview ($multiviewCount)", false, onOpenMultiview)
                }
            }
        }

        when (mode) {
            LiveTvMode.SOURCES -> MyIptvScreen(sources = sources, onSourcesChanged = onSourcesChanged)
            LiveTvMode.CHANNELS -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                when (val current = state) {
                    LiveTvLoadState.Loading -> AstraWaveStatePanel(
                        "Getting Live TV ready…",
                        "Loading free TV, official watch options, and your enabled sources.",
                        loading = true,
                    )
                    is LiveTvLoadState.Error -> AstraWaveStatePanel("Live TV unavailable", current.message)
                    is LiveTvLoadState.Ready -> {
                        val snapshot = current.snapshot
                        val totalChannels = snapshot.freeChannelCount + snapshot.userChannelCount
                        AstraWaveStatePanel(
                            "$totalChannels channel candidates loaded",
                            "${snapshot.groups.size} merged channels • ${snapshot.handoffCount} official free-provider options • alternates verified before playback",
                        )
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search channels") },
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LiveFilterButton("All", filter == LiveTvFilter.ALL) { filter = LiveTvFilter.ALL }
                            LiveFilterButton("★ Favorites ${favoriteIds.size}", filter == LiveTvFilter.FAVORITES) { filter = LiveTvFilter.FAVORITES }
                            LiveFilterButton("Recent ${recentIds.size}", filter == LiveTvFilter.RECENTS) { filter = LiveTvFilter.RECENTS }
                        }
                        Spacer(Modifier.height(16.dp))

                        val normalizedQuery = query.trim().lowercase()
                        val groups = snapshot.groups
                            .filter { group ->
                                normalizedQuery.isBlank() ||
                                    group.displayName.lowercase().contains(normalizedQuery) ||
                                    group.candidates.any { candidate ->
                                        candidate.source.lowercase().contains(normalizedQuery) ||
                                            candidate.group.orEmpty().lowercase().contains(normalizedQuery)
                                    }
                            }
                            .filter { group ->
                                when (filter) {
                                    LiveTvFilter.ALL -> true
                                    LiveTvFilter.FAVORITES -> group.canonicalName in favoriteIds
                                    LiveTvFilter.RECENTS -> group.canonicalName in recentIds
                                }
                            }
                            .let { visible ->
                                if (filter == LiveTvFilter.RECENTS) {
                                    visible.sortedBy { group -> recentIds.indexOf(group.canonicalName).let { if (it < 0) Int.MAX_VALUE else it } }
                                } else visible
                            }

                        if (groups.isEmpty()) {
                            AstraWaveStatePanel(
                                "No channels match",
                                when (filter) {
                                    LiveTvFilter.FAVORITES -> "Favorite channels with the star icon, or switch back to All."
                                    LiveTvFilter.RECENTS -> "Channels you watch will appear here automatically."
                                    LiveTvFilter.ALL -> "Try a different search, retry later, or add your own M3U/Xtream source."
                                },
                            )
                        } else {
                            groups.take(500).forEach { group ->
                                val candidate = group.bestCandidate
                                val isFavorite = group.canonicalName in favoriteIds
                                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        if (isFavorite) "★" else "☆",
                                                        color = if (isFavorite) AstraWaveColors.Accent else AstraWaveColors.TertiaryText,
                                                        modifier = Modifier.clickable {
                                                            favoriteIds = preferences.toggleFavorite(group.canonicalName)
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(
                                                        group.displayName,
                                                        color = AstraWaveColors.PrimaryText,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 2,
                                                    )
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    candidate?.source ?: "Source unavailable",
                                                    color = AstraWaveColors.Accent,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                            if (candidate != null) {
                                                val multiviewEligible = PlayerActivity.isDirectMediaUrl(candidate.url)
                                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    Text(
                                                        "Watch",
                                                        color = AstraWaveColors.Success,
                                                        modifier = Modifier.clickable { play(group) },
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                    if (multiviewEligible) {
                                                        Text(
                                                            if (multiviewCount >= 4) "Multiview full" else "+ Multiview",
                                                            color = if (multiviewCount >= 4) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText,
                                                            modifier = Modifier.clickable(enabled = multiviewCount < 4) {
                                                                onAddToMultiview(
                                                                    MultiviewPane(
                                                                        id = "live:${group.canonicalName}",
                                                                        title = group.displayName,
                                                                        streamUrl = candidate.url,
                                                                        sourceName = candidate.source,
                                                                        channelId = candidate.id,
                                                                    ),
                                                                )
                                                            },
                                                            style = MaterialTheme.typography.labelLarge,
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text("Unavailable", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        Text(
                                            "${group.candidates.size} stream candidate${if (group.candidates.size == 1) "" else "s"}",
                                            color = AstraWaveColors.TertiaryText,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        group.currentProgram?.title?.let { now ->
                                            Spacer(Modifier.height(8.dp))
                                            Text("Now • $now", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        }
                                        group.nextProgram?.title?.let { next ->
                                            Text("Next • $next", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        if (snapshot.handoffs.isNotEmpty() && filter == LiveTvFilter.ALL && normalizedQuery.isBlank()) {
                            Spacer(Modifier.height(24.dp))
                            Text("Official Free Watch Options", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Free provider destinations that open in their official app or website when a direct in-app stream is not authorized.",
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            snapshot.handoffs.take(40).forEach { handoff ->
                                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(handoff.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                handoff.provider ?: handoff.group,
                                                color = AstraWaveColors.SecondaryText,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                        Text(
                                            "Open Official",
                                            color = AstraWaveColors.Accent,
                                            modifier = Modifier.clickable { openOfficial(handoff.actionUrl) },
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LiveModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.SurfaceRaised,
            contentColor = AstraWaveColors.PrimaryText,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(label)
    }
}

@Composable
private fun LiveFilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.Surface,
            contentColor = AstraWaveColors.PrimaryText,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(label)
    }
}
