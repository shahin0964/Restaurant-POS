package com.restaurant.pos.data.backupv2

import java.io.File

/**
 * Metadata header for Backup V2 format.
 */
data class BackupMetadataV2(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val appVersion: String,
    val dbVersion: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAtFormatted: String = "",
    val deviceModel: String = "",
    val recordCounts: Map<String, Int> = emptyMap()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val FORMAT_IDENTIFIER = "POS_BACKUP_V2"
    }
}

/**
 * Local file asset payload (e.g. item images, category images, shop logos).
 */
data class BackupAssetV2(
    val relativePath: String,
    val mimeType: String,
    val base64Data: String,
    val sizeBytes: Long
)

/**
 * Complete container for all Room entity data.
 */
data class BackupDatabaseDataV2(
    val categories: List<com.restaurant.pos.data.db.CategoryEntity> = emptyList(),
    val menuItems: List<com.restaurant.pos.data.db.MenuItemEntity> = emptyList(),
    val orders: List<com.restaurant.pos.data.db.OrderEntity> = emptyList(),
    val orderItems: List<com.restaurant.pos.data.db.OrderItemEntity> = emptyList(),
    val users: List<com.restaurant.pos.data.db.UserEntity> = emptyList(),
    val tables: List<com.restaurant.pos.data.db.TableEntity> = emptyList(),
    val expenses: List<com.restaurant.pos.data.db.ExpenseEntity> = emptyList(),
    val stockLogs: List<com.restaurant.pos.data.db.StockLogEntity> = emptyList(),
    val offers: List<com.restaurant.pos.data.db.OfferEntity> = emptyList(),
    val notifications: List<com.restaurant.pos.data.db.NotificationEntity> = emptyList(),
    val staffFoods: List<com.restaurant.pos.data.db.StaffFoodEntity> = emptyList(),
    val syncRecords: List<com.restaurant.pos.data.db.SyncRecordEntity> = emptyList(),
    val printerSetting: com.restaurant.pos.data.db.PrinterSettingEntity? = null,
    val receiptSetting: com.restaurant.pos.data.db.ReceiptSettingEntity? = null
)

/**
 * Top-level backup payload object containing metadata, DB data, prefs, and local assets.
 */
data class BackupPayloadV2(
    val metadata: BackupMetadataV2,
    val databaseData: BackupDatabaseDataV2,
    val preferencesData: Map<String, Map<String, Any?>> = emptyMap(),
    val assets: List<BackupAssetV2> = emptyList()
)

sealed class BackupExportResultV2 {
    data class Success(
        val file: File?,
        val metadata: BackupMetadataV2,
        val summary: String
    ) : BackupExportResultV2()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : BackupExportResultV2()
}

sealed class BackupValidationResultV2 {
    data class Valid(
        val payload: BackupPayloadV2,
        val summary: String
    ) : BackupValidationResultV2()

    data class Invalid(
        val reason: String
    ) : BackupValidationResultV2()
}

sealed class BackupRestoreResultV2 {
    data class Success(
        val restoredSummary: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : BackupRestoreResultV2()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : BackupRestoreResultV2()
}
