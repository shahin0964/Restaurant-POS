package com.restaurant.pos.data.backupv2

import com.restaurant.pos.data.db.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes BackupPayloadV2 to/from JSON.
 */
object BackupSerializerV2 {

    fun serializePayload(payload: BackupPayloadV2): String {
        val root = JSONObject()

        // 1. Format Identifier & Metadata
        root.put("formatIdentifier", BackupMetadataV2.FORMAT_IDENTIFIER)
        root.put("formatVersion", payload.metadata.formatVersion)
        
        val metaObj = JSONObject()
        metaObj.put("formatVersion", payload.metadata.formatVersion)
        metaObj.put("appVersion", payload.metadata.appVersion)
        metaObj.put("dbVersion", payload.metadata.dbVersion)
        metaObj.put("timestamp", payload.metadata.timestamp)
        metaObj.put("createdAtFormatted", payload.metadata.createdAtFormatted)
        metaObj.put("deviceModel", payload.metadata.deviceModel)

        val countsObj = JSONObject()
        payload.metadata.recordCounts.forEach { (k, v) -> countsObj.put(k, v) }
        metaObj.put("recordCounts", countsObj)
        root.put("metadata", metaObj)

        // 2. Database Data (All 14 entities)
        val dbObj = JSONObject()
        dbObj.put("categories", JSONArray().apply { payload.databaseData.categories.forEach { put(categoryToJson(it)) } })
        dbObj.put("menuItems", JSONArray().apply { payload.databaseData.menuItems.forEach { put(menuItemToJson(it)) } })
        dbObj.put("orders", JSONArray().apply { payload.databaseData.orders.forEach { put(orderToJson(it)) } })
        dbObj.put("orderItems", JSONArray().apply { payload.databaseData.orderItems.forEach { put(orderItemToJson(it)) } })
        dbObj.put("users", JSONArray().apply { payload.databaseData.users.forEach { put(userToJson(it)) } })
        dbObj.put("tables", JSONArray().apply { payload.databaseData.tables.forEach { put(tableToJson(it)) } })
        dbObj.put("expenses", JSONArray().apply { payload.databaseData.expenses.forEach { put(expenseToJson(it)) } })
        dbObj.put("stockLogs", JSONArray().apply { payload.databaseData.stockLogs.forEach { put(stockLogToJson(it)) } })
        dbObj.put("offers", JSONArray().apply { payload.databaseData.offers.forEach { put(offerToJson(it)) } })
        dbObj.put("notifications", JSONArray().apply { payload.databaseData.notifications.forEach { put(notificationToJson(it)) } })
        dbObj.put("staffFoods", JSONArray().apply { payload.databaseData.staffFoods.forEach { put(staffFoodToJson(it)) } })
        dbObj.put("syncRecords", JSONArray().apply { payload.databaseData.syncRecords.forEach { put(syncRecordToJson(it)) } })

        if (payload.databaseData.printerSetting != null) {
            dbObj.put("printerSetting", printerSettingToJson(payload.databaseData.printerSetting))
        }
        if (payload.databaseData.receiptSetting != null) {
            dbObj.put("receiptSetting", receiptSettingToJson(payload.databaseData.receiptSetting))
        }
        root.put("databaseData", dbObj)

        // 3. Preferences Data
        val prefsObj = JSONObject()
        payload.preferencesData.forEach { (prefName, map) ->
            val pObj = JSONObject()
            map.forEach { (k, v) -> pObj.put(k, v ?: JSONObject.NULL) }
            prefsObj.put(prefName, pObj)
        }
        root.put("preferencesData", prefsObj)

        // 4. Local Assets
        val assetsArr = JSONArray()
        payload.assets.forEach { asset ->
            val aObj = JSONObject()
            aObj.put("relativePath", asset.relativePath)
            aObj.put("mimeType", asset.mimeType)
            aObj.put("base64Data", asset.base64Data)
            aObj.put("sizeBytes", asset.sizeBytes)
            assetsArr.put(aObj)
        }
        root.put("assets", assetsArr)

        return root.toString(2)
    }

