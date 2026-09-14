package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.BuildConfig

@Composable
fun HelpLegalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val customerBase = remember {
        BuildConfig.ASTRAWAVE_API_BASE_URL.trim().trimEnd('/')
            .removeSuffix("/api/astrawave")
            .trimEnd('/')
    }
    val linksReady = customerBase.startsWith("https://") || customerBase.startsWith("http://")

    fun open(path: String) {
        if (!linksReady) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$customerBase/$path")))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AstraWavePageHeader(
                    title = "Help & Legal",
                    subtitle = "Get help, review AstraWave policies, and check this app version.",
                )
            }
            Text("Back", color = AstraWaveColors.Accent, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
        }

        if (!linksReady) {
            AstraWaveStatePanel(
                "Customer links unavailable in this build",
                "Support and policy links appear automatically when the production AstraWave service is configured.",
            )
        }

        HelpLegalRow("Support", "Help with your account, playback, connected services, or subscription.", linksReady) { open("support") }
        HelpLegalRow("Privacy Policy", "How AstraWave handles account, profile, playback, and device data.", linksReady) { open("privacy") }
        HelpLegalRow("Terms of Service", "Rules and terms for using AstraWave and Premium.", linksReady) { open("terms") }

        Spacer(Modifier.height(8.dp))
        AstraWaveStatePanel(
            "App version",
            "AstraWave ${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}",
        )
    }
}

@Composable
private fun HelpLegalRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    AstraWaveFocusableCard(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Column {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (enabled) "Open ›" else "Available in production",
                color = if (enabled) AstraWaveColors.Accent else AstraWaveColors.TertiaryText,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
