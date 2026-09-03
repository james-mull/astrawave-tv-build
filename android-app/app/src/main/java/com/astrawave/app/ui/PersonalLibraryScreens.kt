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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AstraWaveList
import com.astrawave.app.core.FavoriteEntry
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.WatchlistEntry
import com.astrawave.app.data.LocalLibraryStore

sealed interface PersonalLibraryView {
    data object Watchlist : PersonalLibraryView
    data object Favorites : PersonalLibraryView
    data object History : PersonalLibraryView
    data object ContinueWatching : PersonalLibraryView
    data class CustomList(val listId: String) : PersonalLibraryView
}

@Composable
fun PersonalLibraryScreen(
    title: String,
    items: List<LibraryItemRef>,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
            Text("Back", color = AstraWaveColors.Accent, modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(18.dp))
        if (items.isEmpty()) {
            AstraWaveStatePanel("Nothing here yet", "Items you add will appear here and stay available offline.")
        } else {
            items.forEach { item ->
                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column {
                        Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text(item.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingScreen(
    progress: List<LocalLibraryStore.PlaybackProgress>,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Continue Watching", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
            Text("Back", color = AstraWaveColors.Accent, modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(18.dp))
        if (progress.isEmpty()) {
            AstraWaveStatePanel("Nothing in progress", "Partially watched movies, episodes, and other media will appear here.")
        } else {
            progress.forEach { entry ->
                val percent = if (entry.durationMs > 0) ((entry.positionMs * 100) / entry.durationMs).coerceIn(0, 100) else 0
                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column {
                        Text(entry.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Text("$percent% watched", color = AstraWaveColors.Accent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateListDialog(
    profileId: String,
    onDismiss: () -> Unit,
    onCreate: (AstraWaveList) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New AstraWave list") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("List name") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val now = System.currentTimeMillis()
                    onCreate(
                        AstraWaveList(
                            id = "list-$now",
                            profileId = profileId,
                            name = name.trim(),
                            description = description.trim(),
                            updatedAtEpochMs = now,
                        ),
                    )
                },
            ) { Text("Create") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun watchlistItems(entries: List<WatchlistEntry>): List<LibraryItemRef> = entries.map { it.item }
fun favoriteItems(entries: List<FavoriteEntry>): List<LibraryItemRef> = entries.map { it.item }
fun historyItems(entries: List<LocalLibraryStore.HistoryEntry>): List<LibraryItemRef> = entries.map { it.item }
