package com.astrawave.app.core

enum class OnboardingStep {
    WELCOME,
    PROFILE,
    TMDB,
    LIVE_TV,
    ADDONS,
    PERSONAL_MEDIA,
    AUDIO,
    DEVICE_PAIRING,
    PRIVACY,
    COMPLETE,
}

data class OnboardingState(
    val completedSteps: Set<OnboardingStep> = emptySet(),
    val skippedSteps: Set<OnboardingStep> = emptySet(),
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
) {
    val complete: Boolean get() = currentStep == OnboardingStep.COMPLETE
    fun isDone(step: OnboardingStep): Boolean = step in completedSteps || step in skippedSteps
}

data class SetupReadiness(
    val profileReady: Boolean,
    val discoveryReady: Boolean,
    val liveTvReady: Boolean,
    val addonsReady: Boolean,
    val personalMediaReady: Boolean,
    val audioReady: Boolean,
    val devicePairingReady: Boolean,
)

object OnboardingFlow {
    val orderedSteps = listOf(
        OnboardingStep.WELCOME,
        OnboardingStep.PROFILE,
        OnboardingStep.TMDB,
        OnboardingStep.LIVE_TV,
        OnboardingStep.ADDONS,
        OnboardingStep.PERSONAL_MEDIA,
        OnboardingStep.AUDIO,
        OnboardingStep.DEVICE_PAIRING,
        OnboardingStep.PRIVACY,
        OnboardingStep.COMPLETE,
    )

    fun next(state: OnboardingState): OnboardingStep {
        val currentIndex = orderedSteps.indexOf(state.currentStep).coerceAtLeast(0)
        return orderedSteps.drop(currentIndex + 1).firstOrNull { !state.isDone(it) } ?: OnboardingStep.COMPLETE
    }
}
