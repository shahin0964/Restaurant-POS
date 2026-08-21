package com.restaurant.pos.data.backupv2

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.restaurant.pos.data.db.AppDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean Backup & Restore Engine V2.
 * Fully decoupled from old backup system and UI.
 */
class BackupEngineV2(
    private val context: Context,
    private val database: AppDatabase
) {

    /**
     * Reads all DB entities, preferences, and local assets, builds JSON payload, and writes to OutputStream.
     */
    suspend fun exportBackup(outputStream: OutputStream): BackupExportResultV2 {
        return try {
            val categories = database.categoryDao().getAllCategoriesSync()
            val menuItems = database.menuItemDao().getAllMenuItemsSync()
            val orders = database.orderDao().getAllOrderEntities()
            val orderItems = database.orderDao().getAllOrderItemEntities()
            val users = database.userDao().getAllUsersSync()
            val tables = database.tableDao().getAllTablesSync()
            val expenses = database.expenseDao().getAllExpensesSync()
            val stockLogs = database.stockLogDao().getAllStockLogsSync()
            val offers = database.offerDao().getAllOffersSync()
            val notifications = database.notificationDao().getAllNotificationsSync()
            val staffFoods = database.staffFoodDao().getAllStaffFoodSync()
            val syncRecords = database.syncRecordDao().getAllSyncRecordsSync()
            val printerSetting = database.printerSettingDao().getPrinterSettingSync()
            val receiptSetting = database.receiptSettingDao().getReceiptSettingSync()

            val recordCounts = mapOf(
                "categories" to categories.size,
                "menuItems" to menuItems.size,
                "orders" to orders.size,
                "orderItems" to orderItems.size,
                "users" to users.size,
                "tables" to tables.size,
                "expenses" to expenses.size,
                "stockLogs" to stockLogs.size,
                "offers" to offers.size,
                "notifications" to notifications.size,
                "staffFoods" to staffFoods.size,
                "syncRecords" to syncRecords.size,
                "printerSetting" to (if (printerSetting != null) 1 else 0),
                "receiptSetting" to (if (receiptSetting != null) 1 else 0)
            )

            val appVersion = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }

            val timestamp = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val createdAtFormatted = sdf.format(Date(timestamp))
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

            val metadata = BackupMetadataV2(
                formatVersion = BackupMetadataV2.CURRENT_FORMAT_VERSION,
                appVersion = appVersion,
                dbVersion = 19,
                timestamp = timestamp,
                createdAtFormatted = createdAtFormatted,
                deviceModel = deviceModel,
                recordCounts = recordCounts
            )

            val databaseData = BackupDatabaseDataV2(
                categories = categories,
                menuItems = menuItems,
                orders = orders,
                orderItems = orderItems,
                users = users,
                tables = tables,
                expenses = expenses,
                stockLogs = stockLogs,
                offers = offers,
                notifications = notifications,
                staffFoods = staffFoods,
                syncRecords = syncRecords,
                printerSetting = printerSetting,
                receiptSetting = receiptSetting
            )

            val preferencesData = BackupPreferenceManagerV2.exportPreferences(context)
            val assets = BackupAssetManagerV2.exportAssets(context, categories, menuItems, receiptSetting)

            val payload = BackupPayloadV2(
                metadata = metadata,
                databaseData = databaseData,
                preferencesData = preferencesData,
                assets = assets
            )

            val serializedJson = BackupSerializerV2.serializePayload(payload)
            outputStream.write(serializedJson.toByteArray(Charsets.UTF_8))
            outputStream.flush()

            val summaryText = "Backup created with ${orders.size} orders, ${menuItems.size} items, ${categories.size} categories, ${users.size} users, ${assets.size} local assets."
            BackupExportResultV2.Success(
                file = null,
                metadata = metadata,
                summary = summaryText
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            BackupExportResultV2.Error("Failed to export backup: ${e.message}", e)
        }
    }

    /**
     * Reads InputStream, parses JSON, and performs strict validation.
     * Does NOT touch the database.
     */
    fun importAndValidate(inputStream: InputStream): BackupValidationResultV2 {
        return try {
            val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (jsonString.isBlank()) {
                return BackupValidationResultV2.Invalid("Backup file is empty.")
            }

            val payload = BackupSerializerV2.deserializePayload(jsonString)

            if (payload.metadata.formatVersion != BackupMetadataV2.CURRENT_FORMAT_VERSION) {
                return BackupValidationResultV2.Invalid("Unsupported backup format version: ${payload.metadata.formatVersion}. Expected: ${BackupMetadataV2.CURRENT_FORMAT_VERSION}")
            }

            if (payload.metadata.dbVersion > 19) {
                return BackupValidationResultV2.Invalid("Backup schema version (${payload.metadata.dbVersion}) is newer than current app database schema version (19).")
            }

            val counts = payload.metadata.recordCounts
            val summary = "Valid Backup V2 (${payload.metadata.createdAtFormatted}): " +
                    "${payload.databaseData.orders.size} orders, " +
                    "${payload.databaseData.menuItems.size} products, " +
                    "${payload.databaseData.categories.size} categories, " +
                    "${payload.databaseData.users.size} users, " +
                    "${payload.assets.size} assets."

            BackupValidationResultV2.Valid(payload, summary)
        } catch (e: Exception) {
            e.printStackTrace()
            BackupValidationResultV2.Invalid("Corrupted or invalid backup file: ${e.message}")
        }
    }

    fun importAndValidateFromUri(uri: Uri): BackupValidationResultV2 {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return BackupValidationResultV2.Invalid("Cannot open input stream for URI: $uri")
            inputStream.use { importAndValidate(it) }
        } catch (e: Exception) {
            BackupValidationResultV2.Invalid("Error reading backup file: ${e.message}")
        }
    }

    /**
     * Performs atomic DB restore inside a Room transaction.
     * Restores entities preserving exact primary key IDs, restores assets, and restores preferences.
     */
    suspend fun restoreBackup(payload: BackupPayloadV2): BackupRestoreResultV2 {
        return try {
            val dbData = payload.databaseData

            // Restore assets first so files exist before DB insertion
            val restoredAssetPaths = BackupAssetManagerV2.restoreAssets(context, payload.assets)

            // Execute atomic database restoration inside a transaction
            database.withTransaction {
                // 1. Delete all existing records in child-first order
                database.orderDao().clearAllOrderItems()
                database.orderDao().clearAllOrders()
                database.stockLogDao().clearAllStockLogs()
                database.menuItemDao().clearAll()
                database.categoryDao().clearAll()
                database.tableDao().clearAllTables()
                database.userDao().clearAllUsers()
                database.expenseDao().clearAllExpenses()
                database.offerDao().clearAllOffers()
                database.notificationDao().clearAllNotifications()
                database.staffFoodDao().clearAllStaffFood()
                database.syncRecordDao().clearAll()
                database.printerSettingDao().clearPrinterSettings()
                database.receiptSettingDao().clearReceiptSettings()

                // 2. Insert records in parent-first order, preserving primary key IDs
                if (dbData.categories.isNotEmpty()) {
                    database.categoryDao().insertCategories(dbData.categories)
                }
                if (dbData.tables.isNotEmpty()) {
                    database.tableDao().insertTables(dbData.tables)
                }
                if (dbData.users.isNotEmpty()) {
                    database.userDao().insertUsers(dbData.users)
                }
                if (dbData.menuItems.isNotEmpty()) {
                    database.menuItemDao().insertMenuItems(dbData.menuItems)
                }
                if (dbData.orders.isNotEmpty()) {
                    database.orderDao().insertOrders(dbData.orders)
                }
                if (dbData.orderItems.isNotEmpty()) {
                    database.orderDao().insertOrderItems(dbData.orderItems)
                }
                if (dbData.expenses.isNotEmpty()) {
                    database.expenseDao().insertExpenses(dbData.expenses)
                }
                if (dbData.stockLogs.isNotEmpty()) {
                    database.stockLogDao().insertStockLogs(dbData.stockLogs)
                }
                if (dbData.offers.isNotEmpty()) {
                    database.offerDao().insertOffers(dbData.offers)
                }
                if (dbData.notifications.isNotEmpty()) {
                    database.notificationDao().insertNotifications(dbData.notifications)
                }
                if (dbData.staffFoods.isNotEmpty()) {
                    database.staffFoodDao().insertStaffFoodList(dbData.staffFoods)
                }
                if (dbData.syncRecords.isNotEmpty()) {
                    database.syncRecordDao().insertOrUpdateAll(dbData.syncRecords)
                }
                if (dbData.printerSetting != null) {
                    database.printerSettingDao().savePrinterSetting(dbData.printerSetting)
                }
                if (dbData.receiptSetting != null) {
                    database.receiptSettingDao().saveReceiptSetting(dbData.receiptSetting)
                }
            }

            // Restore SharedPreferences
            if (payload.preferencesData.isNotEmpty()) {
                BackupPreferenceManagerV2.restorePreferences(context, payload.preferencesData)
            }

            val summary = "Successfully restored ${dbData.orders.size} orders, " +
                    "${dbData.menuItems.size} products, ${dbData.categories.size} categories, " +
                    "${dbData.users.size} users, and ${payload.assets.size} local assets."

            BackupRestoreResultV2.Success(restoredSummary = summary)
        } catch (e: Throwable) {
            e.printStackTrace()
            BackupRestoreResultV2.Error("Failed during atomic backup restore: ${e.message}", e)
        }
    }
}
