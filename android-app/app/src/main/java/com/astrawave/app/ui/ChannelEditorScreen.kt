package com.astrawave.app.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.ChannelCustomization
import com.astrawave.app.core.IptvSource
import com.astrawave.app.data.ChannelCustomizationStore
import com.astrawave.app.data.GuideChannelRow
import com.astrawave.app.data.GuideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AstraWaveChannelEditorScreen(
    sources: List<IptvSource>,
    profileId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ChannelCustomizationStore(context) }
    val guide = remember { GuideRepository() }
    var rows by remember(sources, profileId) { mutableStateOf<List<GuideChannelRow>>(emptyList()) }
    var loading by remember(sources) { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<GuideChannelRow?>(null) }
    var revision by remember { mutableStateOf(0) }

    LaunchedEffect(sources, revision) {
        loading = true
        rows = withContext(Dispatchers.IO) {
            runCatching { guide.load(sources).rows }.getOrDefault(emptyList())
        }
        loading = false
    }

    if (selected != null) {
        ChannelEditDetail(
            row = selected!!,
            profileId = profileId,
            store = store,
            onBack = { selected = null },
            onSaved = {
                revision += 1
                selected = null
            },
        )
        return
    }

    val customizations = remember(profileId, revision) { store.load(profileId).associateBy { it.channelId } }
    val normalized = query.trim().lowercase()
    val visible = rows.filter { row ->
        normalized.isBlank() || row.name.lowercase().contains(normalized) || row.group.orEmpty().lowercase().contains(normalized)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AstraWavePageHeader(
                title = "Channel Editor",
                subtitle = "Rename, number, regroup, hide, repair logos, and override EPG IDs per profile.",
                modifier = Modifier.weight(1f),
            )
            AstraWaveSecondaryButton("← Sources", onBack)
        }

        AstraWaveStatePanel(
            title = "${customizations.size} customized channels",
            message = "Edits change AstraWave presentation and guide mapping only. They never create or authorize a stream.",
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search channels or groups") },
        )

        if (loading) {
            AstraWaveLoadingState("Loading channels", "Building the merged lineup for editing.")
        } else if (visible.isEmpty()) {
            AstraWaveEmptyState("No channels found", "Change the search or connect a Live TV source first.")
        } else {
            visible.take(800).forEach { row ->
                val customization = customizations[row.id]
                AstraWaveFocusableCard(
                    Modifier.fillMaxWidth().clickable { selected = row },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                customization?.customName ?: row.name,
                                color = if (customization?.hidden == true) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                            )
                            Text(
                                listOfNotNull(
                                    customization?.customNumber?.let { "#$it" },
                                    customization?.customGroup ?: row.group,
                                    row.preferredSource,
                                ).joinToString(" • "),
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                        Text(
                            when {
                                customization?.hidden == true -> "HIDDEN"
                                customization != null -> "CUSTOM"
                                else -> "EDIT"
                            },
                            color = if (customization != null) AstraWaveColors.Accent else AstraWaveColors.TertiaryText,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelEditDetail(
    row: GuideChannelRow,
    profileId: String,
    store: ChannelCustomizationStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val existing = remember(profileId, row.id) { store.load(profileId).firstOrNull { it.channelId == row.id } }
    var name by remember { mutableStateOf(existing?.customName.orEmpty()) }
    var number by remember { mutableStateOf(existing?.customNumber?.toString().orEmpty()) }
    var group by remember { mutableStateOf(existing?.customGroup.orEmpty()) }
    var epgId by remember { mutableStateOf(existing?.epgIdOverride.orEmpty()) }
    var logo by remember { mutableStateOf(existing?.logoUrlOverride.orEmpty()) }
    var hidden by remember { mutableStateOf(existing?.hidden ?: false) }
    var sortOrder by remember { mutableStateOf(existing?.sortOrder?.takeIf { it != 0 }?.toString().orEmpty()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AstraWavePageHeader(
                title = row.name,
                subtitle = listOfNotNull(row.group, row.preferredSource).joinToString(" • "),
                modifier = Modifier.weight(1f),
            )
            AstraWaveSecondaryButton("← Back", onBack)
        }

        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Custom channel name") }, singleLine = true)
        OutlinedTextField(number, { number = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Channel number") }, singleLine = true)
        OutlinedTextField(group, { group = it }, Modifier.fillMaxWidth(), label = { Text("Custom group") }, singleLine = true)
        OutlinedTextField(epgId, { epgId = it }, Modifier.fillMaxWidth(), label = { Text("EPG / tvg-id override") }, singleLine = true)
        OutlinedTextField(logo, { logo = it }, Modifier.fillMaxWidth(), label = { Text("Logo URL override") }, singleLine = true)
        OutlinedTextField(sortOrder, { sortOrder = it.filter { ch -> ch.isDigit() || ch == '-' } }, Modifier.fillMaxWidth(), label = { Text("Sort order") }, singleLine = true)

        AstraWaveActionRow(
            title = "Hide channel",
            subtitle = "Remove this channel from your profile's Live TV and Guide views.",
        ) {
            Switch(checked = hidden, onCheckedChange = { hidden = it })
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AstraWavePrimaryButton("Save channel") {
                store.save(
                    profileId,
                    ChannelCustomization(
                        channelId = row.id,
                        customName = name.trim().takeIf(String::isNotBlank),
                        customNumber = number.toIntOrNull(),
                        customGroup = group.trim().takeIf(String::isNotBlank),
                        hidden = hidden,
                        sortOrder = sortOrder.toIntOrNull() ?: 0,
                        epgIdOverride = epgId.trim().takeIf(String::isNotBlank),
                        logoUrlOverride = logo.trim().takeIf(String::isNotBlank),
                    ),
                )
                onSaved()
            }
            if (existing != null) {
                AstraWaveSecondaryButton("Reset") {
                    store.remove(profileId, row.id)
                    onSaved()
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AstraWaveStatePanel(
            title = "Guide mapping",
            message = "Current source name: ${row.name} • current group: ${row.group ?: "Uncategorized"} • ${row.programmes.size} schedule entries currently linked. Use the EPG override when a provider's channel ID does not match its XMLTV ID.",
        )
    }
}