    fun deserializePayload(jsonString: String): BackupPayloadV2 {
        val root = JSONObject(jsonString)

        // 1. Metadata
        val metaObj = root.optJSONObject("metadata") ?: throw IllegalArgumentException("Missing 'metadata' section in backup JSON.")
        val formatVersion = metaObj.optInt("formatVersion", root.optInt("formatVersion", 0))
        val appVersion = metaObj.optString("appVersion", "Unknown")
        val dbVersion = metaObj.optInt("dbVersion", 0)
        val timestamp = metaObj.optLong("timestamp", System.currentTimeMillis())
        val createdAtFormatted = metaObj.optString("createdAtFormatted", "")
        val deviceModel = metaObj.optString("deviceModel", "")

        val countsMap = mutableMapOf<String, Int>()
        val countsObj = metaObj.optJSONObject("recordCounts")
        countsObj?.keys()?.forEach { k ->
            countsMap[k] = countsObj.optInt(k, 0)
        }

        val metadata = BackupMetadataV2(
            formatVersion = formatVersion,
            appVersion = appVersion,
            dbVersion = dbVersion,
            timestamp = timestamp,
            createdAtFormatted = createdAtFormatted,
            deviceModel = deviceModel,
            recordCounts = countsMap
        )

        // 2. Database Data
        val dbObj = root.optJSONObject("databaseData") ?: JSONObject()
        
        val categories = parseArray(dbObj.optJSONArray("categories")) { jsonToCategory(it) }
        val menuItems = parseArray(dbObj.optJSONArray("menuItems")) { jsonToMenuItem(it) }
        val orders = parseArray(dbObj.optJSONArray("orders")) { jsonToOrder(it) }
        val orderItems = parseArray(dbObj.optJSONArray("orderItems")) { jsonToOrderItem(it) }
        val users = parseArray(dbObj.optJSONArray("users")) { jsonToUser(it) }
        val tables = parseArray(dbObj.optJSONArray("tables")) { jsonToTable(it) }
        val expenses = parseArray(dbObj.optJSONArray("expenses")) { jsonToExpense(it) }
        val stockLogs = parseArray(dbObj.optJSONArray("stockLogs")) { jsonToStockLog(it) }
        val offers = parseArray(dbObj.optJSONArray("offers")) { jsonToOffer(it) }
        val notifications = parseArray(dbObj.optJSONArray("notifications")) { jsonToNotification(it) }
        val staffFoods = parseArray(dbObj.optJSONArray("staffFoods")) { jsonToStaffFood(it) }
        val syncRecords = parseArray(dbObj.optJSONArray("syncRecords")) { jsonToSyncRecord(it) }

        val printerSettingObj = dbObj.optJSONObject("printerSetting")
        val printerSetting = if (printerSettingObj != null) jsonToPrinterSetting(printerSettingObj) else null

        val receiptSettingObj = dbObj.optJSONObject("receiptSetting")
        val receiptSetting = if (receiptSettingObj != null) jsonToReceiptSetting(receiptSettingObj) else null

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

        // 3. Preferences Data
        val prefsData = mutableMapOf<String, Map<String, Any?>>()
        val prefsObj = root.optJSONObject("preferencesData")
        prefsObj?.keys()?.forEach { prefName ->
            val pObj = prefsObj.optJSONObject(prefName)
            if (pObj != null) {
                val map = mutableMapOf<String, Any?>()
                pObj.keys().forEach { k ->
                    map[k] = if (pObj.isNull(k)) null else pObj.get(k)
                }
                prefsData[prefName] = map
            }
        }

        // 4. Local Assets
        val assetsList = mutableListOf<BackupAssetV2>()
        val assetsArr = root.optJSONArray("assets")
        if (assetsArr != null) {
            for (i in 0 until assetsArr.length()) {
                val aObj = assetsArr.getJSONObject(i)
                assetsList.add(
                    BackupAssetV2(
                        relativePath = aObj.optString("relativePath", ""),
                        mimeType = aObj.optString("mimeType", "application/octet-stream"),
                        base64Data = aObj.optString("base64Data", ""),
                        sizeBytes = aObj.optLong("sizeBytes", 0L)
                    )
                )
            }
        }

        return BackupPayloadV2(
            metadata = metadata,
            databaseData = databaseData,
            preferencesData = prefsData,
            assets = assetsList
        )
    }

