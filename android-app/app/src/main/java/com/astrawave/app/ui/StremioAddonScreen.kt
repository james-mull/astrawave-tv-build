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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.InstalledAddon
import com.astrawave.app.data.CloudStreamRepositoryPreference
import com.astrawave.app.data.CloudStreamRepositoryPreferenceStore
import com.astrawave.app.data.StremioAddonStore
import com.astrawave.app.data.StremioCatalogAggregator
import com.astrawave.app.data.StremioCatalogRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StremioAddonScreen(profileId: String = "default") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { StremioAddonStore(context) }
    val repoStore = remember { CloudStreamRepositoryPreferenceStore(context) }
    val aggregator = remember { StremioCatalogAggregator(context) }
    var addons by remember(profileId) { mutableStateOf(store.loadAll()) }
    var repositories by remember(profileId) { mutableStateOf(repoStore.load(profileId)) }
    var catalogRows by remember(profileId) { mutableStateOf<List<StremioCatalogRow>>(emptyList()) }
    var installDialog by remember { mutableStateOf(false) }
    var showVodAuthorization by remember { mutableStateOf(false) }
    var showDebrid by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var loadingCatalogs by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showVodAuthorization) {
        VodProviderAuthorizationScreen(
            profileId = profileId,
            onBack = { showVodAuthorization = false },
        )
        return
    }

    if (showDebrid) {
        DebridAccountScreen(
            profileId = profileId,
            onBack = { showDebrid = false },
        )
        return
    }

    fun refresh() {
        addons = store.loadAll()
        repositories = repoStore.load(profileId)
    }

    suspend fun refreshCatalogs() {
        loadingCatalogs = true
        catalogRows = withContext(Dispatchers.IO) { aggregator.load(profileId, maxItemsPerCatalog = 12) }
        loadingCatalogs = false
    }

    LaunchedEffect(addons, profileId) { refreshCatalogs() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Extensions, Addons & Repositories",
            subtitle = "Web-synced configuration, hardcoded catalog defaults and local controls in one place. Stream-capable community extensions remain authorization-gated.",
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { installDialog = true }) { Text("Install Stremio Addon") }
            AstraWaveSecondaryButton(label = "Authorized VOD Providers", onClick = { showVodAuthorization = true })
            AstraWaveSecondaryButton(label = "Cloud & Debrid", onClick = { showDebrid = true })
            Text(
                "Manage more from AstraWave Web Control Center",
                color = AstraWaveColors.Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            AstraWaveStatePanel("Addon error", it)
        }
        Spacer(Modifier.height(18.dp))

        Text("Stremio-compatible addons", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (addons.isEmpty()) {
            AstraWaveStatePanel(
                "No addons installed",
                "Install a compatible manifest URL on this device or add it from the web Control Center and sync.",
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

        Spacer(Modifier.height(28.dp))
        Text("CloudStream repository registry", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Text(
            "Repository choices sync from the web Control Center. Enabling a repo exposes its configuration but does not automatically trust or execute every plugin inside it.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (repositories.isEmpty()) {
            AstraWaveStatePanel("No repositories configured", "Add or enable CloudStream repositories from AstraWave Web.")
        } else {
            repositories.forEach { repo ->
                RepositoryCard(repo) {
                    repositories = repositories.map { current ->
                        if (current.id == repo.id) current.copy(enabled = !current.enabled) else current
                    }
                    repoStore.save(profileId, repositories)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Enabled Addon Catalogs", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (loadingCatalogs) {
            AstraWaveStatePanel("Loading addon catalogs…", "Refreshing metadata from enabled addons.", loading = true)
        } else if (catalogRows.isEmpty()) {
            AstraWaveStatePanel("No addon catalogs", "Enabled addons with catalog resources will appear here.")
        } else {
            catalogRows.forEach { row -> AddonCatalogPreview(row) }
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
private fun RepositoryCard(repo: CloudStreamRepositoryPreference, onToggle: () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(repo.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (repo.custom) "Custom web-synced repository" else "AstraWave hardcoded repository registry",
                        color = AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    if (repo.enabled) "Enabled" else "Disabled",
                    color = if (repo.enabled) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(repo.url, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            if (repo.extensionHints.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Extensions (${repo.extensionHints.size}): ${repo.extensionHints.joinToString(" • ")}",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (repo.enabled) "Disable repo" else "Enable repo",
                color = AstraWaveColors.Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onToggle),
            )
        }
    }
}

@Composable
private fun AddonCatalogPreview(row: StremioCatalogRow) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column {
            Text("${row.catalog.name} • ${row.addonName}", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(row.catalog.type, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
            row.error?.let {
                Spacer(Modifier.height(5.dp))
                Text("Catalog unavailable: $it", color = AstraWaveColors.Warning, style = MaterialTheme.typography.bodyMedium)
            }
            if (row.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                row.items.take(8).forEach { item ->
                    Text("• ${item.name}${item.releaseInfo?.let { release -> " • $release" }.orEmpty()}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Metadata only • playback remains authorization-gated", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        }
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
