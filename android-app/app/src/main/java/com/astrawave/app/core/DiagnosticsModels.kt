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
}

interface DiagnosticsProvider {
    fun snapshot(): DiagnosticsSnapshot
}
