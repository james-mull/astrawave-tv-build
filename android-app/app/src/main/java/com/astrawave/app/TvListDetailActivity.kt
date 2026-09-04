package com.astrawave.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.ui.AstraWaveArtwork
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveFocusableCard
import com.astrawave.app.ui.AstraWavePageHeader
import com.astrawave.app.ui.AstraWaveSecondaryButton
import com.astrawave.app.ui.AstraWaveStatePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvListDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "AstraWave TV List" }
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val query = intent.getStringExtra(EXTRA_QUERY)
        val genre = intent.getStringExtra(EXTRA_GENRE)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty().ifBlank { "default" }
        setContent {
            MaterialTheme {
                Surface(color = AstraWaveColors.Background) {
                    TvListDetailScreen(title, reason, query, genre, profileId) { finish() }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "list_title"
        const val EXTRA_REASON = "list_reason"
        const val EXTRA_QUERY = "list_query"
        const val EXTRA_GENRE = "list_genre"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}

@Composable
private fun TvListDetailScreen(
    title: String,
    reason: String,
    query: String?,
    genre: String?,
    profileId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = remember { DynamicCollectionRepository() }
    val metadata = remember { AstraWaveMetadataGateway() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<AstraWaveMetadataGateway.Item>>(emptyList()) }

    LaunchedEffect(title, query, genre) {
        loading = true
        error = null
        items = runCatching {
            withContext(Dispatchers.IO) {
                when {
                    !genre.isNullOrBlank() -> dynamic.genre(DynamicCollectionRepository.Media.SERIES, genre, pages = 3)
                    !query.isNullOrBlank() -> metadata.search(query)
                    else -> metadata.search(title)
                }.filter { it.type.equals("series", true) || it.type.equals("tv", true) || it.type.isBlank() }
                    .distinctBy { it.id }
                    .take(60)
                    .onEach { ArtworkRegistry.register(it.name, it.posterUrl ?: it.backdropUrl) }
            }
        }.onFailure { error = it.message ?: "Unable to load this list" }.getOrDefault(emptyList())
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(title = title, subtitle = reason.ifBlank { "AstraWave TV collection" })
        Spacer(Modifier.height(10.dp))
        AstraWaveSecondaryButton(label = "← Back to show details", onClick = onBack)
        Spacer(Modifier.height(18.dp))
        when {
            loading -> AstraWaveStatePanel("Loading $title…", "Finding matching shows.", loading = true)
            error != null -> AstraWaveStatePanel("List unavailable", error.orEmpty())
            items.isEmpty() -> AstraWaveStatePanel("No shows found", "This collection does not have matching series right now.")
            else -> {
                Text("${items.size} shows", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { item ->
                        AstraWaveFocusableCard(
                            Modifier.width(170.dp).clickable {
                                context.startActivity(
                                    Intent(context, TitleDetailsActivity::class.java)
                                        .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                                        .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, "SERIES")
                                        .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, if (item.id.startsWith("tt", true)) "stremio:cinemeta:series:${item.id}" else "tmdb:${item.id}")
                                        .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
                                )
                            },
                        ) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(240.dp)) { AstraWaveArtwork(item.name, Modifier.fillMaxSize()) }
                                Spacer(Modifier.height(8.dp))
                                Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                item.releaseInfo?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}
