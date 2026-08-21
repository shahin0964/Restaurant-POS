package com.restaurant.pos.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.restaurant.pos.data.db.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupFileInfo(
    val fileName: String,
    val uriString: String,
    val createdAtFormatted: String,
    val sizeFormatted: String,
    val recordSummary: String,
    val timestamp: Long
)

data class AppSettingsBackupData(
    val language: String = "en",
    val appTheme: String = "system",
    val openingCash: String = "0.0",
    val notificationCategories: Map<String, Boolean> = emptyMap()
)

data class ParsedBackupData(
    val categories: List<CategoryEntity>,
    val menuItems: List<MenuItemEntity>,
    val orders: List<OrderEntity>,
    val orderItems: List<OrderItemEntity>,
    val expenses: List<ExpenseEntity>,
    val stockLogs: List<StockLogEntity>,
    val offers: List<OfferEntity>,
    val receiptSetting: ReceiptSettingEntity?,
    val printerSetting: PrinterSettingEntity?,
    val users: List<UserEntity>,
    val tables: List<TableEntity>,
    val notifications: List<NotificationEntity>,
    val staffFood: List<StaffFoodEntity>,
    val syncRecords: List<SyncRecordEntity>,
    val appSettings: AppSettingsBackupData? = null,
    val recordSummary: String,
    val timestamp: Long
)

sealed class BackupResult {
    data class Success(val fileInfo: BackupFileInfo) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

sealed class ValidationResult {
    data class Success(val parsedData: ParsedBackupData) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

sealed class RestoreResult {
    data class Success(val summary: String) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private data class GeneratedBackup(
        val jsonBytes: ByteArray,
        val formattedDate: String,
        val now: Long,
        val summary: String
    )

    private fun encodeFileToBase64(pathOrUri: String?): String? {
        if (pathOrUri.isNullOrBlank()) return null
        if (pathOrUri.startsWith("http://") || pathOrUri.startsWith("https://") || pathOrUri.startsWith("gs://")) {
            return null
        }
        return try {
            val cleanPath = if (pathOrUri.startsWith("file://")) pathOrUri.removePrefix("file://") else pathOrUri
            val file = File(cleanPath)
            if (file.exists() && file.isFile) {
                val bytes = file.readBytes()
                if (bytes.isNotEmpty()) {
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBase64ToFile(base64Str: String?, subDir: String, fileNamePrefix: String): String? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP)
            if (bytes.isNotEmpty()) {
                val dir = File(context.filesDir, subDir).apply { if (!exists()) mkdirs() }
                val file = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    out.write(bytes)
                    out.flush()
                }
                if (file.exists() && file.length() > 0) {
                    file.absolutePath
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun generateBackupPayload(): GeneratedBackup {
        val categories = database.categoryDao().getAllCategoriesSync()
        val menuItems = database.menuItemDao().getAllMenuItemsSync()
        val orders = database.orderDao().getAllOrderEntities()
        val orderItems = database.orderDao().getAllOrderItemEntities()
        val expenses = database.expenseDao().getAllExpensesSync()
        val stockLogs = database.stockLogDao().getAllStockLogsSync()
        val offers = database.offerDao().getAllOffersSync()
        val receiptSetting = database.receiptSettingDao().getReceiptSettingSync() ?: ReceiptSettingEntity()
        val printerSetting = database.printerSettingDao().getPrinterSettingSync() ?: PrinterSettingEntity()
        val users = database.userDao().getAllUsersSync()
        val tables = database.tableDao().getAllTablesSync()
        val notifications = database.notificationDao().getAllNotificationsSync()
        val staffFood = database.staffFoodDao().getAllStaffFoodSync()
        val syncRecords = database.syncRecordDao().getAllSyncRecordsSync()

        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val notifPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val formattedDate = dateFormat.format(Date(now))

        val metadataObj = JSONObject().apply {
            put("backupVersion", 1)
            put("appName", "Restaurant POS")
            put("packageName", context.packageName)
            put("appVersion", com.restaurant.pos.BuildConfig.VERSION_NAME)
            put("dbVersion", 19)
            put("timestamp", now)
            put("createdAt", formattedDate)

            val countsObj = JSONObject().apply {
                put("categories", categories.size)
                put("menuItems", menuItems.size)
                put("orders", orders.size)
                put("orderItems", orderItems.size)
                put("expenses", expenses.size)
                put("stockLogs", stockLogs.size)
                put("offers", offers.size)
                put("users", users.size)
                put("tables", tables.size)
                put("notifications", notifications.size)
                put("staffFood", staffFood.size)
                put("syncRecords", syncRecords.size)
                put("receiptSetting", 1)
                put("printerSetting", 1)
            }
            put("recordCounts", countsObj)
        }

        val dataObj = JSONObject().apply {
            put("categories", JSONArray().apply {
                categories.forEach { cat ->
                    put(JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("itemCount", cat.itemCount)
                        put("iconName", cat.iconName)
                        put("imageUrl", cat.imageUrl)
                        val b64 = encodeFileToBase64(cat.imageUrl)
                        if (b64 != null) put("imageBase64", b64)
                    })
                }
            })

            put("menuItems", JSONArray().apply {
                menuItems.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("categoryId", item.categoryId)
                        put("categoryName", item.categoryName)
                        put("price", item.price)
                        put("description", item.description)
                        put("imageUrl", item.imageUrl)
                        put("isAvailable", item.isAvailable)
                        put("stockQuantity", item.stockQuantity)
                        put("unit", item.unit)
                        put("lowStockThreshold", item.lowStockThreshold)
                        put("costPrice", item.costPrice)
                        put("discountEnabled", item.discountEnabled)
                        put("discountValue", item.discountValue)
                        put("discountType", item.discountType)
                        val b64 = encodeFileToBase64(item.imageUrl)
                        if (b64 != null) put("imageBase64", b64)
                    })
                }
            })

            put("orders", JSONArray().apply {
                orders.forEach { order ->
                    put(JSONObject().apply {
                        put("id", order.id)
                        put("orderNumber", order.orderNumber)
                        put("orderType", order.orderType)
                        put("tableNumber", order.tableNumber)
                        put("customerName", order.customerName)
                        put("status", order.status)
                        put("note", order.note)
                        put("subtotal", order.subtotal)
                        put("discount", order.discount)
                        put("tax", order.tax)
                        put("total", order.total)
                        put("paymentMethod", order.paymentMethod)
                        put("isPaid", order.isPaid)
                        put("timestamp", order.timestamp)
                        put("tableId", order.tableId ?: JSONObject.NULL)
                    })
                }
            })

            put("orderItems", JSONArray().apply {
                orderItems.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("orderId", item.orderId)
                        put("menuItemId", item.menuItemId)
                        put("menuItemName", item.menuItemName)
                        put("pricePerUnit", item.pricePerUnit)
                        put("quantity", item.quantity)
                        put("note", item.note)
                        put("costPriceAtSale", item.costPriceAtSale)
                    })
                }
            })

