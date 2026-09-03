package com.astrawave.app.core

enum class DiagnosticSeverity { INFO, WARNING, ERROR }

data class DiagnosticItem(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val severity: DiagnosticSeverity,
    val observedAtEpochMs: Long,
)

data class SourceHealthSummary(
    val sourceId: String,
    val sourceName: String,
    val sourceType: String,
    val enabled: Boolean,
    val itemCount: Int,
    val healthyCount: Int,
    val failedCount: Int,
    val lastCheckedEpochMs: Long? = null,
)

data class DiagnosticsSnapshot(
    val appVersion: String,
    val buildType: String,
    val firebaseConfigured: Boolean,
    val tmdbConfigured: Boolean,
    val sourceHealth: List<SourceHealthSummary>,
    val diagnostics: List<DiagnosticItem>,
)

data class FeatureFlag(
    val key: String,
    val enabled: Boolean,
    val minimumVersion: String? = null,
    val note: String? = null,
)

interface FeatureFlagProvider {
    fun flags(): List<FeatureFlag>

    fun enabled(key: String): Boolean = flags().firstOrNull { it.key == key }?.enabled == true

    /**
     * Version-aware flag evaluation used by staged rollouts. A flag with a minimumVersion
     * never becomes active on an older client, even if Remote Config says enabled.
     */
    fun enabled(key: String, appVersion: String): Boolean {
        val flag = flags().firstOrNull { it.key == key } ?: return false
        if (!flag.enabled) return false
        val minimum = flag.minimumVersion?.trim().orEmpty()
        return minimum.isBlank() || VersionGate.atLeast(appVersion, minimum)
    }
}

object VersionGate {
    fun atLeast(currentVersion: String, minimumVersion: String): Boolean =
        compare(currentVersion, minimumVersion) >= 0

    fun compare(left: String, right: String): Int {
        val leftParts = numericParts(left)
        val rightParts = numericParts(right)
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }

    private fun numericParts(value: String): List<Int> = value
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

interface DiagnosticsProvider {
    fun snapshot(): DiagnosticsSnapshot
}
