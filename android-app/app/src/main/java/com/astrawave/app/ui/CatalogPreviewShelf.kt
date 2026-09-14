package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.astrawave.app.PremiumVodDetailActivity
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.BuiltInCatalogDefinition
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.BuiltInCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CatalogPreviewShelf(
    section: String,
    definitions: List<BuiltInCatalogDefinition>,
    profileId: String,
) {
    val context = LocalContext.current
    val repository = remember { BuiltInCatalogRepository(context) }
    val lead = remember(definitions) {
        definitions.firstOrNull { it.featured } ?: definitions.firstOrNull()
    } ?: return

    var items by remember(lead.id, profileId) { mutableStateOf<List<AstraWaveMetadataGateway.Item>>(emptyList()) }
    var source by remember(lead.id, profileId) { mutableStateOf("") }

    LaunchedEffect(lead.id, profileId) {
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.page(lead.id, profileId, offset = 0, pageSize = 14) }.getOrNull()
        }
        items = result?.items.orEmpty()
        source = result?.sourceLabel.orEmpty()
    }

    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Featured in $section",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 18.dp),
        ) {
            items(items, key = { item -> "${lead.id}:${item.id}:${item.name}" }) { item ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable {
                            val mediaType = if (
                                lead.mediaType == BuiltInCatalogMediaType.SHOW ||
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
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(132.dp)
                            .height(198.dp)
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
        if (source.isNotBlank()) {
            Text(source, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}
