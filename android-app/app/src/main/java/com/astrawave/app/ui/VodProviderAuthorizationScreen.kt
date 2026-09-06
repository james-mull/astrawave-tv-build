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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.VodProviderAuthorization
import com.astrawave.app.data.VodProviderAuthorizationStore

/**
 * Explicit customer-authorization manager for VOD providers.
 * Installing or hardcoding a catalog never grants playback permission by itself.
 */
@Composable
fun VodProviderAuthorizationScreen(
    profileId: String = "default",
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember { VodProviderAuthorizationStore(context) }
    var providers by remember(profileId) { mutableStateOf(store.list(profileId)) }
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() { providers = store.list(profileId) }

    Column(
        Modifier.fillMaxSize()
            .background(AstraWaveColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("VOD PROVIDERS", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
                Text("Authorized Playback", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Catalogs can always enrich discovery. Only providers you explicitly authorize here may contribute direct playback streams.",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            onBack?.let { AstraWaveSecondaryButton("Back", it) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AstraWavePrimaryButton("＋ Authorize Provider", { showAdd = true })
        }

        message?.let { AstraWaveStatePanel("Provider authorization", it) }

        if (providers.isEmpty()) {
            AstraWaveStatePanel(
                "No customer providers authorized",
                "AstraWave will still use reviewed public-domain playback sources. Installed community catalogs remain metadata-only until explicitly authorized.",
            )
        } else {
            providers.forEach { provider ->
                AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(provider.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text(provider.host, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                if (provider.enabled) "AUTHORIZED" else "DISABLED",
                                color = if (provider.enabled) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        provider.manifestUrl?.let {
                            Text(it, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Text(
                            if (provider.allowedStreamHosts.isEmpty()) {
                                "Stream hosts: provider may return HTTPS streams from any host"
                            } else {
                                "Stream hosts: ${provider.allowedStreamHosts.sorted().joinToString()}"
                            },
                            color = AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(provider.authorizationLabel, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(
                                if (provider.enabled) "Disable" else "Enable",
                                color = AstraWaveColors.Accent,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable {
                                    store.setEnabled(profileId, provider.id, !provider.enabled)
                                    refresh()
                                },
                            )
                            Text(
                                "Remove",
                                color = AstraWaveColors.Warning,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable {
                                    store.remove(profileId, provider.id)
                                    refresh()
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AstraWaveStatePanel(
            "How AstraWave uses this",
            "The unified VOD resolver searches eligible providers in parallel, verifies stream health, ranks quality and latency, chooses the best source, and sends ordered backups to the player. Disabling a provider immediately removes it from future playback resolution.",
        )
    }

    if (showAdd) {
        AddVodProviderDialog(
            onDismiss = { showAdd = false },
            onSave = { name, manifestUrl, allowedHosts ->
                val host = VodProviderAuthorizationStore.httpsHost(manifestUrl)
                if (host == null) {
                    message = "Use a valid HTTPS manifest URL."
                    return@AddVodProviderDialog
                }
                val id = host.replace(Regex("[^a-z0-9]+"), "-").trim('-')
                store.save(
                    profileId,
                    VodProviderAuthorization(
                        id = id,
                        name = name.ifBlank { host },
                        host = host,
                        manifestUrl = manifestUrl,
                        allowedStreamHosts = allowedHosts,
                    ),
                )
                refresh()
                message = "${name.ifBlank { host }} is authorized for this profile."
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddVodProviderDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Set<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var manifestUrl by remember { mutableStateOf("") }
    var streamHosts by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authorize VOD Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Only authorize a provider you own, subscribe to, or otherwise have permission to use.")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = manifestUrl,
                    onValueChange = { manifestUrl = it },
                    label = { Text("HTTPS manifest URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = streamHosts,
                    onValueChange = { streamHosts = it },
                    label = { Text("Allowed stream hosts (optional, comma separated)") },
                )
                Text(
                    "Leave stream hosts blank only if this provider legitimately serves media from changing CDN hosts.",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = manifestUrl.trim().startsWith("https://", true),
                onClick = {
                    onSave(
                        name.trim(),
                        manifestUrl.trim(),
                        streamHosts.split(',').map(String::trim).filter(String::isNotBlank).map(String::lowercase).toSet(),
                    )
                },
            ) { Text("Authorize") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
