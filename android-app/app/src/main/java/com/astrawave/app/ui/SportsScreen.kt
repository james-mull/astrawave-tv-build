package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.astrawave.app.core.IptvSource
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface SportsLoadState {
    data object Loading : SportsLoadState
    data class Ready(val snapshot: SportsGuideSnapshot) : SportsLoadState
    data class Error(val message: String) : SportsLoadState
}

@Composable
fun AstraWaveSportsScreen(
    sources: List<IptvSource>,
    repository: SportsGuideRepository = remember { SportsGuideRepository() },
) {
    var state by remember(sources) { mutableStateOf<SportsLoadState>(SportsLoadState.Loading) }

    LaunchedEffect(sources) {
        state = SportsLoadState.Loading
        state = try {
            val snapshot = withContext(Dispatchers.IO) { repository.load(sources = sources) }
            SportsLoadState.Ready(snapshot)
        } catch (error: Exception) {
            SportsLoadState.Error(error.message ?: "Unable to load sports schedule")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("Sports", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Today's events, broadcaster metadata and matching Watch options from your available channels.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))

        when (val current = state) {
            SportsLoadState.Loading -> SportsInfoCard("Loading sports…", "Checking schedule data and available channels.", true)
            is SportsLoadState.Error -> SportsInfoCard("Sports unavailable", current.message)
            is SportsLoadState.Ready -> {
                SportsInfoCard(
                    "${current.snapshot.events.size} events • ${current.snapshot.date}",
                    "${current.snapshot.combinedChannelGroups} combined channel groups available for broadcaster matching.",
                )
                Spacer(Modifier.height(14.dp))
                if (current.snapshot.events.isEmpty()) {
                    SportsInfoCard("No events returned", "There are no events from the current sports data source for this date.")
                } else {
                    current.snapshot.events.take(100).forEach { item ->
                        val candidate = item.watchCandidate
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                .padding(16.dp),
                        ) {
                            Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                listOfNotNull(item.event.league, item.event.sport, item.event.time).joinToString(" • "),
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (candidate != null) {
                                Text(
                                    "Watch: ${candidate.channelName} • ${candidate.source}",
                                    color = AstraWaveColors.Success,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            } else if (item.broadcasterNames.isNotEmpty()) {
                                Text(
                                    "Broadcaster: ${item.broadcasterNames.joinToString()} • no matching playable channel found",
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            } else {
                                Text(
                                    "Broadcaster data not available yet — schedule shown without a fake Watch button.",
                                    color = AstraWaveColors.TertiaryText,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsInfoCard(title: String, message: String, loading: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .padding(18.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.width(22.dp), color = AstraWaveColors.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
