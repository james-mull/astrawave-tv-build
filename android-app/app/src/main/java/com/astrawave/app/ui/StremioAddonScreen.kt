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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.InstalledAddon
import com.astrawave.app.data.StremioAddonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StremioAddonScreen(profileId: String = "default") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { StremioAddonStore(context) }
    var addons by remember(profileId) { mutableStateOf(store.loadAll()) }
    var installDialog by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() { addons = store.loadAll() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Extensions & Addons",
            subtitle = "Install Stremio-compatible addons you trust. AstraWave does not bundle unauthorized stream addons.",
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { installDialog = true }) { Text("Install Addon") }
        error?.let {
            Spacer(Modifier.height(12.dp))
            AstraWaveStatePanel("Addon error", it)
        }
        Spacer(Modifier.height(18.dp))

        if (addons.isEmpty()) {
            AstraWaveStatePanel(
                "No addons installed",
                "Install a compatible manifest URL to add catalogs, metadata, subtitles, or authorized playback providers.",
            )
        } else {
            addons.sortedBy { it.sortOrder }.forEach { addon ->
                AddonCard(
                    addon = addon,
                    activeForProfile = addon.enabled && (addon.enabledProfileIds.isEmpty() || profileId in addon.enabledProfileIds),
                    onToggle = {
                        store.setEnabled(addon.manifest.id, !addon.enabled)
                        refresh()
                    },
                    onRemove = {
                        store.remove(addon.manifest.id)
                        refresh()
                    },
                )
            }
        }
    }

    if (installDialog) {
        InstallAddonDialog(
            installing = installing,
            onDismiss = { if (!installing) installDialog = false },
            onInstall = { url ->
                installing = true
                error = null
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { store.install(url) } }
                    installing = false
                    result.onSuccess {
                        refresh()
                        installDialog = false
                    }.onFailure { throwable ->
                        error = throwable.message ?: "Unable to install addon"
                    }
                }
            },
        )
    }
}

@Composable
private fun AddonCard(
    addon: InstalledAddon,
    activeForProfile: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(addon.manifest.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Text("v${addon.manifest.version} • ${addon.manifest.id}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    if (activeForProfile) "Enabled" else "Disabled",
                    color = if (activeForProfile) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (addon.manifest.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(addon.manifest.description, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
            Spacer(Modifier.height(8.dp))
            val resources = addon.manifest.resources.joinToString { it.name.lowercase() }.ifBlank { "no declared resources" }
            Text("Resources: $resources", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
            Text("Catalogs: ${addon.manifest.catalogs.size}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (addon.enabled) "Disable" else "Enable",
                    color = AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onToggle),
                )
                Text(
                    "Remove",
                    color = AstraWaveColors.Warning,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onRemove),
                )
            }
        }
    }
}

@Composable
private fun InstallAddonDialog(
    installing: Boolean,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install Stremio Addon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the addon manifest URL. AstraWave will load the manifest before saving it.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Manifest URL") },
                    singleLine = true,
                )
                if (installing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Checking manifest…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !installing && url.trim().startsWith("http"),
                onClick = { onInstall(url.trim()) },
            ) { Text("Install") }
        },
        dismissButton = { TextButton(enabled = !installing, onClick = onDismiss) { Text("Cancel") } },
    )
}
