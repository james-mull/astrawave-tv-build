package com.astrawave.app.ui

import android.content.Context
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AccountSection
import com.astrawave.app.core.DeviceSessionState
import com.astrawave.app.data.DownloadTravelStore
import com.astrawave.app.data.FirebaseCloudRepository
import com.astrawave.app.data.LibraryCloudSync
import com.astrawave.app.data.LocalDeviceSessionGateway
import com.astrawave.app.data.LocalDvrGateway
import com.astrawave.app.data.PlaybackPreferenceStore
import com.astrawave.app.data.PlaybackPreset
import com.astrawave.app.data.SportsPreferenceStore

/**
 * Functional home for account rows that previously had no destination.
 * It only exposes behavior backed by existing stores; unfinished transports/workers are labeled as such.
 */
@Composable
fun AccountUtilityScreen(
    section: AccountSection,
    profileId: String,
    onBack: () -> Unit,
) {
    when (section) {
        AccountSection.DEVICES -> DeviceUtility(profileId, onBack)
        AccountSection.DOWNLOADS_STORAGE -> DownloadsUtility(profileId, onBack)
        AccountSection.NOTIFICATIONS -> NotificationsUtility(profileId, onBack)
        AccountSection.BACKUP_SYNC -> BackupSyncUtility(profileId, onBack)
        AccountSection.PLAYBACK, AccountSection.SUBTITLES_AUDIO, AccountSection.APPEARANCE ->
            PlaybackPreferenceUtility(section, profileId, onBack)
        else -> GenericUtility(section, onBack)
    }
}

