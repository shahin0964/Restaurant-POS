package com.restaurant.pos.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.restaurant.pos.data.db.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupFileInfo(
    val fileName: String,
    val uriString: String,
    val createdAtFormatted: String,
    val sizeFormatted: String,
    val recordSummary: String,
    val timestamp: Long
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
    suspend fun createBackup(targetUri: Uri): BackupResult {
        return try {
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

            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val formattedDate = dateFormat.format(Date(now))

            val metadataObj = JSONObject().apply {
                put("backupVersion", 1)
                put("appName", "Restaurant POS")
                put("packageName", context.packageName)
                put("appVersion", com.restaurant.pos.BuildConfig.VERSION_NAME)
                put("dbVersion", 8)
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
                            // PROTECTED: Do not export passwords or secrets in plain text
                            put("passwordHash", "[PROTECTED]")
                            put("isCurrentSession", false)
                            put("isActive", u.isActive)
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
            }

            val rootObj = JSONObject().apply {
                put("metadata", metadataObj)
                put("data", dataObj)
            }

            val jsonBytes = rootObj.toString(2).toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                outStream.write(jsonBytes)
                outStream.flush()
            } ?: return BackupResult.Error("Failed to open storage output stream.")

            var fileName = "Restaurant_POS_Backup.json"
            var fileSize = jsonBytes.size.toLong()

            context.contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val sizeKb = String.format(Locale.US, "%.1f KB", fileSize / 1024.0)
            val summary = "${menuItems.size} Menu Items, ${orders.size} Orders, ${expenses.size} Expenses, ${categories.size} Categories"

            val info = BackupFileInfo(
                fileName = fileName,
                uriString = targetUri.toString(),
                createdAtFormatted = formattedDate,
                sizeFormatted = sizeKb,
                recordSummary = summary,
                timestamp = now
            )

            BackupResult.Success(info)
        } catch (e: Exception) {
            BackupResult.Error("Backup could not be created: ${e.localizedMessage ?: "Unknown error"}")
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

            if (dbVer > 8) {
                return ValidationResult.Error("Invalid or incompatible backup: Backup was created with a newer database version ($dbVer).")
            }

            val dataObj = rootObj.getJSONObject("data")

            // Parse Categories
            val categoriesList = mutableListOf<CategoryEntity>()
            if (dataObj.has("categories")) {
                val catArray = dataObj.getJSONArray("categories")
                for (i in 0 until catArray.length()) {
                    val o = catArray.getJSONObject(i)
                    categoriesList.add(
                        CategoryEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Category"),
                            itemCount = o.optInt("itemCount", 0),
                            iconName = o.optString("iconName", "burger"),
                            imageUrl = o.optString("imageUrl", "")
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
                    menuItemsList.add(
                        MenuItemEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            categoryId = o.optLong("categoryId", 1L),
                            categoryName = o.optString("categoryName", ""),
                            price = o.optDouble("price", 0.0),
                            description = o.optString("description", ""),
                            imageUrl = o.optString("imageUrl", ""),
                            isAvailable = o.optBoolean("isAvailable", true),
                            stockQuantity = o.optInt("stockQuantity", 0),
                            unit = o.optString("unit", "pcs"),
                            lowStockThreshold = o.optInt("lowStockThreshold", 5)
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
                            note = o.optString("note", "")
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
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
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
                    usersList.add(
                        UserEntity(
                            id = o.optLong("id", 0L),
                            emailOrPhone = o.optString("emailOrPhone", ""),
                            name = o.optString("name", "Staff"),
                            role = o.optString("role", "Administrator"),
                            passwordHash = o.optString("passwordHash", "[PROTECTED]"),
                            isCurrentSession = false,
                            isActive = o.optBoolean("isActive", true)
                        )
                    )
                }
            }

            // Parse Receipt Setting
            var receiptSetting: ReceiptSettingEntity? = null
            if (dataObj.has("receiptSetting")) {
                val rs = dataObj.getJSONObject("receiptSetting")
                receiptSetting = ReceiptSettingEntity(
                    id = rs.optInt("id", 1),
                    shopName = rs.optString("shopName", ""),
                    phone = rs.optString("phone", ""),
                    address = rs.optString("address", ""),
                    email = rs.optString("email", ""),
                    website = rs.optString("website", ""),
                    logoUri = rs.optString("logoUri", ""),
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

            val recordSummary = "${menuItemsList.size} Menu Items, ${ordersList.size} Orders, ${expensesList.size} Expenses, ${categoriesList.size} Categories"

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
                // Clear orders, order items, stock logs, menu items, categories, expenses, offers, notifications
                database.orderDao().clearAllOrderItems()
                database.orderDao().clearAllOrders()
                database.stockLogDao().clearAllStockLogs()
                database.menuItemDao().clearAll()
                database.categoryDao().clearAll()
                database.expenseDao().clearAllExpenses()
                database.offerDao().clearAllOffers()
                database.notificationDao().clearAllNotifications()
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
                // Clear existing tables
                database.orderDao().clearAllOrderItems()
                database.orderDao().clearAllOrders()
                database.stockLogDao().clearAllStockLogs()
                database.menuItemDao().clearAll()
                database.categoryDao().clearAll()
                database.expenseDao().clearAllExpenses()
                database.offerDao().clearAllOffers()

                // Insert Categories
                if (parsedData.categories.isNotEmpty()) {
                    database.categoryDao().insertCategories(parsedData.categories)
                }

                // Insert Menu Items
                if (parsedData.menuItems.isNotEmpty()) {
                    database.menuItemDao().insertMenuItems(parsedData.menuItems)
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

                // Restore Receipt Setting
                parsedData.receiptSetting?.let { rs ->
                    database.receiptSettingDao().saveReceiptSetting(rs)
                }

                // Restore Printer Setting
                parsedData.printerSetting?.let { ps ->
                    database.printerSettingDao().savePrinterSetting(ps)
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
                            restoredU.copy(passwordHash = "123456") // default safe hash fallback if new staff
                        } else {
                            restoredU
                        }
                    }
                    database.userDao().insertUsers(finalUsersToRestore)
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