    private fun <T> parseArray(arr: JSONArray?, transform: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        val list = mutableListOf<T>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(transform(obj))
        }
        return list
    }

    // --- CategoryEntity ---
    private fun categoryToJson(e: CategoryEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("itemCount", e.itemCount)
        put("iconName", e.iconName)
        put("imageUrl", e.imageUrl)
    }
    private fun jsonToCategory(o: JSONObject) = CategoryEntity(
        id = o.optLong("id", 0L),
        name = o.optString("name", ""),
        itemCount = o.optInt("itemCount", 0),
        iconName = o.optString("iconName", "burger"),
        imageUrl = o.optString("imageUrl", "")
    )

    // --- MenuItemEntity ---
    private fun menuItemToJson(e: MenuItemEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("categoryId", e.categoryId)
        put("categoryName", e.categoryName)
        put("price", e.price)
        put("description", e.description)
        put("imageUrl", e.imageUrl)
        put("isAvailable", e.isAvailable)
        put("stockQuantity", e.stockQuantity)
        put("unit", e.unit)
        put("lowStockThreshold", e.lowStockThreshold)
        put("costPrice", e.costPrice)
        put("discountEnabled", e.discountEnabled)
        put("discountValue", e.discountValue)
        put("discountType", e.discountType)
    }
    private fun jsonToMenuItem(o: JSONObject) = MenuItemEntity(
        id = o.optLong("id", 0L),
        name = o.optString("name", ""),
        categoryId = o.optLong("categoryId", 0L),
        categoryName = o.optString("categoryName", ""),
        price = o.optDouble("price", 0.0),
        description = o.optString("description", ""),
        imageUrl = o.optString("imageUrl", ""),
        isAvailable = o.optBoolean("isAvailable", true),
        stockQuantity = o.optInt("stockQuantity", 20),
        unit = o.optString("unit", "pcs"),
        lowStockThreshold = o.optInt("lowStockThreshold", 10),
        costPrice = o.optDouble("costPrice", 0.0),
        discountEnabled = o.optBoolean("discountEnabled", false),
        discountValue = o.optDouble("discountValue", 0.0),
        discountType = o.optString("discountType", "PERCENTAGE")
    )

    // --- OrderEntity ---
    private fun orderToJson(e: OrderEntity) = JSONObject().apply {
        put("id", e.id)
        put("orderNumber", e.orderNumber)
        put("orderType", e.orderType)
        put("tableNumber", e.tableNumber)
        put("customerName", e.customerName)
        put("note", e.note)
        put("subtotal", e.subtotal)
        put("discount", e.discount)
        put("tax", e.tax)
        put("total", e.total)
        put("paymentMethod", e.paymentMethod)
        put("isPaid", e.isPaid)
        put("status", e.status)
        put("timestamp", e.timestamp)
        if (e.tableId != null) put("tableId", e.tableId) else put("tableId", JSONObject.NULL)
    }
    private fun jsonToOrder(o: JSONObject) = OrderEntity(
        id = o.optLong("id", 0L),
        orderNumber = o.optString("orderNumber", ""),
        orderType = o.optString("orderType", "Dine In"),
        tableNumber = o.optString("tableNumber", ""),
        customerName = o.optString("customerName", ""),
        note = o.optString("note", ""),
        subtotal = o.optDouble("subtotal", 0.0),
        discount = o.optDouble("discount", 0.0),
        tax = o.optDouble("tax", 0.0),
        total = o.optDouble("total", 0.0),
        paymentMethod = o.optString("paymentMethod", "Cash"),
        isPaid = o.optBoolean("isPaid", true),
        status = o.optString("status", "Pending"),
        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        tableId = if (o.isNull("tableId")) null else o.optLong("tableId")
    )

    // --- OrderItemEntity ---
    private fun orderItemToJson(e: OrderItemEntity) = JSONObject().apply {
        put("id", e.id)
        put("orderId", e.orderId)
        put("menuItemId", e.menuItemId)
        put("menuItemName", e.menuItemName)
        put("quantity", e.quantity)
        put("pricePerUnit", e.pricePerUnit)
        put("note", e.note)
        put("costPriceAtSale", e.costPriceAtSale)
    }
    private fun jsonToOrderItem(o: JSONObject) = OrderItemEntity(
        id = o.optLong("id", 0L),
        orderId = o.optLong("orderId", 0L),
        menuItemId = o.optLong("menuItemId", 0L),
        menuItemName = o.optString("menuItemName", ""),
        quantity = o.optInt("quantity", 1),
        pricePerUnit = o.optDouble("pricePerUnit", 0.0),
        note = o.optString("note", ""),
        costPriceAtSale = o.optDouble("costPriceAtSale", 0.0)
    )

    // --- UserEntity ---
    private fun userToJson(e: UserEntity) = JSONObject().apply {
        put("id", e.id)
        put("emailOrPhone", e.emailOrPhone)
        put("name", e.name)
        put("role", e.role)
        put("passwordHash", e.passwordHash)
        if (e.firebaseUid != null) put("firebaseUid", e.firebaseUid) else put("firebaseUid", JSONObject.NULL)
        put("isCurrentSession", e.isCurrentSession)
        put("isActive", e.isActive)
        put("permissions", e.permissions)
    }
    private fun jsonToUser(o: JSONObject) = UserEntity(
        id = o.optLong("id", 0L),
        emailOrPhone = o.optString("emailOrPhone", ""),
        name = o.optString("name", ""),
        role = o.optString("role", "Administrator"),
        passwordHash = o.optString("passwordHash", ""),
        firebaseUid = if (o.isNull("firebaseUid")) null else o.optString("firebaseUid"),
        isCurrentSession = o.optBoolean("isCurrentSession", false),
        isActive = o.optBoolean("isActive", true),
        permissions = o.optString("permissions", "")
    )

    // --- PrinterSettingEntity ---
    private fun printerSettingToJson(e: PrinterSettingEntity) = JSONObject().apply {
        put("id", e.id)
        put("connectionType", e.connectionType)
        put("printerName", e.printerName)
        put("macAddress", e.macAddress)
        put("ipAddress", e.ipAddress)
        put("port", e.port)
        put("paperSize", e.paperSize)
        put("autoPrintOnOrder", e.autoPrintOnOrder)
        put("isConnected", e.isConnected)
        put("printerType", e.printerType)
        put("bluetoothAddress", e.bluetoothAddress)
    }
    private fun jsonToPrinterSetting(o: JSONObject) = PrinterSettingEntity(
        id = o.optInt("id", 1),
        connectionType = o.optString("connectionType", "BUILT_IN"),
        printerName = o.optString("printerName", ""),
        macAddress = o.optString("macAddress", ""),
        ipAddress = o.optString("ipAddress", "192.168.1.100"),
        port = o.optInt("port", 9100),
        paperSize = o.optString("paperSize", "58mm"),
        autoPrintOnOrder = o.optBoolean("autoPrintOnOrder", true),
        isConnected = o.optBoolean("isConnected", false),
        printerType = o.optString("printerType", "Sunmi InnerPrinter"),
        bluetoothAddress = o.optString("bluetoothAddress", "")
    )

    // --- ExpenseEntity ---
    private fun expenseToJson(e: ExpenseEntity) = JSONObject().apply {
        put("id", e.id)
        put("title", e.title)
        put("amount", e.amount)
        put("category", e.category)
        put("note", e.note)
        put("timestamp", e.timestamp)
        put("paymentMethod", e.paymentMethod)
        put("expenseType", e.expenseType)
    }
    private fun jsonToExpense(o: JSONObject) = ExpenseEntity(
        id = o.optLong("id", 0L),
        title = o.optString("title", ""),
        amount = o.optDouble("amount", 0.0),
        category = o.optString("category", "General"),
        note = o.optString("note", ""),
        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        paymentMethod = o.optString("paymentMethod", "Cash"),
        expenseType = o.optString("expenseType", "OPERATING")
    )

    // --- StockLogEntity ---
    private fun stockLogToJson(e: StockLogEntity) = JSONObject().apply {
        put("id", e.id)
        put("menuItemId", e.menuItemId)
        put("menuItemName", e.menuItemName)
        put("changeAmount", e.changeAmount)
        put("type", e.type)
        put("note", e.note)
        put("timestamp", e.timestamp)
    }
    private fun jsonToStockLog(o: JSONObject) = StockLogEntity(
        id = o.optLong("id", 0L),
        menuItemId = o.optLong("menuItemId", 0L),
        menuItemName = o.optString("menuItemName", ""),
        changeAmount = o.optInt("changeAmount", 0),
        type = o.optString("type", ""),
        note = o.optString("note", ""),
        timestamp = o.optLong("timestamp", System.currentTimeMillis())
    )

    // --- OfferEntity ---
    private fun offerToJson(e: OfferEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("discountType", e.discountType)
        put("discountValue", e.discountValue)
        put("startDate", e.startDate)
        put("endDate", e.endDate)
        put("minOrderAmount", e.minOrderAmount)
        put("maxDiscountAmount", e.maxDiscountAmount)
        put("isActive", e.isActive)
    }
    private fun jsonToOffer(o: JSONObject) = OfferEntity(
        id = o.optLong("id", 0L),
        name = o.optString("name", ""),
        discountType = o.optString("discountType", "PERCENTAGE"),
        discountValue = o.optDouble("discountValue", 0.0),
        startDate = o.optLong("startDate", 0L),
        endDate = o.optLong("endDate", 0L),
        minOrderAmount = o.optDouble("minOrderAmount", 0.0),
        maxDiscountAmount = o.optDouble("maxDiscountAmount", 0.0),
        isActive = o.optBoolean("isActive", true)
    )

    // --- ReceiptSettingEntity ---
    private fun receiptSettingToJson(e: ReceiptSettingEntity) = JSONObject().apply {
        put("id", e.id)
        put("shopName", e.shopName)
        put("phone", e.phone)
        put("address", e.address)
        put("email", e.email)
        put("website", e.website)
        put("logoUri", e.logoUri)
        put("footerText", e.footerText)
        put("currencySymbol", e.currencySymbol)
        put("currencyCode", e.currencyCode)
        put("isTaxEnabled", e.isTaxEnabled)
        put("taxRate", e.taxRate)
        put("showShopName", e.showShopName)
        put("showLogo", e.showLogo)
        put("showPhone", e.showPhone)
        put("showAddress", e.showAddress)
        put("showOrderNumber", e.showOrderNumber)
        put("showDateTime", e.showDateTime)
        put("showCustomerName", e.showCustomerName)
        put("showOrderType", e.showOrderType)
        put("showItems", e.showItems)
        put("showQuantity", e.showQuantity)
        put("showItemPrice", e.showItemPrice)
        put("showSubtotal", e.showSubtotal)
        put("showDiscount", e.showDiscount)
        put("showTax", e.showTax)
        put("showTotal", e.showTotal)
        put("showPaymentStatus", e.showPaymentStatus)
        put("showFooter", e.showFooter)
    }
    private fun jsonToReceiptSetting(o: JSONObject) = ReceiptSettingEntity(
        id = o.optInt("id", 1),
        shopName = o.optString("shopName", ""),
        phone = o.optString("phone", ""),
        address = o.optString("address", ""),
        email = o.optString("email", ""),
        website = o.optString("website", ""),
        logoUri = o.optString("logoUri", ""),
        footerText = o.optString("footerText", ""),
        currencySymbol = o.optString("currencySymbol", "৳"),
        currencyCode = o.optString("currencyCode", "BDT"),
        isTaxEnabled = o.optBoolean("isTaxEnabled", false),
        taxRate = o.optDouble("taxRate", 0.0),
        showShopName = o.optBoolean("showShopName", true),
        showLogo = o.optBoolean("showLogo", true),
        showPhone = o.optBoolean("showPhone", true),
        showAddress = o.optBoolean("showAddress", true),
        showOrderNumber = o.optBoolean("showOrderNumber", true),
        showDateTime = o.optBoolean("showDateTime", true),
        showCustomerName = o.optBoolean("showCustomerName", true),
        showOrderType = o.optBoolean("showOrderType", true),
        showItems = o.optBoolean("showItems", true),
        showQuantity = o.optBoolean("showQuantity", true),
        showItemPrice = o.optBoolean("showItemPrice", true),
        showSubtotal = o.optBoolean("showSubtotal", true),
        showDiscount = o.optBoolean("showDiscount", true),
        showTax = o.optBoolean("showTax", true),
        showTotal = o.optBoolean("showTotal", true),
        showPaymentStatus = o.optBoolean("showPaymentStatus", true),
        showFooter = o.optBoolean("showFooter", true)
    )

    // --- NotificationEntity ---
    private fun notificationToJson(e: NotificationEntity) = JSONObject().apply {
        put("id", e.id)
        put("type", e.type)
        put("title", e.title)
        put("message", e.message)
        if (e.targetId != null) put("targetId", e.targetId) else put("targetId", JSONObject.NULL)
        put("timestamp", e.timestamp)
        put("isRead", e.isRead)
    }
    private fun jsonToNotification(o: JSONObject) = NotificationEntity(
        id = o.optLong("id", 0L),
        type = o.optString("type", ""),
        title = o.optString("title", ""),
        message = o.optString("message", ""),
        targetId = if (o.isNull("targetId")) null else o.optString("targetId"),
        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
        isRead = o.optBoolean("isRead", false)
    )

    // --- TableEntity ---
    private fun tableToJson(e: TableEntity) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("capacity", e.capacity)
        put("isActive", e.isActive)
        put("accountId", e.accountId)
    }
    private fun jsonToTable(o: JSONObject) = TableEntity(
        id = o.optLong("id", 0L),
        name = o.optString("name", ""),
        capacity = o.optInt("capacity", 4),
        isActive = o.optBoolean("isActive", true),
        accountId = o.optString("accountId", "")
    )

    // --- SyncRecordEntity ---
    private fun syncRecordToJson(e: SyncRecordEntity) = JSONObject().apply {
        put("id", e.id)
        put("tableName", e.tableName)
        put("localId", e.localId)
        put("firestoreId", e.firestoreId)
        put("lastSyncTime", e.lastSyncTime)
        put("pendingSync", e.pendingSync)
        put("operation", e.operation)
        put("isDeleted", e.isDeleted)
    }
    private fun jsonToSyncRecord(o: JSONObject) = SyncRecordEntity(
        id = o.optLong("id", 0L),
        tableName = o.optString("tableName", ""),
        localId = o.optLong("localId", 0L),
        firestoreId = o.optString("firestoreId", ""),
        lastSyncTime = o.optLong("lastSyncTime", 0L),
        pendingSync = o.optBoolean("pendingSync", true),
        operation = o.optString("operation", "INSERT"),
        isDeleted = o.optBoolean("isDeleted", false)
    )

    // --- StaffFoodEntity ---
    private fun staffFoodToJson(e: StaffFoodEntity) = JSONObject().apply {
        put("id", e.id)
        put("staffName", e.staffName)
        put("productName", e.productName)
        put("quantity", e.quantity)
        put("unitPrice", e.unitPrice)
        put("totalPrice", e.totalPrice)
        put("timestamp", e.timestamp)
    }
    private fun jsonToStaffFood(o: JSONObject) = StaffFoodEntity(
        id = o.optLong("id", 0L),
        staffName = o.optString("staffName", ""),
        productName = o.optString("productName", ""),
        quantity = o.optInt("quantity", 1),
        unitPrice = o.optDouble("unitPrice", 0.0),
        totalPrice = o.optDouble("totalPrice", 0.0),
        timestamp = o.optLong("timestamp", System.currentTimeMillis())
    )
}
