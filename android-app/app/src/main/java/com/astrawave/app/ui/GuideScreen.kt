package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.CatchUpProgram
import com.astrawave.app.core.DvrEligibility
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.core.RecordingRequest
import com.astrawave.app.data.GuideChannelRow
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.LiveTvRepository
import com.astrawave.app.data.LocalDvrGateway
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        Column(
            Modifier
                .fillMaxSize()
                .background(AstraWaveColors.Background)
                .padding(24.dp),
        ) {
            AstraWavePageHeader(
                title = "Guide",
                subtitle = "Live TV and guide access are disabled for this kids profile.",
            )
            Spacer(Modifier.height(18.dp))
            AstraWaveEmptyState(
                title = "Restricted by profile settings",
                message = "A household administrator can enable Live TV for this profile.",
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val dvr = remember { LocalDvrGateway(context) }
    var state by remember(sources) { mutableStateOf<GuideLoadState>(GuideLoadState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var nowOnly by remember { mutableStateOf(false) }
    var horizonHours by remember { mutableStateOf(6) }

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
                Toast.makeText(context, "No working stream is available right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, healthy.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(healthy))
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    fun openProvider(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Unable to open this provider.", Toast.LENGTH_LONG).show() }
    }

    fun schedule(row: GuideChannelRow, title: String, start: Long, end: Long, series: Boolean) {
        val sourceId = row.preferredSource ?: return
        val request = RecordingRequest(
            id = "rec:${row.id}:$start:${if (series) "series" else "single"}",
            profileId = profileId,
            sourceId = sourceId,
            channelId = row.id,
            title = title,
            startEpochMs = start,
            endEpochMs = end,
            seriesId = if (series) row.id else null,
        )
        runCatching { dvr.schedule(request) }
            .onSuccess {
                Toast.makeText(
                    context,
                    if (series) "Series recording scheduled" else "Recording scheduled",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .onFailure { Toast.makeText(context, it.message ?: "DVR unavailable", Toast.LENGTH_LONG).show() }
    }

    fun catchUp(row: GuideChannelRow, title: String, start: Long, end: Long) {
        val sourceId = row.preferredSource ?: return
        dvr.registerCatchUpPrograms(
            listOf(
                CatchUpProgram(
                    sourceId = sourceId,
                    channelId = row.id,
                    programId = "${row.id}:$start",
                    title = title,
                    startEpochMs = start,
                    endEpochMs = end,
                ),
            ),
        )
        val item = dvr.catchUp(row.id, start, end).firstOrNull()
        if (item == null) {
            Toast.makeText(
                context,
                "This source does not expose authorized catch-up for that program.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            play(listOf(item.playbackUrl))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("ASTRAWAVE LIVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(5.dp))
        AstraWavePageHeader(
            title = "Guide",
            subtitle = "Timeline EPG with merged sources, health-aware playback, catch-up, timeshift and DVR when supported.",
        )
        Spacer(Modifier.height(18.dp))

        when (val current = state) {
            GuideLoadState.Loading -> AstraWaveLoadingState(
                title = "Building your guide",
                message = "Loading channels, schedules and source matches.",
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
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search channels or programs") },
                )
                Spacer(Modifier.height(9.dp))

                val groups = snapshot.rows.map { it.group ?: "Uncategorized" }.distinct().sorted().take(24)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GuideFilterButton("All", selectedGroup == null) { selectedGroup = null }
                    GuideFilterButton("On now", nowOnly) { nowOnly = !nowOnly }
                    listOf(3, 6, 12, 24).forEach { hours ->
                        GuideFilterButton("${hours}h", horizonHours == hours) { horizonHours = hours }
                    }
                    groups.forEach { group ->
                        GuideFilterButton(group, selectedGroup == group) {
                            selectedGroup = if (selectedGroup == group) null else group
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                val normalizedQuery = query.trim().lowercase()
                val visible = snapshot.rows.filter { row ->
                    val group = row.group ?: "Uncategorized"
                    val matchesGroup = selectedGroup == null || selectedGroup == group
                    val matchesNow = !nowOnly || row.now != null
                    val matchesQuery = normalizedQuery.isBlank() ||
                        row.name.lowercase().contains(normalizedQuery) ||
                        row.programmes.any { it.title.lowercase().contains(normalizedQuery) } ||
                        group.lowercase().contains(normalizedQuery)
                    matchesGroup && matchesNow && matchesQuery
                }

                AstraWaveSectionHeader(
                    title = "Timeline",
                    subtitle = "${visible.size} channels • ${horizonHours} hour horizon • past programs can open catch-up when the provider supports it.",
                )
                Spacer(Modifier.height(8.dp))
                TimelineHeader(horizonHours)

                if (visible.isEmpty()) {
                    AstraWaveEmptyState(
                        title = "No guide matches",
                        message = "Try a different search, group or time filter.",
                    )
                } else {
                    visible.take(350).forEach { row ->
                        TimelineRow(
                            row = row,
                            hours = horizonHours,
                            onPlay = {
                                if (row.playableUrls.isNotEmpty()) play(row.playableUrls)
                                else row.externalUrl?.let(::openProvider)
                            },
                            onCatchUp = { title, start, end -> catchUp(row, title, start, end) },
                            onRecord = { title, start, end -> schedule(row, title, start, end, false) },
                            onSeries = { title, start, end -> schedule(row, title, start, end, true) },
                            dvr = dvr,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun TimelineHeader(hours: Int) {
    val now = System.currentTimeMillis()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(AstraWaveColors.BackgroundRaised),
    ) {
        Box(Modifier.width(190.dp).padding(10.dp)) {
            Text("CHANNEL", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
        repeat(hours * 2) { index ->
            val time = now + index * 30L * 60_000L
            Box(Modifier.width(145.dp).padding(10.dp)) {
                Text(formatTime(time), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TimelineRow(
    row: GuideChannelRow,
    hours: Int,
    onPlay: () -> Unit,
    onCatchUp: (String, Long, Long) -> Unit,
    onRecord: (String, Long, Long) -> Unit,
    onSeries: (String, Long, Long) -> Unit,
    dvr: LocalDvrGateway,
) {
    val now = System.currentTimeMillis()
    val horizon = now + hours * 3_600_000L
    val programmes = row.programmes.mapNotNull { programme ->
        val start = LiveTvRepository.parseXmlTvEpochMs(programme.start)
        val end = LiveTvRepository.parseXmlTvEpochMs(programme.stop)
        if (start == null || end == null || end < now - 6 * 3_600_000L || start > horizon) null
        else Triple(programme, start, end)
    }
    val capabilities = row.preferredSource?.let(dvr::capabilities)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .width(190.dp)
                .background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.medium)
                .clickable(onClick = onPlay)
                .padding(11.dp),
        ) {
            Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(row.preferredSource ?: "Source pending", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelSmall)
            if (row.playableUrls.size > 1) {
                Text("${row.playableUrls.size} sources", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (programmes.isEmpty()) {
            Box(Modifier.width(290.dp).padding(12.dp)) {
                Text(row.now?.title ?: "No EPG data", color = AstraWaveColors.SecondaryText)
            }
        } else {
            programmes.take(16).forEach { (programme, start, end) ->
                val past = end <= now
                val current = now in start until end
                val durationMinutes = ((end - start) / 60_000L).coerceAtLeast(15L)
                val width = (durationMinutes / 30.0 * 145.0).coerceIn(100.0, 360.0).dp
                AstraWaveFocusableCard(Modifier.width(width).padding(horizontal = 2.dp)) {
                    Column {
                        Text(programme.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(
                            "${formatTime(start)} – ${formatTime(end)}",
                            color = if (current) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (current) {
                                Text("WATCH", color = AstraWaveColors.Accent, modifier = Modifier.clickable(onClick = onPlay))
                            }
                            if (past && capabilities != null && DvrEligibility.canCatchUp(capabilities)) {
                                Text(
                                    "REPLAY",
                                    color = AstraWaveColors.Accent,
                                    modifier = Modifier.clickable { onCatchUp(programme.title, start, end) },
                                )
                            }
                            if (!past && capabilities?.supportsDvr == true) {
                                Text(
                                    "REC",
                                    color = AstraWaveColors.Warning,
                                    modifier = Modifier.clickable { onRecord(programme.title, start, end) },
                                )
                            }
                            if (!past && capabilities?.supportsSeriesRecording == true) {
                                Text(
                                    "SERIES",
                                    color = AstraWaveColors.Accent,
                                    modifier = Modifier.clickable { onSeries(programme.title, start, end) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.US).format(Date(epochMs))

@Composable
private fun GuideSummaryRail(snapshot: GuideSnapshot) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GuideStatPill("CHANNELS", snapshot.sourceGroups.toString())
        GuideStatPill("FREE", snapshot.freeChannelCount.toString())
        GuideStatPill("OFFICIAL", snapshot.handoffCount.toString())
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
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
    }
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
