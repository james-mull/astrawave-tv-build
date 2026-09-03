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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.PersonalMediaConnection
import com.astrawave.app.core.PersonalMediaConnectionStatus
import com.astrawave.app.core.PersonalMediaGateway
import com.astrawave.app.core.PersonalMediaProvider
import com.astrawave.app.core.PersonalMediaValidation
import com.astrawave.app.data.EmbyFamilyPersonalMediaGateway
import com.astrawave.app.data.PersonalMediaCredentialStore
import com.astrawave.app.data.PersonalMediaStore
import com.astrawave.app.data.PlexPersonalMediaGateway
import com.astrawave.app.data.WebDavPersonalMediaGateway
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PersonalMediaScreen(profileId: String = "default") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PersonalMediaStore(context) }
    val credentials = remember { PersonalMediaCredentialStore(context) }
    val embyFamilyGateway = remember { EmbyFamilyPersonalMediaGateway(context) }
    val plexGateway = remember { PlexPersonalMediaGateway(context) }
    val webDavGateway = remember { WebDavPersonalMediaGateway(context) }
    var connections by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var showAdd by remember { mutableStateOf(false) }
    var tokenAuthConnection by remember { mutableStateOf<PersonalMediaConnection?>(null) }
    var basicAuthConnection by remember { mutableStateOf<PersonalMediaConnection?>(null) }
    var testingConnectionId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() { connections = store.load(profileId) }

    fun gatewayFor(connection: PersonalMediaConnection): PersonalMediaGateway = when (connection.provider) {
        PersonalMediaProvider.PLEX -> plexGateway
        PersonalMediaProvider.JELLYFIN, PersonalMediaProvider.EMBY -> embyFamilyGateway
        PersonalMediaProvider.WEBDAV, PersonalMediaProvider.NAS -> webDavGateway
    }

    fun testConnection(connection: PersonalMediaConnection) {
        testingConnectionId = connection.id
        scope.launch {
            val tested = withContext(Dispatchers.IO) { gatewayFor(connection).test(connection) }
            store.save(tested)
            testingConnectionId = null
            error = tested.lastError
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Personal Media",
            subtitle = "Connect your own Plex, Jellyfin, Emby, WebDAV or WebDAV-compatible NAS libraries. Authentication secrets are encrypted with Android Keystore.",
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = { showAdd = true }) { Text("Add Server") }
        error?.let {
            Spacer(Modifier.height(12.dp))
            AstraWaveStatePanel("Personal media", it)
        }
        Spacer(Modifier.height(18.dp))

        if (connections.isEmpty()) {
            AstraWaveStatePanel(
                "No personal media connected",
                "Add a server to prepare AstraWave for your authorized personal library.",
            )
        } else {
            connections.forEach { connection ->
                val supportsTokenAuth = connection.provider == PersonalMediaProvider.PLEX ||
                    connection.provider == PersonalMediaProvider.JELLYFIN ||
                    connection.provider == PersonalMediaProvider.EMBY
                val supportsBasicAuth = connection.provider == PersonalMediaProvider.WEBDAV ||
                    connection.provider == PersonalMediaProvider.NAS
                val hasCredential = credentials.hasCredential(connection.id)
                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(connection.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text("${connection.provider.name} • ${connection.serverUrl}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                when {
                                    testingConnectionId == connection.id -> "Testing…"
                                    connection.status == PersonalMediaConnectionStatus.READY -> "Ready"
                                    hasCredential -> "Credential saved"
                                    supportsBasicAuth -> "Login optional"
                                    else -> "Setup needed"
                                },
                                color = when {
                                    connection.status == PersonalMediaConnectionStatus.READY -> AstraWaveColors.Success
                                    hasCredential -> AstraWaveColors.Accent
                                    supportsBasicAuth -> AstraWaveColors.SecondaryText
                                    else -> AstraWaveColors.TertiaryText
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (connection.status) {
                                PersonalMediaConnectionStatus.READY -> "Connected • ${connection.libraryCount} libraries • ${connection.itemCount} items • last checked ${connection.lastSyncEpochMs}"
                                PersonalMediaConnectionStatus.ERROR -> connection.lastError ?: "Connection error"
                                PersonalMediaConnectionStatus.DEGRADED -> connection.lastError ?: "Connection degraded"
                                PersonalMediaConnectionStatus.CONNECTING -> "Connecting…"
                                PersonalMediaConnectionStatus.NOT_CONNECTED -> when {
                                    hasCredential -> "Secure credential saved • test the server to finish setup"
                                    supportsBasicAuth -> "Server saved • test anonymously or add a secure username/password"
                                    else -> "Server saved • secure authentication required"
                                }
                            },
                            color = AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (supportsTokenAuth) {
                                Text(
                                    if (hasCredential) "Update Token" else "Add Token",
                                    color = AstraWaveColors.Accent,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.clickable { tokenAuthConnection = connection },
                                )
                            }
                            if (supportsBasicAuth) {
                                Text(
                                    if (hasCredential) "Update Login" else "Add Login",
                                    color = AstraWaveColors.Accent,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.clickable { basicAuthConnection = connection },
                                )
                            }
                            Text(
                                if (testingConnectionId == connection.id) "Testing…" else "Test Connection",
                                color = if (testingConnectionId == connection.id) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable(
                                    enabled = testingConnectionId != connection.id && (supportsBasicAuth || hasCredential),
                                ) { testConnection(connection) },
                            )
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
                                    credentials.clear(connection.id)
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

    tokenAuthConnection?.let { connection ->
        PersonalMediaTokenDialog(
            providerName = connection.provider.name,
            onDismiss = { tokenAuthConnection = null },
            onSave = { token ->
                credentials.saveToken(connection.id, token)
                tokenAuthConnection = null
                error = null
                testConnection(connection)
            },
        )
    }

    basicAuthConnection?.let { connection ->
        PersonalMediaBasicAuthDialog(
            providerName = connection.provider.name,
            onDismiss = { basicAuthConnection = null },
            onSave = { username, password ->
                credentials.clear(connection.id)
                credentials.saveUsername(connection.id, username)
                credentials.savePassword(connection.id, password)
                basicAuthConnection = null
                error = null
                testConnection(connection)
            },
        )
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
private fun PersonalMediaTokenDialog(
    providerName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect $providerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the access token for your own server. AstraWave encrypts it locally with Android Keystore.")
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Access token") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = token.isNotBlank(), onClick = { onSave(token.trim()) }) { Text("Save & Test") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PersonalMediaBasicAuthDialog(
    providerName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect $providerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter credentials for your own WebDAV endpoint. AstraWave encrypts both values locally with Android Keystore.")
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = username.isNotBlank() && password.isNotBlank(),
                onClick = { onSave(username.trim(), password) },
            ) { Text("Save & Test") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
                    when (provider) {
                        PersonalMediaProvider.PLEX, PersonalMediaProvider.JELLYFIN, PersonalMediaProvider.EMBY ->
                            "After saving, add your server access token and AstraWave will test the connection."
                        PersonalMediaProvider.WEBDAV ->
                            "After saving, test the WebDAV endpoint anonymously or add an encrypted username/password."
                        PersonalMediaProvider.NAS ->
                            "NAS support requires an HTTP(S) WebDAV endpoint. Raw SMB browsing is not enabled in this adapter."
                    },
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
