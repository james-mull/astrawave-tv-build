package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.AppSettingsStore
import com.astrawave.app.data.TmdbCatalogRepository
import com.astrawave.app.data.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Ready(val items: List<TmdbItem>) : SearchState
    data class Error(val message: String) : SearchState
}

@Composable
fun UniversalSearchScreen() {
    val context = LocalContext.current
    val token = remember { AppSettingsStore(context).effectiveTmdbBearerToken() }
    val repository = remember(token) { TmdbCatalogRepository(token) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("Search", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Search AstraWave discovery now; IPTV, addons and personal media plug into this same surface as they are connected.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(18.dp))

        if (!repository.isConfigured()) {
            SearchMessage(
                "TMDB setup needed",
                "Connect a TMDB bearer token in My AstraWave to enable movie and TV search. Other connected AstraWave sources remain independent.",
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Movies and shows") },
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    state = SearchState.Idle
                } else {
                    state = SearchState.Loading
                    scope.launch {
                        state = try {
                            val items = withContext(Dispatchers.IO) { repository.search(trimmed) }
                            SearchState.Ready(items)
                        } catch (error: Exception) {
                            SearchState.Error(error.message ?: "Search failed")
                        }
                    }
                }
            },
            enabled = query.isNotBlank(),
        ) {
            Text("Search")
        }
        Spacer(Modifier.height(20.dp))

        when (val current = state) {
            SearchState.Idle -> SearchMessage("Start searching", "Enter a movie or TV title to search AstraWave discovery.")
            SearchState.Loading -> Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
                CircularProgressIndicator(color = AstraWaveColors.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.padding(6.dp))
                Text("Searching…", color = AstraWaveColors.SecondaryText)
            }
            is SearchState.Error -> SearchMessage("Search unavailable", current.message)
            is SearchState.Ready -> {
                if (current.items.isEmpty()) {
                    SearchMessage("No matches", "No movie or TV results matched this search.")
                } else {
                    current.items.take(50).forEach { item ->
                        AstraWaveFocusableCard(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                        ) {
                            Column {
                                Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.overview.ifBlank { "Open for details and Watch options." },
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                )
                                Spacer(Modifier.height(10.dp))
                                LibraryActionRow(item = item.toLibraryItemRef())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .padding(18.dp),
    ) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
    }
}
