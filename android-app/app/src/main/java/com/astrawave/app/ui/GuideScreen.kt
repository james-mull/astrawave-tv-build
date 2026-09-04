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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface GuideLoadState {
    data object Loading : GuideLoadState
    data class Ready(val snapshot: GuideSnapshot) : GuideLoadState
    data class Error(val message: String) : GuideLoadState
}

@Composable
fun AstraWaveGuideScreen(
    sources: List<IptvSource>,
    profileId: String = "default",
    repository: GuideRepository = remember { GuideRepository() },
) {
    val context = LocalContext.current
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (!ProfileSafetyPolicy.liveTvAllowed(safety)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader("Guide", "Live TV and guide access are disabled for this kids profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveEmptyState(
                title = "Restricted by profile settings",
                message = "A household administrator can enable Live TV for this profile from Privacy & Parental Controls.",
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var state by remember(sources) { mutableStateOf<GuideLoadState>(GuideLoadState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var nowOnly by remember { mutableStateOf(false) }

    LaunchedEffect(sources, refreshKey) {
        state = GuideLoadState.Loading
        state = try {
            GuideLoadState.Ready(withContext(Dispatchers.IO) { repository.load(sources) })
        } catch (error: Exception) {
            GuideLoadState.Error(error.message ?: "Unable to load guide")
        }
    }

    fun play(urls: List<String>) {
        scope.launch {
            val healthy = withContext(Dispatchers.IO) {
                urls.distinct().filter { url ->
                    runCatching { StreamHealthChecker.check(url).reachable }.getOrDefault(false)
                }
            }
            if (healthy.isEmpty()) {
                Toast.makeText(context, "No working stream is available for this channel right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val candidates = ArrayList(healthy)
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, candidates.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, candidates)
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    fun openProvider(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Unable to open this provider.", Toast.LENGTH_LONG).show() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text("ASTRAWAVE LIVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(5.dp))
        AstraWavePageHeader(
            title = "Guide",
            subtitle = "Search and filter your merged free TV, official provider channels, and personal IPTV lineup.",
        )
        Spacer(Modifier.height(22.dp))

        when (val current = state) {
            GuideLoadState.Loading -> AstraWaveLoadingState(
                title = "Building your guide",
                message = "Loading channels, official provider options, EPG data and alternate source matches.",
            )

            is GuideLoadState.Error -> AstraWaveErrorState(
                title = "Guide unavailable",
                message = current.message,
                retryLabel = "Refresh guide",
                onRetry = { refreshKey += 1 },
            )

            is GuideLoadState.Ready -> {
                val snapshot = current.snapshot
                GuideSummaryRail(snapshot)
                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search channels or programs") },
                )
                Spacer(Modifier.height(10.dp))

                val groups = snapshot.rows.map { it.group ?: "Uncategorized" }.distinct().sorted().take(20)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuideFilterButton("All", selectedGroup == null) { selectedGroup = null }
                    GuideFilterButton("On now", nowOnly) { nowOnly = !nowOnly }
                    groups.forEach { group ->
                        GuideFilterButton(group, selectedGroup == group) {
                            selectedGroup = if (selectedGroup == group) null else group
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                val normalizedQuery = query.trim().lowercase()
                val visibleRows = snapshot.rows.filter { row ->
                    val group = row.group ?: "Uncategorized"
                    val matchesGroup = selectedGroup == null || selectedGroup == group
                    val matchesNow = !nowOnly || row.now != null
                    val matchesQuery = normalizedQuery.isBlank() ||
                        row.name.lowercase().contains(normalizedQuery) ||
                        row.now?.title.orEmpty().lowercase().contains(normalizedQuery) ||
                        row.next?.title.orEmpty().lowercase().contains(normalizedQuery) ||
                        group.lowercase().contains(normalizedQuery) ||
                        row.preferredSource.orEmpty().lowercase().contains(normalizedQuery)
                    matchesGroup && matchesNow && matchesQuery
                }

                AstraWaveSectionHeader(
                    title = "Now on TV",
                    subtitle = "${visibleRows.size} channels shown • direct channels are checked before playback and use automatic backup streams.",
                )
                Spacer(Modifier.height(12.dp))

                if (visibleRows.isEmpty()) {
                    AstraWaveEmptyState(
                        title = "No guide matches",
                        message = "Try a different search or channel group, or turn off the On now filter.",
                    )
                } else {
                    visibleRows.take(500).forEach { row ->
                        val actionModifier = when {
                            row.playableUrls.isNotEmpty() -> Modifier.clickable { play(row.playableUrls) }
                            row.externalUrl != null -> Modifier.clickable { openProvider(row.externalUrl) }
                            else -> Modifier
                        }
                        AstraWaveFocusableCard(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .then(actionModifier),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(170.dp)) {
                                    Text(
                                        row.name,
                                        color = AstraWaveColors.PrimaryText,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    GuideSourceBadge(row.preferredSource ?: "Source pending")
                                }

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.now?.title ?: if (row.externalUrl != null) "Official provider channel" else "No current program data",
                                        color = AstraWaveColors.PrimaryText,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                    )
                                    row.next?.title?.let { nextTitle ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Next  •  $nextTitle",
                                            color = AstraWaveColors.SecondaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        row.group ?: "Uncategorized",
                                        color = AstraWaveColors.TertiaryText,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val available = row.playableUrls.isNotEmpty() || row.externalUrl != null
                                    Text(
                                        if (row.externalUrl != null) "OFFICIAL" else if (row.playableUrls.isNotEmpty()) "READY" else "UNAVAILABLE",
                                        color = if (available) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        when {
                                            row.externalUrl != null -> "Open provider"
                                            row.playableUrls.size > 1 -> "Watch • ${row.playableUrls.size} backups"
                                            row.playableUrls.isNotEmpty() -> "Watch"
                                            else -> "No direct candidates"
                                        },
                                        color = AstraWaveColors.SecondaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun GuideSummaryRail(snapshot: GuideSnapshot) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GuideStatPill("CHANNELS", snapshot.sourceGroups.toString())
        GuideStatPill("FREE CANDIDATES", snapshot.freeChannelCount.toString())
        GuideStatPill("PREMIUM FREE", snapshot.handoffCount.toString())
        GuideStatPill("MY IPTV", snapshot.userChannelCount.toString())
    }
}

@Composable
private fun GuideStatPill(label: String, value: String) {
    Column(
        Modifier
            .background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GuideSourceBadge(label: String) {
    Text(
        text = label,
        color = AstraWaveColors.Accent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.medium)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

@Composable
private fun GuideFilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.SurfaceRaised,
            contentColor = AstraWaveColors.PrimaryText,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(label, maxLines = 1)
    }
}