@Composable
private fun DeviceUtility(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val gateway = remember { LocalDeviceSessionGateway(context) }
    var devices by remember(profileId) { mutableStateOf(gateway.discover()) }
    var pairingPayload by remember { mutableStateOf("") }
    var generatedPayload by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    UtilityShell("Devices & Remote", "Pairing state and handoff capabilities that exist on this device. Live network remote transport remains capability-gated.", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AstraWavePrimaryButton("Create Pairing Session", {
                generatedPayload = gateway.createPairingSession().qrPayload
                message = "Pairing session created."
            })
        }
        generatedPayload?.let {
            Spacer(Modifier.height(10.dp))
            AstraWaveStatePanel("Pairing payload", it)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = pairingPayload,
            onValueChange = { pairingPayload = it },
            label = { Text("Paste AstraWave pairing payload") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AstraWaveSecondaryButton("Pair Device", {
            val result = runCatching { gateway.pair(pairingPayload.trim()) }
            result.onSuccess {
                devices = gateway.discover()
                pairingPayload = ""
                message = "Paired ${it.name}."
            }.onFailure { message = it.message ?: "Pairing failed" }
        })
        message?.let { Spacer(Modifier.height(10.dp)); Text(it, color = AstraWaveColors.SecondaryText) }
        Spacer(Modifier.height(18.dp))
        AstraWaveSectionHeader("Paired devices", "${devices.size} saved on this device")
        if (devices.isEmpty()) {
            AstraWaveEmptyState("No paired devices", "Create or paste an AstraWave pairing payload to add a companion device.")
        } else {
            devices.forEach { device ->
                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column {
                        Text(device.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text("${device.type.name} • ${device.state.name}", color = if (device.state == DeviceSessionState.CONNECTED) AstraWaveColors.Success else AstraWaveColors.SecondaryText)
                        Text("Remote ${if (device.supportsRemote) "supported" else "off"} • Handoff ${if (device.supportsHandoff) "supported" else "off"} • Cast ${if (device.supportsCasting) "supported" else "not advertised"}", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Remove", color = AstraWaveColors.Warning, modifier = Modifier.clickable {
                            gateway.removeDevice(device.id)
                            devices = gateway.discover()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsUtility(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DownloadTravelStore(context) }
    val dvr = remember { LocalDvrGateway(context) }
    var policy by remember(profileId) { mutableStateOf(store.travelPolicy(profileId)) }
    val downloads = remember(profileId) { store.downloads(profileId) }
    var showRecordings by remember { mutableStateOf(false) }

    if (showRecordings) {
        RecordingsScreen(profileId = profileId, onBack = { showRecordings = false })
        return
    }

    UtilityShell("Downloads & Storage", "Authorized offline downloads, Travel Mode and local DVR recordings stored on this device.", onBack) {
        AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable { showRecordings = true }) {
            Column {
                Text("Recordings", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Text("${dvr.recordings(profileId).size} scheduled, active or completed DVR item${if (dvr.recordings(profileId).size == 1) "" else "s"}", color = AstraWaveColors.SecondaryText)
                Text("Open DVR library", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        UtilityToggle("Travel Mode planning", store.travelSummary(profileId), policy.enabled) { enabled ->
            policy = policy.copy(enabled = enabled)
            store.saveTravelPolicy(profileId, policy)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20, 40).forEach { gb ->
                FilterChip(selected = policy.maxStorageGb == gb, onClick = {
                    policy = policy.copy(maxStorageGb = gb)
                    store.saveTravelPolicy(profileId, policy)
                }, label = { Text("$gb GB") })
            }
        }
        Spacer(Modifier.height(18.dp))
        AstraWaveSectionHeader("Offline downloads", "${downloads.size} stored request${if (downloads.size == 1) "" else "s"}")
        if (downloads.isEmpty()) AstraWaveEmptyState("No downloads queued", "Eligible authorized offline items will appear here when you choose Download on supported media.")
        downloads.take(30).forEach { item ->
            AstraWaveStatePanel(item.title, "${item.state.name} • ${item.progressPercent}%${item.error?.let { " • $it" }.orEmpty()}")
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun NotificationsUtility(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("astrawave_notifications", Context.MODE_PRIVATE) }
    val sports = remember { SportsPreferenceStore(context) }
    var sportsEnabled by remember(profileId) { mutableStateOf(prefs.getBoolean("$profileId:sports", true)) }
    var playbackEnabled by remember(profileId) { mutableStateOf(prefs.getBoolean("$profileId:playback", true)) }
    var sourceHealthEnabled by remember(profileId) { mutableStateOf(prefs.getBoolean("$profileId:sourceHealth", false)) }
    val reminders = remember(profileId) { sports.reminders(profileId) }

    UtilityShell("Notifications", "Choose which local AstraWave events may surface notifications. Game Day reminders are scheduled on-device when notification permission is granted.", onBack) {
        UtilityToggle("Sports reminders", "${reminders.size} saved Game Day reminder${if (reminders.size == 1) "" else "s"}", sportsEnabled) {
            sportsEnabled = it; prefs.edit().putBoolean("$profileId:sports", it).apply()
        }
        UtilityToggle("Playback & downloads", "Playback completion and offline-media status", playbackEnabled) {
            playbackEnabled = it; prefs.edit().putBoolean("$profileId:playback", it).apply()
        }
        UtilityToggle("Source health", "Warn when a preferred source repeatedly degrades", sourceHealthEnabled) {
            sourceHealthEnabled = it; prefs.edit().putBoolean("$profileId:sourceHealth", it).apply()
        }
    }
}

@Composable
private fun BackupSyncUtility(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val cloud = remember { FirebaseCloudRepository(context) }
    val sync = remember { LibraryCloudSync(context) }
    var status by remember(profileId) { mutableStateOf(if (cloud.signedIn) "Signed in • ready to restore profile state" else "Sign in to use cloud restore") }

    UtilityShell("Backup & Sync", "Restore private profile library/config state from AstraWave Cloud. Local data remains the fallback when cloud is unavailable.", onBack) {
        AstraWaveStatePanel("Cloud status", status)
        Spacer(Modifier.height(10.dp))
        AstraWavePrimaryButton("Restore This Profile", {
            status = "Restoring…"
            sync.restore(profileId) { result ->
                status = result.fold(
                    onSuccess = { "Restored ${it.watchlistImported} watchlist • ${it.favoritesImported} favorites • ${it.listsImported} lists • ${it.progressImported} progress records" },
                    onFailure = { it.message ?: "Cloud restore failed" },
                )
            }
        }, enabled = cloud.signedIn)
        Spacer(Modifier.height(10.dp))
        Text("Cloud restore does not upload provider passwords. Xtream/debrid/personal-media secrets stay device-local in encrypted storage.", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlaybackPreferenceUtility(section: AccountSection, profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PlaybackPreferenceStore(context) }
    var settings by remember(profileId) { mutableStateOf(store.load(profileId)) }

    fun save(next: com.astrawave.app.data.PlaybackPreferences) {
        settings = next
        store.save(profileId, next)
    }

    UtilityShell(section.title, "Profile-scoped playback, language and presentation policy used across AstraWave.", onBack) {
        when (section) {
            AccountSection.PLAYBACK -> {
                ChoiceRow(
                    "Playback preset",
                    listOf("Auto", "Best Quality", "Fastest Start", "Data Saver", "HDR Preferred", "Surround Preferred", "Debrid Only", "Direct Only"),
                    presetLabel(settings.preset),
                ) { label -> save(settings.copy(preset = presetForLabel(label))) }
                Spacer(Modifier.height(8.dp))
                UtilityToggle("Prefer Real-Debrid / debrid", "Boost eligible debrid-backed sources in Play Best ranking", settings.preferDebrid) { save(settings.copy(preferDebrid = it)) }
                UtilityToggle("Prefer HDR / Dolby Vision", "Boost HDR-capable candidates when metadata advertises it", settings.preferHdr) { save(settings.copy(preferHdr = it)) }
                UtilityToggle("Prefer surround / Atmos", "Boost 5.1, 7.1, Atmos, TrueHD and DTS candidates", settings.preferSurround) { save(settings.copy(preferSurround = it)) }
                UtilityToggle("AutoNext", "Prepares the next episode policy and continues series playback", settings.autoNextEnabled) { save(settings.copy(autoNextEnabled = it)) }
                UtilityToggle("Skip Intro", "Allow intro skipping when timing metadata is available", settings.skipIntroEnabled) { save(settings.copy(skipIntroEnabled = it)) }
                UtilityToggle("Skip Recap", "Allow recap skipping when timing metadata is available", settings.skipRecapEnabled) { save(settings.copy(skipRecapEnabled = it)) }
                UtilityToggle("Skip Credits", "Allow next-episode actions during credits when timing metadata is available", settings.skipCreditsEnabled) { save(settings.copy(skipCreditsEnabled = it)) }
            }
            AccountSection.SUBTITLES_AUDIO -> {
                ChoiceRow("Preferred audio language", languageOptions, displayLanguage(settings.preferredAudioLanguage)) {
                    save(settings.copy(preferredAudioLanguage = languageCode(it)))
                }
                ChoiceRow("Preferred subtitle language", languageOptions, displayLanguage(settings.preferredSubtitleLanguage)) {
                    save(settings.copy(preferredSubtitleLanguage = languageCode(it)))
                }
                UtilityToggle("Subtitles on by default", "Enable subtitles automatically when a preferred track exists", settings.subtitlesEnabledByDefault) { save(settings.copy(subtitlesEnabledByDefault = it)) }
                UtilityToggle("Prefer forced subtitles", "Prioritize forced/foreign-dialogue tracks when available", settings.preferForcedSubtitles) { save(settings.copy(preferForcedSubtitles = it)) }
                UtilityToggle("Prefer hearing-impaired subtitles", "Prefer SDH/HI tracks when available", settings.preferHearingImpairedSubtitles) { save(settings.copy(preferHearingImpairedSubtitles = it)) }
                UtilityToggle("Prefer surround audio", "Prefer multi-channel audio on capable devices", settings.preferSurround) { save(settings.copy(preferSurround = it)) }
            }
            AccountSection.APPEARANCE -> {
                ChoiceRow("Guide density", listOf("Compact", "Balanced", "Comfortable"), densityLabel(settings.guideDensity)) {
                    val density = when (it) { "Compact" -> 1; "Comfortable" -> 3; else -> 2 }
                    save(settings.copy(guideDensity = density))
                }
                ChoiceRow("UI scale", listOf("85%", "100%", "110%", "125%"), "${settings.uiScalePercent}%") {
                    save(settings.copy(uiScalePercent = it.removeSuffix("%").toIntOrNull()?.coerceIn(85, 125) ?: 100))
                }
                AstraWaveStatePanel(
                    "Per-device design remains automatic",
                    "Phone, tablet and Android TV keep separate density, typography and focus behavior. UI scale fine-tunes that device-specific layout rather than forcing one layout everywhere.",
                )
            }
            else -> Unit
        }
    }
}

private val languageOptions = listOf("Auto", "English", "Spanish", "French", "German", "Portuguese", "Japanese", "Korean", "Off")

private fun presetLabel(preset: PlaybackPreset): String = when (preset) {
    PlaybackPreset.AUTO -> "Auto"
    PlaybackPreset.BEST_QUALITY -> "Best Quality"
    PlaybackPreset.FASTEST_START -> "Fastest Start"
    PlaybackPreset.DATA_SAVER -> "Data Saver"
    PlaybackPreset.HDR_PREFERRED -> "HDR Preferred"
    PlaybackPreset.SURROUND_PREFERRED -> "Surround Preferred"
    PlaybackPreset.DEBRID_ONLY -> "Debrid Only"
    PlaybackPreset.DIRECT_ONLY -> "Direct Only"
}

private fun presetForLabel(label: String): PlaybackPreset = when (label) {
    "Best Quality" -> PlaybackPreset.BEST_QUALITY
    "Fastest Start" -> PlaybackPreset.FASTEST_START
    "Data Saver" -> PlaybackPreset.DATA_SAVER
    "HDR Preferred" -> PlaybackPreset.HDR_PREFERRED
    "Surround Preferred" -> PlaybackPreset.SURROUND_PREFERRED
    "Debrid Only" -> PlaybackPreset.DEBRID_ONLY
    "Direct Only" -> PlaybackPreset.DIRECT_ONLY
    else -> PlaybackPreset.AUTO
}

private fun densityLabel(value: Int): String = when (value) { 1 -> "Compact"; 3 -> "Comfortable"; else -> "Balanced" }

private fun languageCode(label: String): String = when (label) {
    "English" -> "en"
    "Spanish" -> "es"
    "French" -> "fr"
    "German" -> "de"
    "Portuguese" -> "pt"
    "Japanese" -> "ja"
    "Korean" -> "ko"
    "Off" -> "off"
    else -> "auto"
}

private fun displayLanguage(code: String): String = when (code.lowercase()) {
    "en" -> "English"
    "es" -> "Spanish"
    "fr" -> "French"
    "de" -> "German"
    "pt" -> "Portuguese"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "off" -> "Off"
    else -> "Auto"
}

@Composable
private fun GenericUtility(section: AccountSection, onBack: () -> Unit) {
    UtilityShell(section.title, "This area is capability-gated until its underlying transport is production-ready.", onBack) {
        AstraWaveStatePanel("Not enabled yet", "AstraWave keeps unfinished transports visible in the roadmap but does not label them as working integrations.")
    }
}

@Composable
private fun UtilityShell(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { AstraWavePageHeader(title, subtitle) }
            Text("Back", color = AstraWaveColors.Accent, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun UtilityToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.large).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) }) }
        }
    }
}
