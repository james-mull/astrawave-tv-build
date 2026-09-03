package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.OnboardingFlow
import com.astrawave.app.core.OnboardingState
import com.astrawave.app.core.OnboardingStep
import org.json.JSONArray
import org.json.JSONObject

/** Persistent, resumable first-run setup progress. */
class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_onboarding_v1", Context.MODE_PRIVATE)

    fun load(profileId: String = "default"): OnboardingState {
        val raw = prefs.getString(key(profileId), null) ?: return OnboardingState()
        return runCatching {
            val obj = JSONObject(raw)
            OnboardingState(
                completedSteps = decodeSteps(obj.optJSONArray("completed")),
                skippedSteps = decodeSteps(obj.optJSONArray("skipped")),
                currentStep = runCatching { OnboardingStep.valueOf(obj.optString("currentStep")) }
                    .getOrDefault(OnboardingStep.WELCOME),
            )
        }.getOrDefault(OnboardingState())
    }

    fun markComplete(profileId: String = "default", step: OnboardingStep): OnboardingState {
        val current = load(profileId)
        val updated = current.copy(
            completedSteps = current.completedSteps + step,
            skippedSteps = current.skippedSteps - step,
        ).let { it.copy(currentStep = OnboardingFlow.next(it)) }
        save(profileId, updated)
        return updated
    }

    fun skip(profileId: String = "default", step: OnboardingStep): OnboardingState {
        val current = load(profileId)
        val updated = current.copy(
            skippedSteps = current.skippedSteps + step,
            completedSteps = current.completedSteps - step,
        ).let { it.copy(currentStep = OnboardingFlow.next(it)) }
        save(profileId, updated)
        return updated
    }

    fun goTo(profileId: String = "default", step: OnboardingStep): OnboardingState {
        val updated = load(profileId).copy(currentStep = step)
        save(profileId, updated)
        return updated
    }

    fun reset(profileId: String = "default") {
        prefs.edit().remove(key(profileId)).apply()
    }

    private fun save(profileId: String, state: OnboardingState) {
        val obj = JSONObject()
            .put("completed", JSONArray(state.completedSteps.map { it.name }))
            .put("skipped", JSONArray(state.skippedSteps.map { it.name }))
            .put("currentStep", state.currentStep.name)
        prefs.edit().putString(key(profileId), obj.toString()).apply()
    }

    private fun decodeSteps(array: JSONArray?): Set<OnboardingStep> {
        if (array == null) return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                runCatching { OnboardingStep.valueOf(array.optString(index)) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun key(profileId: String) = "state_$profileId"
}
