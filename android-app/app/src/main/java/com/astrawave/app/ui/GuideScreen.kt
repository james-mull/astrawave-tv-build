package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.data.ChannelCustomizationProjection
import com.astrawave.app.data.ChannelCustomizationStore
import com.astrawave.app.data.GuideChannelRow
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.LiveTvRepository
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.SourceFusionPlaybackPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HALF_HOUR_MS = 30L * 60_000L
private const val HOUR_MS = 60L * 60_000L
private val GUIDE_HALF_HOUR_WIDTH = 145.dp
private val GUIDE_CHANNEL_WIDTH = 220.dp

private sealed interface GuideLoadState {
    data object Loading : GuideLoadState
    data class Ready(val snapshot: GuideSnapshot, val customized: Int, val epgOverrides: Int) : GuideLoadState
    data class Error(val message: String) : GuideLoadState
}

private data class GuideProgrammeGeometry(
    val title: String,
    val start: Long,
    val end: Long,
    val leftGap: Dp,
    val width: Dp,
    val current: Boolean,
)

@Composable
fun AstraWaveGuideScreen(
    sources: List<IptvSource>,
    profileId: String = "default",
    repository: GuideRepository = remember { GuideRepository() },
) {
    val context = LocalContext.current
    val device = LocalAstraWaveDeviceClass.current
    val isPhone = device == AstraWaveDeviceClass.PHONE
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (!ProfileSafetyPolicy.liveTvAllowed(safety)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader("Guide", "Live TV and guide access are disabled for this profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveEmptyState("Restricted by profile settings", "A household administrator can enable Live TV for this profile.")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val channelStore = remember { ChannelCustomizationStore(context) }
    val channelProjection = remember { ChannelCustomizationProjection(context) }
    val playbackPlanner = remember { SourceFusionPlaybackPlanner(context) }
    var state by remember(sources, profileId) { mutableStateOf<GuideLoadState>(GuideLoadState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var nowOnly by remember { mutableStateOf(false) }
    var horizonHours by remember { mutableStateOf(6) }

    DisposableEffect(channelStore, profileId) {
        val registration = channelStore.addChangeListener(profileId) { refreshKey += 1 }
        onDispose { registration.close() }
    }

    LaunchedEffect(sources, profileId, refreshKey) {
        state = GuideLoadState.Loading
        state = try {
            val edits = channelStore.load(profileId)
            val overrides = edits.mapNotNull { edit ->
                edit.epgIdOverride?.takeIf(String::isNotBlank)?.let { edit.channelId to it }
            }.toMap()
            val raw = withContext(Dispatchers.IO) { repository.load(sources, overrides) }
            GuideLoadState.Ready(channelProjection.guide(profileId, raw), edits.size, overrides.size)
        } catch (error: Exception) {
            GuideLoadState.Error(error.message ?: "Unable to load guide")
        }
    }

    fun play(row: GuideChannelRow) {
        if (row.playableUrls.isEmpty()) {
            row.externalUrl?.let { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { Toast.makeText(context, "Unable to open this provider.", Toast.LENGTH_LONG).show() }
            }
            return
        }
        scope.launch {
            val plan = withContext(Dispatchers.IO) {
                playbackPlanner.plan(row.playableUrls.mapIndexed { index, url ->
                    SourceFusionPlaybackPlanner.Input(
                        sourceKey = "guide:${row.id}:$index",
                        url = url,
                        provider = row.preferredSource ?: "Live TV",
                        priority = index,
                    )
                })
            }
            val urls = plan?.orderedUrls.orEmpty()
            if (urls.isEmpty()) {
                Toast.makeText(context, "No working stream is available right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(if (isPhone) 16.dp else 24.dp),
    ) {
        Text("ASTRAWAVE LIVE", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        AstraWavePageHeader(
            title = "Guide",
            subtitle = if (isPhone) "What is on now and next." else "Live TV on a true synchronized timeline.",
        )
        Spacer(Modifier.height(if (isPhone) 14.dp else 18.dp))

        when (val current = state) {
            GuideLoadState.Loading -> AstraWaveLoadingState("Building your guide", "Loading channels and schedules.")
            is GuideLoadState.Error -> AstraWaveErrorState("Guide unavailable", current.message, retryLabel = "Refresh", onRetry = { refreshKey += 1 })
            is GuideLoadState.Ready -> {
                val snapshot = current.snapshot
                GuideSummaryRail(snapshot, current.customized, current.epgOverrides)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search channels or programs") },
                )
                Spacer(Modifier.height(8.dp))

                val groups = snapshot.rows.map { it.group ?: "Uncategorized" }.distinct().sorted().take(30)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GuideFilterButton("All", selectedGroup == null) { selectedGroup = null }
                    GuideFilterButton("On now", nowOnly) { nowOnly = !nowOnly }
                    if (!isPhone) listOf(3, 6, 12, 24).forEach { hours ->
                        GuideFilterButton("${hours}h", horizonHours == hours) { horizonHours = hours }
                    }
                    groups.forEach { group ->
                        GuideFilterButton(group, selectedGroup == group) {
                            selectedGroup = if (selectedGroup == group) null else group
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                val normalizedQuery = query.trim().lowercase(Locale.US)
                val visible = snapshot.rows.filter { row ->
                    val group = row.group ?: "Uncategorized"
                    (selectedGroup == null || selectedGroup == group) &&
                        (!nowOnly || row.now != null) &&
                        (normalizedQuery.isBlank() || row.name.lowercase(Locale.US).contains(normalizedQuery) ||
                            row.programmes.any { it.title.lowercase(Locale.US).contains(normalizedQuery) } ||
                            group.lowercase(Locale.US).contains(normalizedQuery))
                }

                if (isPhone) {
                    AstraWaveSectionHeader("Channels", "${visible.size} channels")
                    Spacer(Modifier.height(8.dp))
                    if (visible.isEmpty()) {
                        AstraWaveEmptyState("No guide matches", "Try another search or filter.")
                    } else {
                        visible.take(250).forEach { row ->
                            PhoneGuideCard(row = row, onPlay = { play(row) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else {
                    val timelineScroll = rememberScrollState()
                    val now = System.currentTimeMillis()
                    val windowStart = floorToHalfHour(now)
                    val windowEnd = windowStart + horizonHours * HOUR_MS
                    AstraWaveSectionHeader(
                        title = "Timeline",
                        subtitle = "${visible.size} channels • ${formatGuideTime(windowStart)} to ${formatGuideTime(windowEnd)}",
                    )
                    Spacer(Modifier.height(8.dp))
                    TimelineHeader(windowStart, horizonHours, timelineScroll)
                    if (visible.isEmpty()) {
                        AstraWaveEmptyState("No guide matches", "Try another search, group or time filter.")
                    } else {
                        visible.take(400).forEach { row ->
                            TimelineRow(
                                row = row,
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                timelineScroll = timelineScroll,
                                onPlay = { play(row) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PhoneGuideCard(row: GuideChannelRow, onPlay: () -> Unit) {
    val nowMs = System.currentTimeMillis()
    val parsed = row.programmes.mapNotNull { programme ->
        val start = LiveTvRepository.parseXmlTvEpochMs(programme.start)
        val end = LiveTvRepository.parseXmlTvEpochMs(programme.stop)
        if (start == null || end == null) null else Triple(programme, start, end)
    }.sortedBy { it.second }
    val current = parsed.firstOrNull { nowMs in it.second until it.third }
    val next = parsed.firstOrNull { it.second > nowMs }

    AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(row.group ?: "Live TV", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                }
                if (row.playableUrls.isNotEmpty()) Text("WATCH", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
            }
            current?.let { (programme, start, end) ->
                Text("● LIVE NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
                Text(programme.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text("${formatGuideTime(start)} – ${formatGuideTime(end)}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
            } ?: Text(row.now?.title ?: "No current guide data", color = AstraWaveColors.SecondaryText)
            next?.let { (programme, start, _) ->
                Text("NEXT • ${formatGuideTime(start)} • ${programme.title}", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TimelineHeader(windowStart: Long, hours: Int, timelineScroll: ScrollState) {
    val timelineWidth = timelineWidth(hours)
    val nowOffset = timeWidth((System.currentTimeMillis() - windowStart).coerceAtLeast(0L))
    Row(Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised)) {
        Box(Modifier.width(GUIDE_CHANNEL_WIDTH).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("CHANNEL", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.weight(1f).horizontalScroll(timelineScroll)) {
            Row(Modifier.width(timelineWidth)) {
                repeat(hours * 2) { index ->
                    val time = windowStart + index * HALF_HOUR_MS
                    Box(Modifier.width(GUIDE_HALF_HOUR_WIDTH).padding(horizontal = 10.dp, vertical = 10.dp)) {
                        Text(formatGuideTime(time), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (System.currentTimeMillis() in windowStart..(windowStart + hours * HOUR_MS)) {
                Box(
                    Modifier.offset(x = nowOffset).width(2.dp).height(38.dp).background(AstraWaveColors.Live),
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    row: GuideChannelRow,
    windowStart: Long,
    windowEnd: Long,
    timelineScroll: ScrollState,
    onPlay: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val totalWidth = timeWidth(windowEnd - windowStart)
    val nowOffset = timeWidth((now - windowStart).coerceAtLeast(0L))
    val geometry = buildTimelineGeometry(row, windowStart, windowEnd, now)

    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        AstraWaveFocusableCard(
            Modifier.width(GUIDE_CHANNEL_WIDTH).clickable(onClick = onPlay),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(row.group ?: row.preferredSource ?: "Live TV", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (row.playableUrls.isNotEmpty()) Text("▶ WATCH", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
            }
        }

        Box(Modifier.weight(1f).horizontalScroll(timelineScroll)) {
            Row(Modifier.width(totalWidth).height(92.dp), verticalAlignment = Alignment.CenterVertically) {
                if (geometry.isEmpty()) {
                    Box(Modifier.width(totalWidth).padding(12.dp)) {
                        Text(row.now?.title ?: "No EPG data", color = AstraWaveColors.SecondaryText)
                    }
                } else {
                    geometry.forEach { item ->
                        if (item.leftGap > 0.dp) Spacer(Modifier.width(item.leftGap))
                        AstraWaveFocusableCard(
                            Modifier.width(item.width).height(84.dp).clickable(enabled = item.current, onClick = onPlay),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(
                                    "${formatGuideTime(item.start)} – ${formatGuideTime(item.end)}",
                                    color = if (item.current) AstraWaveColors.Live else AstraWaveColors.TertiaryText,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (item.current && row.playableUrls.isNotEmpty()) {
                                    Text("● LIVE • WATCH", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    val consumed = geometry.sumOf { it.leftGap.value.toDouble() + it.width.value.toDouble() }.dp
                    if (consumed < totalWidth) Spacer(Modifier.width(totalWidth - consumed))
                }
            }
            if (now in windowStart..windowEnd) {
                Box(
                    Modifier.offset(x = nowOffset).width(2.dp).height(92.dp).background(AstraWaveColors.Live),
                )
            }
        }
    }
}

private fun buildTimelineGeometry(
    row: GuideChannelRow,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
): List<GuideProgrammeGeometry> {
    val programmes = row.programmes.mapNotNull { programme ->
        val rawStart = LiveTvRepository.parseXmlTvEpochMs(programme.start) ?: return@mapNotNull null
        val rawEnd = LiveTvRepository.parseXmlTvEpochMs(programme.stop) ?: return@mapNotNull null
        if (rawEnd <= windowStart || rawStart >= windowEnd || rawEnd <= rawStart) return@mapNotNull null
        Triple(programme, maxOf(rawStart, windowStart), minOf(rawEnd, windowEnd))
    }.sortedBy { it.second }

    var cursor = windowStart
    return programmes.mapNotNull { (programme, start, end) ->
        val effectiveStart = maxOf(start, cursor)
        if (end <= effectiveStart) return@mapNotNull null
        val gap = timeWidth((effectiveStart - cursor).coerceAtLeast(0L))
        val width = timeWidth(end - effectiveStart)
        cursor = end
        GuideProgrammeGeometry(
            title = programme.title,
            start = effectiveStart,
            end = end,
            leftGap = gap,
            width = width,
            current = now in effectiveStart until end,
        )
    }
}

private fun floorToHalfHour(value: Long): Long = value - (value % HALF_HOUR_MS)

private fun timeWidth(durationMs: Long): Dp =
    (durationMs.toDouble() / HALF_HOUR_MS.toDouble() * GUIDE_HALF_HOUR_WIDTH.value).coerceAtLeast(0.0).dp

private fun timelineWidth(hours: Int): Dp = GUIDE_HALF_HOUR_WIDTH * (hours * 2)

private fun formatGuideTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(epochMs))

@Composable
private fun GuideSummaryRail(snapshot: GuideSnapshot, customized: Int, epgOverrides: Int) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GuideStatPill("CHANNELS", snapshot.rows.size.toString())
        GuideStatPill("FREE TV", snapshot.freeChannelCount.toString())
        GuideStatPill("MY CHANNELS", snapshot.userChannelCount.toString())
        if (customized > 0) GuideStatPill("CUSTOMIZED", customized.toString())
        
    }
}

@Composable
private fun GuideStatPill(label: String, value: String) {
    Column(Modifier.background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large).padding(horizontal = 18.dp, vertical = 12.dp)) {
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
    ) { Text(label, maxLines = 1) }
}
