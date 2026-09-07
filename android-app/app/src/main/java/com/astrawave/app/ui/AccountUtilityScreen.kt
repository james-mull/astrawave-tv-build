package com.astrawave.app.ui

import android.content.Context
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
    var policy by remember(profileId) { mutableStateOf(store.travelPolicy(profileId)) }
    val downloads = remember(profileId) { store.downloads(profileId) }

    UtilityShell("Downloads & Storage", "Authorized download queue and Travel Mode planning. The background download worker is not advertised as complete until transport QA is finished.", onBack) {
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
        AstraWaveSectionHeader("Queue", "${downloads.size} stored request${if (downloads.size == 1) "" else "s"}")
        if (downloads.isEmpty()) AstraWaveEmptyState("No downloads queued", "Eligible authorized offline items will appear here when the download worker is enabled.")
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

    UtilityShell("Notifications", "Choose which local AstraWave events may surface notifications. Actual system scheduling remains provider/device capability dependent.", onBack) {
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
    val prefs = remember { context.getSharedPreferences("astrawave_experience", Context.MODE_PRIVATE) }
    fun key(name: String) = "$profileId:$name"
    var quality by remember(profileId) { mutableStateOf(prefs.getString(key("quality"), "Auto") ?: "Auto") }
    var subtitle by remember(profileId) { mutableStateOf(prefs.getString(key("subtitleLanguage"), "English") ?: "English") }
    var density by remember(profileId) { mutableStateOf(prefs.getString(key("density"), "Standard") ?: "Standard") }
    var autoplay by remember(profileId) { mutableStateOf(prefs.getBoolean(key("autoplayBest"), true)) }

    UtilityShell(section.title, "Profile-scoped settings used by AstraWave's native playback and TV presentation layers.", onBack) {
        when (section) {
            AccountSection.PLAYBACK -> {
                UtilityToggle("Autoplay best healthy source", "Use source ranking and failover automatically", autoplay) { autoplay = it; prefs.edit().putBoolean(key("autoplayBest"), it).apply() }
                ChoiceRow("Preferred quality", listOf("Auto","Best","4K","1080p","720p","Data Saver"), quality) { quality = it; prefs.edit().putString(key("quality"), it).apply() }
            }
            AccountSection.SUBTITLES_AUDIO -> ChoiceRow("Subtitle language", listOf("English","Spanish","French","German","Portuguese","Off"), subtitle) { subtitle = it; prefs.edit().putString(key("subtitleLanguage"), it).apply() }
            AccountSection.APPEARANCE -> ChoiceRow("Interface density", listOf("Compact","Standard","Cinematic"), density) { density = it; prefs.edit().putString(key("density"), it).apply() }
            else -> Unit
        }
    }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) }) }
        }
    }
}
