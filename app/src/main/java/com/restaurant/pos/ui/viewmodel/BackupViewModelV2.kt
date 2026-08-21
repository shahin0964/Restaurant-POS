package com.restaurant.pos.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.pos.data.backupv2.*
import com.restaurant.pos.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class BackupUiStateV2 {
    object Idle : BackupUiStateV2()
    data class Loading(val message: String) : BackupUiStateV2()
    data class ExportSuccess(val summary: String, val metadata: BackupMetadataV2) : BackupUiStateV2()
    data class ExportError(val message: String) : BackupUiStateV2()
    data class ValidationSuccess(val payload: BackupPayloadV2, val summary: String) : BackupUiStateV2()
    data class ValidationError(val message: String) : BackupUiStateV2()
    data class RestoreSuccess(val summary: String) : BackupUiStateV2()
    data class RestoreError(val message: String) : BackupUiStateV2()
}

class BackupViewModelV2(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val backupEngine = BackupEngineV2(application, database)

    private val _uiState = MutableStateFlow<BackupUiStateV2>(BackupUiStateV2.Idle)
    val uiState: StateFlow<BackupUiStateV2> = _uiState.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(AutoBackupSchedulerV2.isAutoBackupEnabled(application))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupIntervalHours = MutableStateFlow(AutoBackupSchedulerV2.getAutoBackupIntervalHours(application))
    val autoBackupIntervalHours: StateFlow<Int> = _autoBackupIntervalHours.asStateFlow()

    private val _lastAutoBackupTimestamp = MutableStateFlow(AutoBackupSchedulerV2.getLastBackupTimestamp(application))
    val lastAutoBackupTimestamp: StateFlow<Long> = _lastAutoBackupTimestamp.asStateFlow()

    private val _lastAutoBackupStatus = MutableStateFlow(AutoBackupSchedulerV2.getLastBackupStatus(application))
    val lastAutoBackupStatus: StateFlow<String> = _lastAutoBackupStatus.asStateFlow()

    private val _lastAutoBackupMessage = MutableStateFlow(AutoBackupSchedulerV2.getLastBackupMessage(application))
    val lastAutoBackupMessage: StateFlow<String> = _lastAutoBackupMessage.asStateFlow()

    private val _lastAutoBackupFilename = MutableStateFlow(AutoBackupSchedulerV2.getLastBackupFilename(application))
    val lastAutoBackupFilename: StateFlow<String> = _lastAutoBackupFilename.asStateFlow()

    fun refreshAutoBackupState() {
        val app = getApplication<Application>()
        _autoBackupEnabled.value = AutoBackupSchedulerV2.isAutoBackupEnabled(app)
        _autoBackupIntervalHours.value = AutoBackupSchedulerV2.getAutoBackupIntervalHours(app)
        _lastAutoBackupTimestamp.value = AutoBackupSchedulerV2.getLastBackupTimestamp(app)
        _lastAutoBackupStatus.value = AutoBackupSchedulerV2.getLastBackupStatus(app)
        _lastAutoBackupMessage.value = AutoBackupSchedulerV2.getLastBackupMessage(app)
        _lastAutoBackupFilename.value = AutoBackupSchedulerV2.getLastBackupFilename(app)
    }

    fun setAutoBackupEnabled(enabled: Boolean, intervalHours: Int = 24) {
        val app = getApplication<Application>()
        AutoBackupSchedulerV2.setAutoBackupEnabled(app, enabled, intervalHours)
        refreshAutoBackupState()
    }

    fun triggerManualAutoBackupNow() {
        viewModelScope.launch {
            _uiState.value = BackupUiStateV2.Loading("Running Automatic Backup Now...")
            val app = getApplication<Application>()
            try {
                withContext(Dispatchers.IO) {
                    val backupDir = java.io.File(app.filesDir, "auto_backups")
                    if (!backupDir.exists()) backupDir.mkdirs()

                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault())
                    val timestampStr = sdf.format(java.util.Date())
                    val filename = "pos_backup_v2_auto_${timestampStr}.json"
                    val backupFile = java.io.File(backupDir, filename)

                    val exportResult = backupFile.outputStream().use { stream ->
                        backupEngine.exportBackup(stream)
                    }

                    if (exportResult is BackupExportResultV2.Success) {
                        val fileUri = Uri.fromFile(backupFile)
                        val validateResult = backupEngine.importAndValidateFromUri(fileUri)
                        if (validateResult is BackupValidationResultV2.Valid) {
                            AutoBackupSchedulerV2.updateLastBackupResult(
                                app, System.currentTimeMillis(), "SUCCESS",
                                "Automatic backup completed successfully.", filename
                            )
                            // Cleanup retention
                            val autoBackupFiles = backupDir.listFiles { file ->
                                file.isFile && file.name.startsWith("pos_backup_v2_auto_") && file.name.endsWith(".json")
                            }
                            if (autoBackupFiles != null && autoBackupFiles.size > 7) {
                                val sorted = autoBackupFiles.sortedByDescending { it.lastModified() }
                                for (i in 7 until sorted.size) sorted[i].delete()
                            }
                        } else {
                            if (backupFile.exists()) backupFile.delete()
                            AutoBackupSchedulerV2.updateLastBackupResult(
                                app, System.currentTimeMillis(), "FAILED",
                                "Integrity validation failed", ""
                            )
                        }
                    } else {
                        if (backupFile.exists()) backupFile.delete()
                        AutoBackupSchedulerV2.updateLastBackupResult(
                            app, System.currentTimeMillis(), "FAILED",
                            (exportResult as? BackupExportResultV2.Error)?.message ?: "Export error", ""
                        )
                    }
                }
                refreshAutoBackupState()
                if (_lastAutoBackupStatus.value == "SUCCESS") {
                    _uiState.value = BackupUiStateV2.ExportSuccess(
                        "Automatic backup completed successfully.",
                        BackupMetadataV2(
                            appVersion = "1.0",
                            dbVersion = 19,
                            createdAtFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                    )
                } else {
                    _uiState.value = BackupUiStateV2.ExportError(_lastAutoBackupMessage.value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = BackupUiStateV2.ExportError("Failed to trigger auto backup: ${e.message}")
            }
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiStateV2.Loading("Creating POS Backup V2...")
            try {
                val outputStream = getApplication<Application>().contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _uiState.value = BackupUiStateV2.ExportError("Failed to open output stream for selected file.")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    outputStream.use { stream ->
                        backupEngine.exportBackup(stream)
                    }
                }

                when (result) {
                    is BackupExportResultV2.Success -> {
                        _uiState.value = BackupUiStateV2.ExportSuccess(result.summary, result.metadata)
                    }
                    is BackupExportResultV2.Error -> {
                        _uiState.value = BackupUiStateV2.ExportError(result.message)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = BackupUiStateV2.ExportError("Error during export: ${e.message}")
            }
        }
    }

    fun importAndValidateFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiStateV2.Loading("Reading and validating backup file...")
            try {
                val result = withContext(Dispatchers.IO) {
                    backupEngine.importAndValidateFromUri(uri)
                }

                when (result) {
                    is BackupValidationResultV2.Valid -> {
                        _uiState.value = BackupUiStateV2.ValidationSuccess(result.payload, result.summary)
                    }
                    is BackupValidationResultV2.Invalid -> {
                        _uiState.value = BackupUiStateV2.ValidationError(result.reason)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = BackupUiStateV2.ValidationError("Error reading file: ${e.message}")
            }
        }
    }

    fun confirmRestore(payload: BackupPayloadV2) {
        viewModelScope.launch {
            _uiState.value = BackupUiStateV2.Loading("Restoring database, settings, and assets...")
            try {
                val result = withContext(Dispatchers.IO) {
                    backupEngine.restoreBackup(payload)
                }

                when (result) {
                    is BackupRestoreResultV2.Success -> {
                        _uiState.value = BackupUiStateV2.RestoreSuccess(result.restoredSummary)
                    }
                    is BackupRestoreResultV2.Error -> {
                        _uiState.value = BackupUiStateV2.RestoreError(result.message)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = BackupUiStateV2.RestoreError("Error during restore: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = BackupUiStateV2.Idle
    }
}
