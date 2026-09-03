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
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface GuideLoadState {
    data object Loading : GuideLoadState
    data class Ready(val snapshot: GuideSnapshot) : GuideLoadState
    data class Error(val message: String) : GuideLoadState
}

@Composable
fun AstraWaveGuideScreen(
    sources: List<IptvSource>,
    repository: GuideRepository = remember { GuideRepository() },
) {
    var state by remember(sources) { mutableStateOf<GuideLoadState>(GuideLoadState.Loading) }

    LaunchedEffect(sources) {
        state = GuideLoadState.Loading
        state = try {
            val snapshot = withContext(Dispatchers.IO) { repository.load(sources) }
            GuideLoadState.Ready(snapshot)
        } catch (error: Exception) {
            GuideLoadState.Error(error.message ?: "Unable to load guide")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("Guide", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "One merged guide across AstraWave Free TV and your IPTV sources.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))

        when (val current = state) {
            GuideLoadState.Loading -> {
                Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp), color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading channels and guide data…", color = AstraWaveColors.SecondaryText)
                }
            }
            is GuideLoadState.Error -> GuideMessage("Guide unavailable", current.message)
            is GuideLoadState.Ready -> {
                val snapshot = current.snapshot
                GuideMessage(
                    "${snapshot.sourceGroups} channels",
                    "${snapshot.freeChannelCount} AstraWave Free TV • ${snapshot.userChannelCount} from My IPTV",
                )
                Spacer(Modifier.height(14.dp))
                if (snapshot.rows.isEmpty()) {
                    GuideMessage(
                        "No channels yet",
                        "Add an IPTV source or wait for the AstraWave Free TV playlist to refresh. The guide will populate automatically.",
                    )
                } else {
                    snapshot.rows.take(150).forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                .padding(16.dp),
                        ) {
                            Column(Modifier.width(150.dp)) {
                                Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text(
                                    row.preferredSource ?: "Source pending",
                                    color = AstraWaveColors.Accent,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.now?.title ?: "No current program data",
                                    color = AstraWaveColors.PrimaryText,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    row.group ?: "Uncategorized",
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                "${row.playableCandidateCount} source${if (row.playableCandidateCount == 1) "" else "s"}",
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideMessage(title: String, message: String) {
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
