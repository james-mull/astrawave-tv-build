package com.astrawave.app.ui

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
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaConnectionStatus
import com.astrawave.app.core.PersonalMediaProvider
import com.astrawave.app.core.PersonalMediaValidation
import com.astrawave.app.data.PersonalMediaStore
import java.util.UUID

@Composable
fun PersonalMediaScreen(profileId: String = "default") {
    val context = LocalContext.current
    val store = remember { PersonalMediaStore(context) }
    var connections by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var showAdd by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() { connections = store.load(profileId) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Personal Media",
            subtitle = "Connect your own Plex, Jellyfin, Emby, WebDAV or NAS libraries. Authentication secrets are not stored in plain text.",
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = { showAdd = true }) { Text("Add Server") }
        error?.let {
            Spacer(Modifier.height(12.dp))
            AstraWaveStatePanel("Connection setup", it)
        }
        Spacer(Modifier.height(18.dp))

        if (connections.isEmpty()) {
            AstraWaveStatePanel(
                "No personal media connected",
                "Add a server to prepare AstraWave for your authorized personal library. Provider login/token setup will be requested separately and stored securely.",
            )
        } else {
            connections.forEach { connection ->
                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(connection.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text("${connection.provider.name} • ${connection.serverUrl}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                if (connection.enabled) "Enabled" else "Disabled",
                                color = if (connection.enabled) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (connection.status) {
                                PersonalMediaConnectionStatus.READY -> "Ready • ${connection.libraryCount} libraries • ${connection.itemCount} items"
                                PersonalMediaConnectionStatus.ERROR -> connection.lastError ?: "Connection error"
                                PersonalMediaConnectionStatus.DEGRADED -> connection.lastError ?: "Connection degraded"
                                PersonalMediaConnectionStatus.CONNECTING -> "Connecting…"
                                PersonalMediaConnectionStatus.NOT_CONNECTED -> "Server saved • secure authentication not completed yet"
                            },
                            color = AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                if (connection.enabled) "Disable" else "Enable",
                                color = AstraWaveColors.Accent,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable {
                                    store.setEnabled(connection.id, !connection.enabled)
                                    refresh()
                                },
                            )
                            Text(
                                "Remove",
                                color = AstraWaveColors.Warning,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable {
                                    store.remove(connection.id)
                                    refresh()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddPersonalMediaDialog(
            profileId = profileId,
            onDismiss = { showAdd = false },
            onSave = { connection ->
                val errors = PersonalMediaValidation.validate(connection)
                if (errors.isNotEmpty()) {
                    error = errors.joinToString(" • ")
                } else {
                    store.save(connection)
                    refresh()
                    error = null
                    showAdd = false
                }
            },
        )
    }
}

@Composable
private fun AddPersonalMediaDialog(
    profileId: String,
    onDismiss: () -> Unit,
    onSave: (PersonalMediaConnection) -> Unit,
) {
    var provider by remember { mutableStateOf(PersonalMediaProvider.PLEX) }
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Personal Media Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Provider: ${provider.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Previous", color = AstraWaveColors.Accent, modifier = Modifier.clickable {
                        val values = PersonalMediaProvider.entries
                        provider = values[(values.indexOf(provider) - 1 + values.size) % values.size]
                    })
                    Text("Next", color = AstraWaveColors.Accent, modifier = Modifier.clickable {
                        val values = PersonalMediaProvider.entries
                        provider = values[(values.indexOf(provider) + 1) % values.size]
                    })
                }
                OutlinedTextField(name, { name = it }, label = { Text("Connection name") }, singleLine = true)
                OutlinedTextField(serverUrl, { serverUrl = it }, label = { Text("Server URL") }, singleLine = true)
                Text(
                    "Credentials/tokens are intentionally not requested in this screen until the encrypted provider-auth flow is connected.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && serverUrl.isNotBlank(),
                onClick = {
                    onSave(
                        PersonalMediaConnection(
                            id = UUID.randomUUID().toString(),
                            profileId = profileId,
                            provider = provider,
                            name = name.trim(),
                            serverUrl = serverUrl.trim().trimEnd('/'),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
