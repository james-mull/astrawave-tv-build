package com.astrawave.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceStatus
import com.astrawave.app.core.IptvSourceType
import com.astrawave.app.core.IptvSourceValidation
import com.astrawave.app.data.IptvSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

fun newIptvSource(profileId: String, type: IptvSourceType): IptvSource = IptvSource(
    id = UUID.randomUUID().toString(),
    profileId = profileId,
    name = if (type == IptvSourceType.M3U) "My M3U" else "My Xtream",
    type = type,
)

@Composable
fun IptvSourceEditorDialog(
    source: IptvSource,
    repository: IptvSourceRepository = remember { IptvSourceRepository() },
    onDismiss: () -> Unit,
    onSave: (IptvSource) -> Unit,
    onDelete: ((IptvSource) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(source) { mutableStateOf(source) }
    var message by remember(source) { mutableStateOf<String?>(source.lastError) }
    var testing by remember(source) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source.id.startsWith("legacy-") || source.lastCheckedEpochMs > 0L) "Edit IPTV source" else "Add IPTV source") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Source name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))

                if (draft.type == IptvSourceType.M3U) {
                    OutlinedTextField(
                        value = draft.m3uUrl.orEmpty(),
                        onValueChange = { draft = draft.copy(m3uUrl = it) },
                        label = { Text("M3U URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        value = draft.xtreamServer.orEmpty(),
                        onValueChange = { draft = draft.copy(xtreamServer = it) },
                        label = { Text("Xtream server") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.xtreamUsername.orEmpty(),
                        onValueChange = { draft = draft.copy(xtreamUsername = it) },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.xtreamPassword.orEmpty(),
                        onValueChange = { draft = draft.copy(xtreamPassword = it) },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.xmlTvUrl.orEmpty(),
                    onValueChange = { draft = draft.copy(xmlTvUrl = it) },
                    label = { Text("XMLTV URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.priority.toString(),
                    onValueChange = { value -> value.toIntOrNull()?.let { draft = draft.copy(priority = it.coerceIn(1, 100)) } },
                    label = { Text("Priority (1–100)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                }

                message?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = if (draft.status == IptvSourceStatus.READY) AstraWaveColors.Success else AstraWaveColors.SecondaryText)
                }

                Spacer(Modifier.height(10.dp))
                Row {
                    OutlinedButton(
                        onClick = {
                            val errors = IptvSourceValidation.validate(draft)
                            if (errors.isNotEmpty()) {
                                message = errors.joinToString(" • ")
                                return@OutlinedButton
                            }
                            testing = true
                            message = "Testing connection…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { repository.test(draft) }
                                draft = draft.copy(
                                    status = result.status,
                                    channelCount = result.channelCount,
                                    guideProgramCount = result.guideProgramCount,
                                    lastCheckedEpochMs = System.currentTimeMillis(),
                                    lastError = result.error,
                                )
                                testing = false
                                message = if (result.status == IptvSourceStatus.READY) {
                                    "Connected • ${result.channelCount} channels • ${result.guideProgramCount} guide items"
                                } else {
                                    result.error ?: "Connection returned ${result.status.name.lowercase()} status"
                                }
                            }
                        },
                        enabled = !testing,
                    ) { Text(if (testing) "Testing…" else "Test Connection") }

                    if (onDelete != null) {
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        OutlinedButton(onClick = { onDelete(draft) }) { Text("Delete") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val errors = IptvSourceValidation.validate(draft)
                    if (errors.isEmpty()) onSave(draft) else message = errors.joinToString(" • ")
                },
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
