package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.astrawave.app.PremiumVodDetailActivity
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.BuiltInCatalogDefinition
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.BuiltInCatalogRepository
import com.astrawave.app.data.CatalogServiceOverrides
import com.astrawave.app.data.VerifiedMdbListCatalogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CatalogPreviewShelf(
    section: String,
    definitions: List<BuiltInCatalogDefinition>,
    profileId: String,
) {
    val lead = remember(section, definitions) {
        when (section) {
            "Streaming Services" -> definitions.firstOrNull { it.id in CatalogServiceOverrides }
            else -> definitions.firstOrNull { it.id in VerifiedMdbListCatalogs }
        } ?: definitions.firstOrNull { it.featured }
            ?: definitions.firstOrNull()
    } ?: return
    CatalogContentShelf(lead, profileId)
}

@Composable
fun CatalogContentShelf(
    definition: BuiltInCatalogDefinition,
    profileId: String,
) {
    val context = LocalContext.current
    val repository = remember { BuiltInCatalogRepository(context) }
    var items by remember(definition.id, profileId) { mutableStateOf<List<AstraWaveMetadataGateway.Item>>(emptyList()) }
    var source by remember(definition.id, profileId) { mutableStateOf("") }

    LaunchedEffect(definition.id, profileId) {
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.page(definition.id, profileId, offset = 0, pageSize = 18) }.getOrNull()
        }
        items = result?.items.orEmpty()
        source = result?.sourceLabel.orEmpty()
    }

    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    definition.title,
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (source.isNotBlank()) {
                    Text(source, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("See all  ›", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 28.dp),
        ) {
            items(items, key = { item -> "${definition.id}:${item.id}:${item.name}" }) { item ->
                Column(
                    modifier = Modifier
                        .width(142.dp)
                        .clickable {
                            val mediaType = if (
                                definition.mediaType == BuiltInCatalogMediaType.SHOW ||
                                item.type.equals("series", true) || item.type.equals("tv", true)
                            ) "SERIES" else "MOVIE"
                            val sourceId = when {
                                item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (mediaType == "SERIES") "series" else "movie"}:${item.id}"
                                item.id.toLongOrNull() != null -> "tmdb:${item.id}"
                                else -> null
                            }
                            context.startActivity(
                                Intent(context, PremiumVodDetailActivity::class.java)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_TITLE, item.name)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_MEDIA_TYPE, mediaType)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_SOURCE_ID, sourceId)
                                    .putExtra(PremiumVodDetailActivity.EXTRA_PROFILE_ID, profileId),
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(142.dp)
                            .height(213.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = item.posterUrl ?: item.backdropUrl,
                            contentDescription = item.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (item.posterUrl.isNullOrBlank() && item.backdropUrl.isNullOrBlank()) {
                            Text(item.name.take(1), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Text(
                        item.name,
                        color = AstraWaveColors.PrimaryText,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