            put("expenses", JSONArray().apply {
                expenses.forEach { exp ->
                    put(JSONObject().apply {
                        put("id", exp.id)
                        put("title", exp.title)
                        put("amount", exp.amount)
                        put("category", exp.category)
                        put("note", exp.note)
                        put("timestamp", exp.timestamp)
                        put("paymentMethod", exp.paymentMethod)
                        put("expenseType", exp.expenseType)
                    })
                }
            })

            put("stockLogs", JSONArray().apply {
                stockLogs.forEach { log ->
                    put(JSONObject().apply {
                        put("id", log.id)
                        put("menuItemId", log.menuItemId)
                        put("menuItemName", log.menuItemName)
                        put("changeAmount", log.changeAmount)
                        put("type", log.type)
                        put("note", log.note)
                        put("timestamp", log.timestamp)
                    })
                }
            })

            put("offers", JSONArray().apply {
                offers.forEach { off ->
                    put(JSONObject().apply {
                        put("id", off.id)
                        put("name", off.name)
                        put("discountType", off.discountType)
                        put("discountValue", off.discountValue)
                        put("startDate", off.startDate)
                        put("endDate", off.endDate)
                        put("minOrderAmount", off.minOrderAmount)
                        put("maxDiscountAmount", off.maxDiscountAmount)
                        put("isActive", off.isActive)
                    })
                }
            })

            put("users", JSONArray().apply {
                users.forEach { u ->
                    put(JSONObject().apply {
                        put("id", u.id)
                        put("emailOrPhone", u.emailOrPhone)
                        put("name", u.name)
                        put("role", u.role)
                        put("passwordHash", u.passwordHash)
                        put("firebaseUid", u.firebaseUid ?: JSONObject.NULL)
                        put("isCurrentSession", u.isCurrentSession)
                        put("isActive", u.isActive)
                        put("permissions", u.permissions)
                    })
                }
            })

            put("tables", JSONArray().apply {
                tables.forEach { tbl ->
                    put(JSONObject().apply {
                        put("id", tbl.id)
                        put("name", tbl.name)
                        put("capacity", tbl.capacity)
                        put("isActive", tbl.isActive)
                        put("accountId", tbl.accountId)
                    })
                }
            })

            put("notifications", JSONArray().apply {
                notifications.forEach { notif ->
                    put(JSONObject().apply {
                        put("id", notif.id)
                        put("type", notif.type)
                        put("title", notif.title)
                        put("message", notif.message)
                        put("targetId", notif.targetId ?: JSONObject.NULL)
                        put("timestamp", notif.timestamp)
                        put("isRead", notif.isRead)
                    })
                }
            })

