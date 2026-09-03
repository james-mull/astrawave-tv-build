package com.astrawave.app.core

/** Versioned backup contract for portable AstraWave user state. Secrets and credentials are excluded. */
data class AstraWaveBackup(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val profiles: List<BackupProfile> = emptyList(),
    val lists: List<BackupList> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val progress: List<BackupProgress> = emptyList(),
    val sourceReferences: List<BackupSourceReference> = emptyList(),
)

data class BackupProfile(
    val id: String,
    val name: String,
    val isKids: Boolean = false,
)

data class BackupList(
    val id: String,
    val profileId: String,
    val name: String,
    val description: String = "",
    val itemIds: List<String> = emptyList(),
)

data class BackupProgress(
    val profileId: String,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
)

data class BackupSourceReference(
    val id: String,
    val profileId: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
)

data class BackupImportReport(
    val importedProfiles: Int,
    val importedLists: Int,
    val importedProgressItems: Int,
    val importedSourceReferences: Int,
    val warnings: List<String> = emptyList(),
)

interface BackupGateway {
    fun exportBackup(): AstraWaveBackup
    fun importBackup(backup: AstraWaveBackup): BackupImportReport
}
