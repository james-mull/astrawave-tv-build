package com.astrawave.app.ui

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.KidsContentRatingRepository
import com.astrawave.app.data.KidsModePolicyStore
import com.astrawave.app.data.ParentPinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class KidsRatedItem(
    val item: AstraWaveMetadataGateway.Item,
    val rating: String?,
    val approved: Boolean,
    val allowed: Boolean,
)

private sealed interface KidsRowState {
    data object Loading : KidsRowState
    data class Ready(val items: List<KidsRatedItem>) : KidsRowState
    data class Error(val message: String) : KidsRowState
}

@Composable
fun KidsDiscoveryScreen(
    profileId: String,
    media: DynamicCollectionRepository.Media,
) {
    val context = LocalContext.current
    val policyStore = remember { KidsModePolicyStore(context) }
    val parentPin = remember { ParentPinStore(context) }
    val ratingRepository = remember { KidsContentRatingRepository() }
    val repository = remember { DynamicCollectionRepository() }
    var approvalVersion by remember(profileId) { mutableStateOf(0) }
    val policy = remember(profileId, approvalVersion) { policyStore.load(profileId) }
    val genres = remember(profileId, approvalVersion) { policyStore.allowedGenres(profileId) }
    val states = remember(profileId, media) { mutableStateMapOf<String, KidsRowState>() }
    var approvalTarget by remember { mutableStateOf<KidsRatedItem?>(null) }

    if (policyStore.bedtimeActive(profileId)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader(title = "Kids Mode", subtitle = "Bedtime is active for this profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel(
                title = "Time for a break",
                message = "Kids viewing is paused during the parent-set bedtime window. A parent can change this schedule from Parental Controls.",
            )
        }
        return
    }

    LaunchedEffect(profileId, media, genres, approvalVersion) {
        genres.forEach { genre ->
            states[genre] = KidsRowState.Loading
            states[genre] = try {
                val items = withContext(Dispatchers.IO) {
                    repository.genre(media, genre, pages = 2)
                        .distinctBy { it.id }
                        .take(30)
                        .map { item ->
                            val approved = policyStore.isApproved(profileId, item.id)
                            val rating = if (approved) null else ratingRepository.rating(media, item.id).rating
                            KidsRatedItem(
                                item = item,
                                rating = rating,
                                approved = approved,
                                allowed = policyStore.ratingAllowed(profileId, rating, item.id),
                            )
                        }
                        .filter { checked -> policy.approvedOnly || checked.allowed }
                        .take(24)
                        .onEach { ArtworkRegistry.register(it.item.name, it.item.posterUrl ?: it.item.backdropUrl) }
                }
                KidsRowState.Ready(items)
            } catch (error: Exception) {
                KidsRowState.Error(error.message ?: "Unable to load kids collection")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(
            title = if (media == DynamicCollectionRepository.Media.MOVIE) "Kids Movies" else "Kids TV",
            subtitle = "${policy.ageLevel.label} • ${if (policy.approvedOnly) "Parent-approved titles only" else "Certification-filtered family discovery"}",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (policy.approvedOnly) {
                "Unapproved titles stay locked until a parent enters the household Parent PIN."
            } else {
                "US movie/TV certifications are checked when available. Titles above this profile's age tier are removed; parent approvals override the rating rule."
            },
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(22.dp))

        genres.forEach { genre ->
            val state = states[genre] ?: KidsRowState.Loading
            Text(genre, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(9.dp))
            when (state) {
                KidsRowState.Loading -> AstraWaveStatePanel("Checking $genre…", "Loading titles and validating kids content ratings.", loading = true)
                is KidsRowState.Error -> AstraWaveStatePanel("$genre unavailable", state.message)
                is KidsRowState.Ready -> KidsMediaRow(
                    items = state.items,
                    mediaType = if (media == DynamicCollectionRepository.Media.MOVIE) LibraryMediaType.MOVIE else LibraryMediaType.SERIES,
                    approvedOnly = policy.approvedOnly,
                    onLocked = { approvalTarget = it },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    approvalTarget?.let { target ->
        ParentApprovalDialog(
            title = target.item.name,
            rating = target.rating,
            hasParentPin = parentPin.hasPin(),
            onDismiss = { approvalTarget = null },
            verify = parentPin::verify,
            onApproved = {
                policyStore.approveTitle(profileId, target.item.id, true)
                approvalTarget = null
                approvalVersion++
            },
        )
    }
}

@Composable
private fun KidsMediaRow(
    items: List<KidsRatedItem>,
    mediaType: LibraryMediaType,
    approvedOnly: Boolean,
    onLocked: (KidsRatedItem) -> Unit,
) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        AstraWaveStatePanel("Nothing to show", "No titles passed this profile's current rating and approval rules.")
        return
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { checked ->
            val item = checked.item
            val locked = approvedOnly && !checked.approved
            AstraWaveFocusableCard(
                Modifier.width(220.dp).clickable {
                    if (locked) {
                        onLocked(checked)
                    } else {
                        val sourceId = if (item.id.startsWith("tt", true)) {
                            "stremio:cinemeta:${if (mediaType == LibraryMediaType.MOVIE) "movie" else "series"}:${item.id}"
                        } else "tmdb:${item.id}"
                        context.startActivity(
                            Intent(context, TitleDetailsActivity::class.java)
                                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType.name)
                                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId),
                        )
                    }
                },
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(130.dp)) {
                        AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxSize())
                        Text(
                            when {
                                checked.approved -> "APPROVED"
                                locked -> "PARENT LOCK"
                                !checked.rating.isNullOrBlank() -> checked.rating
                                else -> "KIDS"
                            },
                            color = AstraWaveColors.PrimaryText,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                                .background(AstraWaveColors.Background.copy(alpha = 0.84f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (locked) "Parent approval required" else checked.rating ?: "Parent-approved family discovery",
                        color = if (locked) AstraWaveColors.Warning else AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentApprovalDialog(
    title: String,
    rating: String?,
    hasParentPin: Boolean,
    onDismiss: () -> Unit,
    verify: (String) -> Boolean,
    onApproved: () -> Unit,
) {
    var pin by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent approval") },
        text = {
            Column {
                Text("Approve $title${rating?.let { " ($it)" } ?: ""} for this Kids profile?")
                Spacer(Modifier.height(12.dp))
                if (hasParentPin) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false },
                        label = { Text("Parent PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    if (error) {
                        Spacer(Modifier.height(6.dp))
                        Text("Incorrect Parent PIN", color = AstraWaveColors.Error)
                    }
                } else {
                    Text("Set a Parent PIN in Parental Controls before approving individual titles.", color = AstraWaveColors.Warning)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasParentPin,
                onClick = { if (verify(pin)) onApproved() else error = true },
            ) { Text("Approve") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