            put("staffFood", JSONArray().apply {
                staffFood.forEach { sf ->
                    put(JSONObject().apply {
                        put("id", sf.id)
                        put("staffName", sf.staffName)
                        put("productName", sf.productName)
                        put("quantity", sf.quantity)
                        put("unitPrice", sf.unitPrice)
                        put("totalPrice", sf.totalPrice)
                        put("timestamp", sf.timestamp)
                    })
                }
            })

            put("syncRecords", JSONArray().apply {
                syncRecords.forEach { sync ->
                    put(JSONObject().apply {
                        put("id", sync.id)
                        put("tableName", sync.tableName)
                        put("localId", sync.localId)
                        put("firestoreId", sync.firestoreId)
                        put("lastSyncTime", sync.lastSyncTime)
                        put("pendingSync", sync.pendingSync)
                        put("operation", sync.operation)
                        put("isDeleted", sync.isDeleted)
                    })
                }
            })

            put("receiptSetting", JSONObject().apply {
                put("id", receiptSetting.id)
                put("shopName", receiptSetting.shopName)
                put("phone", receiptSetting.phone)
                put("address", receiptSetting.address)
                put("email", receiptSetting.email)
                put("website", receiptSetting.website)
                put("logoUri", receiptSetting.logoUri)
                put("footerText", receiptSetting.footerText)
                put("currencySymbol", receiptSetting.currencySymbol)
                put("currencyCode", receiptSetting.currencyCode)
                put("isTaxEnabled", receiptSetting.isTaxEnabled)
                put("taxRate", receiptSetting.taxRate)
                put("showShopName", receiptSetting.showShopName)
                put("showLogo", receiptSetting.showLogo)
                put("showPhone", receiptSetting.showPhone)
                put("showAddress", receiptSetting.showAddress)
                put("showOrderNumber", receiptSetting.showOrderNumber)
                put("showDateTime", receiptSetting.showDateTime)
                put("showCustomerName", receiptSetting.showCustomerName)
                put("showOrderType", receiptSetting.showOrderType)
                put("showItems", receiptSetting.showItems)
                put("showQuantity", receiptSetting.showQuantity)
                put("showItemPrice", receiptSetting.showItemPrice)
                put("showSubtotal", receiptSetting.showSubtotal)
                put("showDiscount", receiptSetting.showDiscount)
                put("showTax", receiptSetting.showTax)
                put("showTotal", receiptSetting.showTotal)
                put("showPaymentStatus", receiptSetting.showPaymentStatus)
                put("showFooter", receiptSetting.showFooter)
                val b64 = encodeFileToBase64(receiptSetting.logoUri)
                if (b64 != null) put("logoBase64", b64)
            })

            put("printerSetting", JSONObject().apply {
                put("id", printerSetting.id)
                put("connectionType", printerSetting.connectionType)
                put("printerName", printerSetting.printerName)
                put("macAddress", printerSetting.macAddress)
                put("ipAddress", printerSetting.ipAddress)
                put("port", printerSetting.port)
                put("paperSize", printerSetting.paperSize)
                put("autoPrintOnOrder", printerSetting.autoPrintOnOrder)
                put("isConnected", false)
                put("printerType", printerSetting.printerType)
                put("bluetoothAddress", printerSetting.bluetoothAddress)
            })

            val notifCategoryKeys = listOf("notify_new_order", "notify_low_stock", "notify_out_of_stock", "notify_payment", "notify_order_ready", "notify_general")
            val notifObj = JSONObject().apply {
                notifCategoryKeys.forEach { key ->
                    put(key, notifPrefs.getBoolean(key, true))
                }
            }

            put("appSettings", JSONObject().apply {
                put("language", appPrefs.getString("language", "en") ?: "en")
                put("app_theme", appPrefs.getString("app_theme", "system") ?: "system")
                put("opening_cash", appPrefs.getString("opening_cash", "0.0") ?: "0.0")
                put("notificationCategories", notifObj)
            })
        }

        val rootObj = JSONObject().apply {
            put("metadata", metadataObj)
            put("data", dataObj)
        }

        val jsonBytes = rootObj.toString(2).toByteArray(Charsets.UTF_8)
        val summary = "${menuItems.size} Menu Items, ${orders.size} Orders, ${expenses.size} Expenses, ${categories.size} Categories, ${tables.size} Tables"

