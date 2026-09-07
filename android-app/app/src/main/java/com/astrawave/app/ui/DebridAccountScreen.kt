package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.astrawave.app.data.DebridAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings for optional user-linked debrid accounts. No discovery is performed here. */
@Composable
fun DebridAccountScreen(profileId: String = "default", onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DebridAccountRepository(context) }
    var token by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var status by remember(profileId) {
        mutableStateOf(
            if (repository.hasRealDebrid(profileId)) {
                DebridAccountRepository.AccountStatus(connected = true)
            } else {
                DebridAccountRepository.AccountStatus(connected = false)
            },
        )
    }

    fun verify() {
        checking = true
        scope.launch {
            status = withContext(Dispatchers.IO) { repository.verifyRealDebrid(profileId) }
            checking = false
        }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AstraWavePageHeader(
                title = "Cloud & Debrid",
                subtitle = "Link your own supported account to optimize playback links AstraWave has already authorized. Debrid is never used for title or file discovery.",
                modifier = Modifier.weight(1f),
            )
            AstraWaveSecondaryButton(label = "Back", onClick = onBack)
        }

        AstraWaveStatePanel(
            title = if (status.connected) "Real-Debrid connected" else "Real-Debrid not connected",
            message = when {
                checking -> "Verifying your linked account…"
                status.connected -> buildString {
                    append(status.username ?: "Linked account")
                    status.type?.let { append(" • $it") }
                    status.expiration?.let { append(" • expires $it") }
                    append(". Authorized VOD links can be optimized automatically; normal sources remain as fallback.")
                }
                !status.error.isNullOrBlank() -> status.error.orEmpty()
                else -> "Enter your own access token to enable optional link optimization for this profile."
            },
            loading = checking,
        )

        if (!status.connected || !repository.hasRealDebrid(profileId)) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Real-Debrid access token") },
                supportingText = { Text("Stored encrypted with Android Keystore on this device.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            AstraWavePrimaryButton(
                label = if (checking) "Checking…" else "Connect & Verify",
                enabled = !checking && token.trim().isNotEmpty(),
                onClick = {
                    repository.saveRealDebridToken(profileId, token.trim())
                    token = ""
                    verify()
                },
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraWavePrimaryButton(label = if (checking) "Checking…" else "Verify Account", enabled = !checking, onClick = ::verify)
                AstraWaveSecondaryButton(
                    label = "Disconnect",
                    onClick = {
                        repository.disconnectRealDebrid(profileId)
                        status = DebridAccountRepository.AccountStatus(connected = false)
                        token = ""
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("How AstraWave uses it", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        AstraWaveActionRow(
            title = "Authorization first",
            subtitle = "A title must already have an eligible source from public-domain, your Xtream account, personal media, or a provider you explicitly authorized.",
        ) { Text("1", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleMedium) }
        AstraWaveActionRow(
            title = "Optional optimization",
            subtitle = "AstraWave may ask your linked account to convert an already-authorized link into a direct optimized URL. Failures keep the original URL.",
        ) { Text("2", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleMedium) }
        AstraWaveActionRow(
            title = "Player failover remains",
            subtitle = "Play Best still receives ordered backups, so debrid does not become a single point of failure.",
        ) { Text("3", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleMedium) }
    }
}
