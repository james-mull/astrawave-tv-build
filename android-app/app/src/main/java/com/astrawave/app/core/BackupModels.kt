package com.astrawave.app.core

/** Versioned backup contract for portable AstraWave user state. Secrets and credentials are excluded. */
data class AstraWaveBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val profiles: List<BackupProfile> = emptyList(),
    val lists: List<BackupList> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val progress: List<BackupProgress> = emptyList(),
    val sourceReferences: List<BackupSourceReference> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

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

object BackupValidation {
    fun compatibilityErrors(backup: AstraWaveBackup): List<String> = buildList {
        if (backup.schemaVersion <= 0) add("Backup schema version is invalid")
        if (backup.schemaVersion > AstraWaveBackup.CURRENT_SCHEMA_VERSION) {
            add("Backup was created by a newer AstraWave schema")
        }
        if (backup.exportedAtEpochMs <= 0L) add("Backup export timestamp is invalid")
        if (backup.profiles.any { it.id.isBlank() || it.name.isBlank() }) add("Backup contains an invalid profile")
        if (backup.lists.any { it.id.isBlank() || it.profileId.isBlank() || it.name.isBlank() }) add("Backup contains an invalid list")
        if (backup.progress.any { it.profileId.isBlank() || it.mediaId.isBlank() || it.positionMs < 0L || it.durationMs < 0L || (it.durationMs > 0L && it.positionMs > it.durationMs) }) {
            add("Backup contains invalid playback progress")
        }
        if (backup.sourceReferences.any { it.id.isBlank() || it.profileId.isBlank() || it.name.isBlank() || it.type.isBlank() }) {
            add("Backup contains an invalid source reference")
        }
    }

    fun canImport(backup: AstraWaveBackup): Boolean = compatibilityErrors(backup).isEmpty()
}

interface BackupGateway {
    fun exportBackup(): AstraWaveBackup
    fun importBackup(backup: AstraWaveBackup): BackupImportReport
}