        return GeneratedBackup(
            jsonBytes = jsonBytes,
            formattedDate = formattedDate,
            now = now,
            summary = summary
        )
    }

    suspend fun createBackup(targetUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val payload = generateBackupPayload()

            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                outStream.write(payload.jsonBytes)
                outStream.flush()
            } ?: return@withContext BackupResult.Error("Failed to open storage output stream.")

            var fileName = "Restaurant_POS_Backup.json"
            var fileSize = payload.jsonBytes.size.toLong()

            context.contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val sizeKb = String.format(Locale.US, "%.1f KB", fileSize / 1024.0)

            val info = BackupFileInfo(
                fileName = fileName,
                uriString = targetUri.toString(),
                createdAtFormatted = payload.formattedDate,
                sizeFormatted = sizeKb,
                recordSummary = payload.summary,
                timestamp = payload.now
            )

            BackupResult.Success(info)
        } catch (e: Exception) {
            BackupResult.Error("Backup could not be created: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun createAutoBackup(): BackupResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val payload = generateBackupPayload()

            val autoBackupDir = File(context.filesDir, "auto_backups")
            if (!autoBackupDir.exists()) {
                autoBackupDir.mkdirs()
            }

            val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
            val fileName = "auto_backup_${fileDateFormat.format(Date(payload.now))}.json"
            val targetFile = File(autoBackupDir, fileName)
            val tempFile = File(autoBackupDir, "$fileName.tmp")

            FileOutputStream(tempFile).use { outStream ->
                outStream.write(payload.jsonBytes)
                outStream.flush()
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } else {
                return@withContext BackupResult.Error("Failed to write auto backup file.")
            }

            // Retention: Keep latest 7 auto backups
            cleanOldAutoBackups(autoBackupDir, maxKeep = 7)

            val fileSize = targetFile.length()
            val sizeKb = String.format(Locale.US, "%.1f KB", fileSize / 1024.0)

            val info = BackupFileInfo(
                fileName = fileName,
                uriString = targetFile.absolutePath,
                createdAtFormatted = payload.formattedDate,
                sizeFormatted = sizeKb,
                recordSummary = payload.summary,
                timestamp = payload.now
            )

            BackupResult.Success(info)
        } catch (e: Exception) {
            BackupResult.Error("Auto backup failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun cleanOldAutoBackups(dir: File, maxKeep: Int = 7) {
        try {
            val files = dir.listFiles { f ->
                f.isFile && f.name.startsWith("auto_backup_") && f.name.endsWith(".json")
            } ?: return
            if (files.size > maxKeep) {
                files.sortedByDescending { it.lastModified() }
                    .drop(maxKeep)
                    .forEach { fileToDelete ->
                        fileToDelete.delete()
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun validateBackup(sourceUri: Uri): ValidationResult {
        return try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            } ?: return ValidationResult.Error("Could not read backup file.")

            if (jsonString.isBlank()) {
                return ValidationResult.Error("Invalid or incompatible backup: File is empty.")
            }

            val rootObj = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                return ValidationResult.Error("Invalid or incompatible backup: JSON structure corrupted.")
            }

            if (!rootObj.has("metadata") || !rootObj.has("data")) {
                return ValidationResult.Error("Invalid or incompatible backup: Missing metadata or data structure.")
            }

            val metaObj = rootObj.getJSONObject("metadata")
            val backupVer = metaObj.optInt("backupVersion", -1)
            val dbVer = metaObj.optInt("dbVersion", -1)

            if (backupVer < 1) {
                return ValidationResult.Error("Invalid or incompatible backup: Unsupported backup format version ($backupVer).")
            }

            if (dbVer > 19) {
                return ValidationResult.Error("Invalid or incompatible backup: Backup was created with a newer database version ($dbVer).")
            }

            val dataObj = rootObj.getJSONObject("data")

            // Parse Categories
            val categoriesList = mutableListOf<CategoryEntity>()
            if (dataObj.has("categories")) {
                val catArray = dataObj.getJSONArray("categories")
                for (i in 0 until catArray.length()) {
                    val o = catArray.getJSONObject(i)
                    val rawUrl = o.optString("imageUrl", "")
                    val base64Img = o.optString("imageBase64", "")
                    val restoredPath = if (base64Img.isNotBlank()) {
                        decodeBase64ToFile(base64Str = base64Img, subDir = "item_images", fileNamePrefix = "cat_${o.optLong("id", 0L)}") ?: rawUrl
                    } else rawUrl

                    categoriesList.add(
                        CategoryEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Category"),
                            itemCount = o.optInt("itemCount", 0),
                            iconName = o.optString("iconName", "burger"),
                            imageUrl = restoredPath
                        )
                    )
                }
            }

            // Parse Menu Items
            val menuItemsList = mutableListOf<MenuItemEntity>()
            if (dataObj.has("menuItems")) {
                val itemArray = dataObj.getJSONArray("menuItems")
                for (i in 0 until itemArray.length()) {
                    val o = itemArray.getJSONObject(i)
                    val rawUrl = o.optString("imageUrl", "")
                    val base64Img = o.optString("imageBase64", "")
                    val restoredPath = if (base64Img.isNotBlank()) {
                        decodeBase64ToFile(base64Str = base64Img, subDir = "item_images", fileNamePrefix = "item_${o.optLong("id", 0L)}") ?: rawUrl
                    } else rawUrl

                    menuItemsList.add(
                        MenuItemEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            categoryId = o.optLong("categoryId", 1L),
                            categoryName = o.optString("categoryName", ""),
                            price = o.optDouble("price", 0.0),
                            description = o.optString("description", ""),
                            imageUrl = restoredPath,
                            isAvailable = o.optBoolean("isAvailable", true),
                            stockQuantity = o.optInt("stockQuantity", 0),
                            unit = o.optString("unit", "pcs"),
                            lowStockThreshold = o.optInt("lowStockThreshold", 5),
                            costPrice = o.optDouble("costPrice", 0.0),
                            discountEnabled = o.optBoolean("discountEnabled", false),
                            discountValue = o.optDouble("discountValue", 0.0),
                            discountType = o.optString("discountType", "PERCENTAGE")
                        )
                    )
                }
            }

            // Parse Orders
            val ordersList = mutableListOf<OrderEntity>()
            if (dataObj.has("orders")) {
                val orderArray = dataObj.getJSONArray("orders")
                for (i in 0 until orderArray.length()) {
                    val o = orderArray.getJSONObject(i)
                    ordersList.add(
                        OrderEntity(
                            id = o.optLong("id", 0L),
                            orderNumber = o.optString("orderNumber", ""),
                            orderType = o.optString("orderType", "Dine In"),
                            tableNumber = o.optString("tableNumber", ""),
                            customerName = o.optString("customerName", "Walk-in Customer"),
                            status = o.optString("status", "Completed"),
                            note = o.optString("note", ""),
                            subtotal = o.optDouble("subtotal", 0.0),
                            discount = o.optDouble("discount", 0.0),
                            tax = o.optDouble("tax", 0.0),
                            total = o.optDouble("total", 0.0),
                            paymentMethod = o.optString("paymentMethod", "Cash"),
                            isPaid = o.optBoolean("isPaid", true),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            tableId = if (o.has("tableId") && !o.isNull("tableId")) o.optLong("tableId") else null
                        )
                    )
                }
            }

            // Parse Order Items
            val orderItemsList = mutableListOf<OrderItemEntity>()
            if (dataObj.has("orderItems")) {
                val oiArray = dataObj.getJSONArray("orderItems")
                for (i in 0 until oiArray.length()) {
                    val o = oiArray.getJSONObject(i)
                    orderItemsList.add(
                        OrderItemEntity(
                            id = o.optLong("id", 0L),
                            orderId = o.optLong("orderId", 0L),
                            menuItemId = o.optLong("menuItemId", 0L),
                            menuItemName = o.optString("menuItemName", ""),
                            pricePerUnit = o.optDouble("pricePerUnit", o.optDouble("price", 0.0)),
                            quantity = o.optInt("quantity", 1),
                            note = o.optString("note", ""),
                            costPriceAtSale = o.optDouble("costPriceAtSale", 0.0)
                        )
                    )
                }
            }

            // Parse Expenses
            val expensesList = mutableListOf<ExpenseEntity>()
            if (dataObj.has("expenses")) {
                val expArray = dataObj.getJSONArray("expenses")
                for (i in 0 until expArray.length()) {
                    val o = expArray.getJSONObject(i)
                    expensesList.add(
                        ExpenseEntity(
                            id = o.optLong("id", 0L),
                            title = o.optString("title", ""),
                            amount = o.optDouble("amount", 0.0),
                            category = o.optString("category", "General"),
                            note = o.optString("note", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            paymentMethod = o.optString("paymentMethod", "Cash"),
                            expenseType = o.optString("expenseType", "OPERATING")
                        )
                    )
                }
            }

            // Parse Stock Logs
            val stockLogsList = mutableListOf<StockLogEntity>()
            if (dataObj.has("stockLogs")) {
                val slArray = dataObj.getJSONArray("stockLogs")
                for (i in 0 until slArray.length()) {
                    val o = slArray.getJSONObject(i)
                    stockLogsList.add(
                        StockLogEntity(
                            id = o.optLong("id", 0L),
                            menuItemId = o.optLong("menuItemId", 0L),
                            menuItemName = o.optString("menuItemName", ""),
                            changeAmount = o.optInt("changeAmount", 0),
                            type = o.optString("type", "ADJUST"),
                            note = o.optString("note", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Parse Offers
            val offersList = mutableListOf<OfferEntity>()
            if (dataObj.has("offers")) {
                val offArray = dataObj.getJSONArray("offers")
                for (i in 0 until offArray.length()) {
                    val o = offArray.getJSONObject(i)
                    offersList.add(
                        OfferEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", o.optString("title", "Offer")),
                            discountType = o.optString("discountType", "PERCENTAGE"),
                            discountValue = o.optDouble("discountValue", o.optDouble("discountPercent", 0.0)),
                            startDate = o.optLong("startDate", System.currentTimeMillis()),
                            endDate = o.optLong("endDate", System.currentTimeMillis() + 86400000L),
                            minOrderAmount = o.optDouble("minOrderAmount", 0.0),
                            maxDiscountAmount = o.optDouble("maxDiscountAmount", 0.0),
                            isActive = o.optBoolean("isActive", true)
                        )
                    )
                }
            }

            // Parse Users
            val usersList = mutableListOf<UserEntity>()
            if (dataObj.has("users")) {
                val uArray = dataObj.getJSONArray("users")
                for (i in 0 until uArray.length()) {
                    val o = uArray.getJSONObject(i)
                    val uidStr = if (o.has("firebaseUid") && !o.isNull("firebaseUid")) o.optString("firebaseUid") else null
                    usersList.add(
                        UserEntity(
                            id = o.optLong("id", 0L),
                            emailOrPhone = o.optString("emailOrPhone", ""),
                            name = o.optString("name", "Staff"),
                            role = o.optString("role", "Administrator"),
                            passwordHash = o.optString("passwordHash", ""),
                            firebaseUid = uidStr,
                            isCurrentSession = o.optBoolean("isCurrentSession", false),
                            isActive = o.optBoolean("isActive", true),
                            permissions = o.optString("permissions", "")
                        )
                    )
                }
            }

            // Parse Tables
            val tablesList = mutableListOf<TableEntity>()
            if (dataObj.has("tables")) {
                val tblArray = dataObj.getJSONArray("tables")
                for (i in 0 until tblArray.length()) {
                    val o = tblArray.getJSONObject(i)
                    tablesList.add(
                        TableEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Table"),
                            capacity = o.optInt("capacity", 4),
                            isActive = o.optBoolean("isActive", true),
                            accountId = o.optString("accountId", "")
                        )
                    )
                }
            }

            // Parse Notifications
            val notificationsList = mutableListOf<NotificationEntity>()
            if (dataObj.has("notifications")) {
                val notifArray = dataObj.getJSONArray("notifications")
                for (i in 0 until notifArray.length()) {
                    val o = notifArray.getJSONObject(i)
                    val targetIdStr = if (o.has("targetId") && !o.isNull("targetId")) o.optString("targetId") else null
                    notificationsList.add(
                        NotificationEntity(
                            id = o.optLong("id", 0L),
                            type = o.optString("type", "NEW_ORDER"),
                            title = o.optString("title", ""),
                            message = o.optString("message", ""),
                            targetId = targetIdStr,
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            isRead = o.optBoolean("isRead", false)
                        )
                    )
                }
            }

            // Parse Staff Food
            val staffFoodList = mutableListOf<StaffFoodEntity>()
            if (dataObj.has("staffFood")) {
                val sfArray = dataObj.getJSONArray("staffFood")
                for (i in 0 until sfArray.length()) {
                    val o = sfArray.getJSONObject(i)
                    staffFoodList.add(
                        StaffFoodEntity(
                            id = o.optLong("id", 0L),
                            staffName = o.optString("staffName", ""),
                            productName = o.optString("productName", ""),
                            quantity = o.optInt("quantity", 1),
                            unitPrice = o.optDouble("unitPrice", 0.0),
                            totalPrice = o.optDouble("totalPrice", 0.0),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Parse Sync Records
            val syncRecordsList = mutableListOf<SyncRecordEntity>()
            if (dataObj.has("syncRecords")) {
                val srArray = dataObj.getJSONArray("syncRecords")
                for (i in 0 until srArray.length()) {
                    val o = srArray.getJSONObject(i)
                    syncRecordsList.add(
                        SyncRecordEntity(
                            id = o.optLong("id", 0L),
                            tableName = o.optString("tableName", ""),
                            localId = o.optLong("localId", 0L),
                            firestoreId = o.optString("firestoreId", ""),
                            lastSyncTime = o.optLong("lastSyncTime", 0L),
                            pendingSync = o.optBoolean("pendingSync", true),
                            operation = o.optString("operation", "INSERT"),
                            isDeleted = o.optBoolean("isDeleted", false)
                        )
                    )
                }
            }

            // Parse Receipt Setting
            var receiptSetting: ReceiptSettingEntity? = null
            if (dataObj.has("receiptSetting")) {
                val rs = dataObj.getJSONObject("receiptSetting")
                val rawLogo = rs.optString("logoUri", "")
                val logoB64 = rs.optString("logoBase64", "")
                val restoredLogoPath = if (logoB64.isNotBlank()) {
                    decodeBase64ToFile(base64Str = logoB64, subDir = "receipt_logos", fileNamePrefix = "logo") ?: rawLogo
                } else rawLogo

                receiptSetting = ReceiptSettingEntity(
                    id = rs.optInt("id", 1),
                    shopName = rs.optString("shopName", ""),
                    phone = rs.optString("phone", ""),
                    address = rs.optString("address", ""),
                    email = rs.optString("email", ""),
                    website = rs.optString("website", ""),
                    logoUri = restoredLogoPath,
                    footerText = rs.optString("footerText", ""),
                    currencySymbol = rs.optString("currencySymbol", "৳"),
                    currencyCode = rs.optString("currencyCode", "BDT"),
                    isTaxEnabled = rs.optBoolean("isTaxEnabled", false),
                    taxRate = rs.optDouble("taxRate", 0.0),
                    showShopName = rs.optBoolean("showShopName", true),
                    showLogo = rs.optBoolean("showLogo", true),
                    showPhone = rs.optBoolean("showPhone", true),
                    showAddress = rs.optBoolean("showAddress", true),
                    showOrderNumber = rs.optBoolean("showOrderNumber", true),
                    showDateTime = rs.optBoolean("showDateTime", true),
                    showCustomerName = rs.optBoolean("showCustomerName", true),
                    showOrderType = rs.optBoolean("showOrderType", true),
                    showItems = rs.optBoolean("showItems", true),
                    showQuantity = rs.optBoolean("showQuantity", true),
                    showItemPrice = rs.optBoolean("showItemPrice", true),
                    showSubtotal = rs.optBoolean("showSubtotal", true),
                    showDiscount = rs.optBoolean("showDiscount", true),
                    showTax = rs.optBoolean("showTax", true),
                    showTotal = rs.optBoolean("showTotal", true),
                    showPaymentStatus = rs.optBoolean("showPaymentStatus", true),
                    showFooter = rs.optBoolean("showFooter", true)
                )
            }

            // Parse Printer Setting
            var printerSetting: PrinterSettingEntity? = null
            if (dataObj.has("printerSetting")) {
                val ps = dataObj.getJSONObject("printerSetting")
                printerSetting = PrinterSettingEntity(
                    id = ps.optInt("id", 1),
                    connectionType = ps.optString("connectionType", "BUILT_IN"),
                    printerName = ps.optString("printerName", ""),
                    macAddress = ps.optString("macAddress", ""),
                    ipAddress = ps.optString("ipAddress", "192.168.1.100"),
                    port = ps.optInt("port", 9100),
                    paperSize = ps.optString("paperSize", "58mm"),
                    autoPrintOnOrder = ps.optBoolean("autoPrintOnOrder", true),
                    isConnected = false,
                    printerType = ps.optString("printerType", "Sunmi InnerPrinter"),
                    bluetoothAddress = ps.optString("bluetoothAddress", "")
                )
            }

            // Parse App Settings
            var appSettings: AppSettingsBackupData? = null
            if (dataObj.has("appSettings")) {
                val asObj = dataObj.getJSONObject("appSettings")
                val lang = asObj.optString("language", "en")
                val theme = asObj.optString("app_theme", "system")
                val cash = asObj.optString("opening_cash", "0.0")
                val notifMap = mutableMapOf<String, Boolean>()
                if (asObj.has("notificationCategories")) {
                    val ncObj = asObj.getJSONObject("notificationCategories")
                    val keys = ncObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        notifMap[k] = ncObj.optBoolean(k, true)
                    }
                }
                appSettings = AppSettingsBackupData(
                    language = lang,
                    appTheme = theme,
                    openingCash = cash,
                    notificationCategories = notifMap
                )
            }

            val recordSummary = "${menuItemsList.size} Menu Items, ${ordersList.size} Orders, ${expensesList.size} Expenses, ${categoriesList.size} Categories, ${tablesList.size} Tables"

            val parsedData = ParsedBackupData(
                categories = categoriesList,
                menuItems = menuItemsList,
                orders = ordersList,
                orderItems = orderItemsList,
                expenses = expensesList,
                stockLogs = stockLogsList,
                offers = offersList,
                receiptSetting = receiptSetting,
                printerSetting = printerSetting,
                users = usersList,
                tables = tablesList,
                notifications = notificationsList,
                staffFood = staffFoodList,
                syncRecords = syncRecordsList,
                appSettings = appSettings,
                recordSummary = recordSummary,
                timestamp = metaObj.optLong("timestamp", System.currentTimeMillis())
            )

            ValidationResult.Success(parsedData)
        } catch (e: Exception) {
            ValidationResult.Error("Invalid or incompatible backup: ${e.localizedMessage ?: "File format mismatch"}")
        }
    }

    suspend fun resetAllApplicationData(): Result<String> {
        return try {
            database.withTransaction {
                database.orderDao().clearAllOrderItems()
                database.orderDao().clearAllOrders()
                database.stockLogDao().clearAllStockLogs()
                database.menuItemDao().clearAll()
                database.categoryDao().clearAll()
                database.expenseDao().clearAllExpenses()
                database.offerDao().clearAllOffers()
                database.notificationDao().clearAllNotifications()
                database.tableDao().clearAllTables()
                database.staffFoodDao().clearAllStaffFood()
                database.syncRecordDao().clearAll()
                database.userDao().clearAllUsers()
            }
            Result.success("All application data has been successfully reset.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to reset application data: ${e.localizedMessage ?: "Database error"}"))
        }
    }

    suspend fun restoreBackup(parsedData: ParsedBackupData): RestoreResult {
        return try {
            val existingUsers = database.userDao().getAllUsersSync()

            database.withTransaction {
                // Clear existing database tables
                database.orderDao().clearAllOrderItems()
                database.orderDao().clearAllOrders()
                database.stockLogDao().clearAllStockLogs()
                database.menuItemDao().clearAll()
                database.categoryDao().clearAll()
                database.expenseDao().clearAllExpenses()
                database.offerDao().clearAllOffers()
                database.notificationDao().clearAllNotifications()
                database.tableDao().clearAllTables()
                database.staffFoodDao().clearAllStaffFood()
                database.syncRecordDao().clearAll()
                database.userDao().clearAllUsers()

                // Insert Tables first (dependency parent)
                if (parsedData.tables.isNotEmpty()) {
                    database.tableDao().insertTables(parsedData.tables)
                }

                // Insert Categories
                if (parsedData.categories.isNotEmpty()) {
                    database.categoryDao().insertCategories(parsedData.categories)
                }

                // Insert Menu Items
                if (parsedData.menuItems.isNotEmpty()) {
                    database.menuItemDao().insertMenuItems(parsedData.menuItems)
                }

                // Restore Users safely
                if (parsedData.users.isNotEmpty()) {
                    val finalUsersToRestore = parsedData.users.map { restoredU ->
                        val existingMatch = existingUsers.find {
                            it.emailOrPhone.equals(restoredU.emailOrPhone, ignoreCase = true) || it.id == restoredU.id
                        }
                        if (existingMatch != null && (restoredU.passwordHash == "[PROTECTED]" || restoredU.passwordHash.isBlank())) {
                            restoredU.copy(passwordHash = existingMatch.passwordHash)
                        } else if (existingMatch == null && (restoredU.passwordHash == "[PROTECTED]" || restoredU.passwordHash.isBlank())) {
                            restoredU.copy(passwordHash = "123456")
                        } else {
                            restoredU
                        }
                    }
                    database.userDao().insertUsers(finalUsersToRestore)
                }

                // Insert Orders & Order Items
                if (parsedData.orders.isNotEmpty()) {
                    database.orderDao().insertOrders(parsedData.orders)
                }
                if (parsedData.orderItems.isNotEmpty()) {
                    database.orderDao().insertOrderItems(parsedData.orderItems)
                }

                // Insert Expenses
                if (parsedData.expenses.isNotEmpty()) {
                    database.expenseDao().insertExpenses(parsedData.expenses)
                }

                // Insert Stock Logs
                if (parsedData.stockLogs.isNotEmpty()) {
                    database.stockLogDao().insertStockLogs(parsedData.stockLogs)
                }

                // Insert Offers
                if (parsedData.offers.isNotEmpty()) {
                    database.offerDao().insertOffers(parsedData.offers)
                }

                // Insert Notifications
                if (parsedData.notifications.isNotEmpty()) {
                    database.notificationDao().insertNotifications(parsedData.notifications)
                }

                // Insert Staff Food
                if (parsedData.staffFood.isNotEmpty()) {
                    database.staffFoodDao().insertStaffFoodList(parsedData.staffFood)
                }

                // Insert Sync Records
                if (parsedData.syncRecords.isNotEmpty()) {
                    database.syncRecordDao().insertOrUpdateAll(parsedData.syncRecords)
                }

                // Restore Receipt Setting
                parsedData.receiptSetting?.let { rs ->
                    database.receiptSettingDao().saveReceiptSetting(rs)
                }

                // Restore Printer Setting
                parsedData.printerSetting?.let { ps ->
                    database.printerSettingDao().savePrinterSetting(ps)
                }
            }

            // Restore App Settings (SharedPreferences)
            parsedData.appSettings?.let { appSet ->
                val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                appPrefs.edit().apply {
                    putString("language", appSet.language)
                    putString("app_theme", appSet.appTheme)
                    putString("opening_cash", appSet.openingCash)
                    apply()
                }
                if (appSet.notificationCategories.isNotEmpty()) {
                    val notifPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
                    val notifEditor = notifPrefs.edit()
                    appSet.notificationCategories.forEach { (key, enabled) ->
                        notifEditor.putBoolean(key, enabled)
                    }
                    notifEditor.apply()
                }
            }

            // Post-restore verification
            val verifiedMenuCount = database.menuItemDao().getAllMenuItemsSync().size
            val verifiedOrderCount = database.orderDao().getAllOrderEntities().size

            val summaryText = "Successfully restored $verifiedMenuCount menu items, $verifiedOrderCount orders, and settings."
            RestoreResult.Success(summaryText)
        } catch (e: Exception) {
            RestoreResult.Error("Restore failed: ${e.localizedMessage ?: "Database transaction error"}")
        }
    }
}
