package com.astrawave.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.astrawave.app.core.OnboardingStep
import com.astrawave.app.ui.AstraWaveColors
import com.astrawave.app.ui.AstraWaveOnboardingScreen
import com.astrawave.app.ui.AstraWaveTheme

/** First-run shell. Returning users bypass this activity after the initial handoff. */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstraWaveTheme {
                Surface(color = AstraWaveColors.Background) {
                    AstraWaveOnboardingScreen(
                        onOpenStep = ::openSetupStep,
                        onFinished = ::finishOnboarding,
                    )
                }
            }
        }
    }

    private fun openSetupStep(step: OnboardingStep) {
        markOnboardingSeen()
        startActivity(
            Intent(this, RebuildMainActivity::class.java)
                .putExtra(EXTRA_SETUP_STEP, step.name),
        )
        finish()
    }

    private fun finishOnboarding() {
        markOnboardingSeen()
        startActivity(Intent(this, RebuildMainActivity::class.java))
        finish()
    }

    private fun markOnboardingSeen() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_SEEN, true)
            .apply()
    }

    companion object {
        const val PREFS = "astrawave_first_run"
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        const val EXTRA_SETUP_STEP = "setup_step"
    }
}
