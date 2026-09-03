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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceStatus
import com.astrawave.app.core.IptvSourceType
import com.astrawave.app.data.IptvSourceStore

@Composable
fun MyIptvScreen(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { IptvSourceStore(context) }
    val profileId = sources.firstOrNull()?.profileId ?: "default"
    var managedSources by remember(sources) { mutableStateOf(sources) }
    var editingSource by remember { mutableStateOf<IptvSource?>(null) }

    fun refresh(profile: String) {
        val refreshed = store.load(profile)
        managedSources = refreshed
        onSourcesChanged(refreshed)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        Text("Live TV", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "AstraWave Free TV, your IPTV services, and Combined mode in one place.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(24.dp))
        Text("MY IPTV", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddSourceButton("Add M3U") { editingSource = newIptvSource(profileId, IptvSourceType.M3U) }
            AddSourceButton("Add Xtream") { editingSource = newIptvSource(profileId, IptvSourceType.XTREAM) }
        }

        Spacer(Modifier.height(18.dp))
        if (managedSources.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(AstraWaveColors.Surface, RoundedCornerShape(18.dp)).padding(18.dp),
            ) {
                Text("No IPTV sources yet", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add an M3U playlist or Xtream account. You can connect multiple providers and merge them later in Combined mode.",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            managedSources.sortedBy { it.priority }.forEach { source ->
                SourceRow(source) { editingSource = it }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("ASTRAWAVE FREE TV", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().background(AstraWaveColors.Surface, RoundedCornerShape(18.dp)).padding(18.dp),
        ) {
            Text("Built-in free channels", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(
                "Published channels are generated from the daily rights/health-checked AstraWave Free TV pipeline.",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    editingSource?.let { source ->
        val isExisting = managedSources.any { it.id == source.id }
        IptvSourceEditorDialog(
            source = source,
            onDismiss = { editingSource = null },
            onSave = { saved ->
                store.upsert(saved)
                refresh(saved.profileId)
                editingSource = null
            },
            onDelete = if (isExisting) {
                { deleting ->
                    store.delete(deleting.profileId, deleting.id)
                    refresh(deleting.profileId)
                    editingSource = null
                }
            } else null,
        )
    }
}

@Composable
private fun AddSourceButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.background(AstraWaveColors.SurfaceRaised, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = AstraWaveColors.Accent)
        Spacer(Modifier.width(6.dp))
        Text(label, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SourceRow(source: IptvSource, onOpenSource: (IptvSource) -> Unit) {
    val (icon, tint) = sourceStatusIcon(source.status)
    AstraWaveFocusableCard(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { onOpenSource(source) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${source.type.displayName()} • ${source.channelCount} channels • ${source.guideProgramCount} guide items",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                source.lastError?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = AstraWaveColors.Error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun sourceStatusIcon(status: IptvSourceStatus): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (status) {
    IptvSourceStatus.READY -> Icons.Default.CheckCircle to AstraWaveColors.Success
    IptvSourceStatus.ERROR -> Icons.Default.ErrorOutline to AstraWaveColors.Error
    IptvSourceStatus.DEGRADED -> Icons.Default.ErrorOutline to AstraWaveColors.Warning
    else -> Icons.Default.RadioButtonUnchecked to AstraWaveColors.SecondaryText
}

private fun IptvSourceType.displayName(): String = when (this) {
    IptvSourceType.M3U -> "M3U"
    IptvSourceType.XTREAM -> "Xtream"
}
