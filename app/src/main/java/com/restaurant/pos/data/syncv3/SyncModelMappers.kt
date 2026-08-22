package com.restaurant.pos.data.syncv3

import com.restaurant.pos.data.db.*
import java.util.UUID

/**
 * Mapper utilities for bidirectionally converting between Room database entities and
 * Firebase Realtime Database sync models, resolving local integer IDs to/from cross-device UUID syncIds.
 */
object SyncModelMappers {

    // Helper to fetch or generate cloud syncId for a local ID
    suspend fun resolveOrCreateSyncId(
        syncRecordDao: SyncRecordDao,
        tableName: String,
        localId: Long
    ): String {
        if (localId <= 0) return ""
        val existing = syncRecordDao.getRecordByLocalId(tableName, localId)
        if (existing != null) {
            return existing.firestoreId
        }
        // Generate new UUID mapping for unsynced local record
        val newSyncId = UUID.randomUUID().toString()
        val record = SyncRecordEntity(
            tableName = tableName,
            localId = localId,
            firestoreId = newSyncId,
            lastSyncTime = 0L,
            pendingSync = true,
            operation = "INSERT"
        )
        syncRecordDao.insertOrUpdate(record)
        return newSyncId
    }

    // Helper to find local ID for a remote cloud syncId
    suspend fun resolveLocalId(
        syncRecordDao: SyncRecordDao,
        tableName: String,
        syncId: String
    ): Long {
        if (syncId.isBlank()) return 0L
        return syncRecordDao.getRecordByFirestoreId(tableName, syncId)?.localId ?: 0L
    }

    // 1. CATEGORIES MAPPINGS
    suspend fun toSyncModel(
        entity: CategoryEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): CategorySyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "categories", entity.id)
        return CategorySyncModel(
            syncId = syncId,
            name = entity.name,
            itemCount = entity.itemCount,
            iconName = entity.iconName,
            imageUrl = entity.imageUrl,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: CategorySyncModel, localId: Long): CategoryEntity {
        return CategoryEntity(
            id = localId,
            name = syncModel.name,
            itemCount = syncModel.itemCount,
            iconName = syncModel.iconName,
            imageUrl = syncModel.imageUrl
        )
    }

    // 2. MENU ITEMS MAPPINGS
    suspend fun toSyncModel(
        entity: MenuItemEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): MenuItemSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "menu_items", entity.id)
        val categorySyncId = resolveOrCreateSyncId(syncRecordDao, "categories", entity.categoryId)
        return MenuItemSyncModel(
            syncId = syncId,
            name = entity.name,
            categorySyncId = categorySyncId,
            categoryName = entity.categoryName,
            price = entity.price,
            description = entity.description,
            imageUrl = entity.imageUrl,
            isAvailable = entity.isAvailable,
            stockQuantity = entity.stockQuantity,
            unit = entity.unit,
            lowStockThreshold = entity.lowStockThreshold,
            costPrice = entity.costPrice,
            discountEnabled = entity.discountEnabled,
            discountValue = entity.discountValue,
            discountType = entity.discountType,
            version = version,
            isDeleted = isDeleted
        )
    }

    suspend fun toEntity(
        syncModel: MenuItemSyncModel,
        localId: Long,
        syncRecordDao: SyncRecordDao
    ): MenuItemEntity {
        val localCategoryId = resolveLocalId(syncRecordDao, "categories", syncModel.categorySyncId)
        return MenuItemEntity(
            id = localId,
            name = syncModel.name,
            categoryId = localCategoryId,
            categoryName = syncModel.categoryName,
            price = syncModel.price,
            description = syncModel.description,
            imageUrl = syncModel.imageUrl,
            isAvailable = syncModel.isAvailable,
            stockQuantity = syncModel.stockQuantity,
            unit = syncModel.unit,
            lowStockThreshold = syncModel.lowStockThreshold,
            costPrice = syncModel.costPrice,
            discountEnabled = syncModel.discountEnabled,
            discountValue = syncModel.discountValue,
            discountType = syncModel.discountType
        )
    }

    // 3. ORDERS MAPPINGS
    suspend fun toSyncModel(
        entity: OrderEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): OrderSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "orders", entity.id)
        val tableSyncId = entity.tableId?.let { resolveOrCreateSyncId(syncRecordDao, "tables", it) } ?: ""
        return OrderSyncModel(
            syncId = syncId,
            orderNumber = entity.orderNumber,
            orderType = entity.orderType,
            tableNumber = entity.tableNumber,
            customerName = entity.customerName,
            note = entity.note,
            subtotal = entity.subtotal,
            discount = entity.discount,
            tax = entity.tax,
            total = entity.total,
            paymentMethod = entity.paymentMethod,
            isPaid = entity.isPaid,
            status = entity.status,
            timestamp = entity.timestamp,
            tableSyncId = tableSyncId,
            version = version,
            isDeleted = isDeleted
        )
    }

    suspend fun toEntity(
        syncModel: OrderSyncModel,
        localId: Long,
        syncRecordDao: SyncRecordDao
    ): OrderEntity {
        val localTableId = if (syncModel.tableSyncId.isNotBlank()) {
            resolveLocalId(syncRecordDao, "tables", syncModel.tableSyncId).let { if (it <= 0) null else it }
        } else {
            null
        }
        return OrderEntity(
            id = localId,
            orderNumber = syncModel.orderNumber,
            orderType = syncModel.orderType,
            tableNumber = syncModel.tableNumber,
            customerName = syncModel.customerName,
            note = syncModel.note,
            subtotal = syncModel.subtotal,
            discount = syncModel.discount,
            tax = syncModel.tax,
            total = syncModel.total,
            paymentMethod = syncModel.paymentMethod,
            isPaid = syncModel.isPaid,
            status = syncModel.status,
            timestamp = syncModel.timestamp,
            tableId = localTableId
        )
    }

    // 4. ORDER ITEMS MAPPINGS
    suspend fun toSyncModel(
        entity: OrderItemEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): OrderItemSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "order_items", entity.id)
        val orderSyncId = resolveOrCreateSyncId(syncRecordDao, "orders", entity.orderId)
        val menuItemSyncId = resolveOrCreateSyncId(syncRecordDao, "menu_items", entity.menuItemId)
        return OrderItemSyncModel(
            syncId = syncId,
            orderSyncId = orderSyncId,
            menuItemSyncId = menuItemSyncId,
            menuItemName = entity.menuItemName,
            quantity = entity.quantity,
            pricePerUnit = entity.pricePerUnit,
            note = entity.note,
            costPriceAtSale = entity.costPriceAtSale,
            version = version,
            isDeleted = isDeleted
        )
    }

    suspend fun toEntity(
        syncModel: OrderItemSyncModel,
        localId: Long,
        syncRecordDao: SyncRecordDao
    ): OrderItemEntity {
        val localOrderId = resolveLocalId(syncRecordDao, "orders", syncModel.orderSyncId)
        val localMenuItemId = resolveLocalId(syncRecordDao, "menu_items", syncModel.menuItemSyncId)
        return OrderItemEntity(
            id = localId,
            orderId = localOrderId,
            menuItemId = localMenuItemId,
            menuItemName = syncModel.menuItemName,
            quantity = syncModel.quantity,
            pricePerUnit = syncModel.pricePerUnit,
            note = syncModel.note,
            costPriceAtSale = syncModel.costPriceAtSale
        )
    }

    // 5. USERS MAPPINGS
    suspend fun toSyncModel(
        entity: UserEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): UserSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "users", entity.id)
        return UserSyncModel(
            syncId = syncId,
            emailOrPhone = entity.emailOrPhone,
            name = entity.name,
            role = entity.role,
            passwordHash = entity.passwordHash,
            firebaseUid = entity.firebaseUid ?: "",
            isActive = entity.isActive,
            permissions = entity.permissions,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: UserSyncModel, localId: Long): UserEntity {
        val currentFirebaseUid = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }

        val isSessionMatch = !currentFirebaseUid.isNullOrBlank() && 
            (syncModel.firebaseUid == currentFirebaseUid || syncModel.syncId == currentFirebaseUid)

        val isRoleAdmin = syncModel.role.equals("Administrator", ignoreCase = true) || 
                          syncModel.role.equals("Admin", ignoreCase = true)

        val resolvedRole = if (isSessionMatch || isRoleAdmin) {
            "Administrator"
        } else {
            syncModel.role.ifBlank { "Staff" }
        }

        val resolvedPermissions = if (resolvedRole.equals("Administrator", ignoreCase = true)) {
            com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
        } else {
            syncModel.permissions
        }

        return UserEntity(
            id = localId,
            emailOrPhone = syncModel.emailOrPhone,
            name = syncModel.name,
            role = resolvedRole,
            passwordHash = syncModel.passwordHash,
            firebaseUid = syncModel.firebaseUid.ifBlank { null },
            isCurrentSession = isSessionMatch,
            isActive = syncModel.isActive,
            permissions = resolvedPermissions
        )
    }

    // 6. PRINTER SETTINGS MAPPINGS
    suspend fun toSyncModel(
        entity: PrinterSettingEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): PrinterSettingSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "printer_settings", entity.id.toLong())
        return PrinterSettingSyncModel(
            syncId = syncId,
            connectionType = entity.connectionType,
            printerName = entity.printerName,
            macAddress = entity.macAddress,
            ipAddress = entity.ipAddress,
            port = entity.port,
            paperSize = entity.paperSize,
            autoPrintOnOrder = entity.autoPrintOnOrder,
            isConnected = entity.isConnected,
            printerType = entity.printerType,
            bluetoothAddress = entity.bluetoothAddress,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: PrinterSettingSyncModel, localId: Int): PrinterSettingEntity {
        return PrinterSettingEntity(
            id = localId,
            connectionType = syncModel.connectionType,
            printerName = syncModel.printerName,
            macAddress = syncModel.macAddress,
            ipAddress = syncModel.ipAddress,
            port = syncModel.port,
            paperSize = syncModel.paperSize,
            autoPrintOnOrder = syncModel.autoPrintOnOrder,
            isConnected = syncModel.isConnected,
            printerType = syncModel.printerType,
            bluetoothAddress = syncModel.bluetoothAddress
        )
    }

    // 7. EXPENSES MAPPINGS
    suspend fun toSyncModel(
        entity: ExpenseEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): ExpenseSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "expenses", entity.id)
        return ExpenseSyncModel(
            syncId = syncId,
            title = entity.title,
            amount = entity.amount,
            category = entity.category,
            note = entity.note,
            timestamp = entity.timestamp,
            paymentMethod = entity.paymentMethod,
            expenseType = entity.expenseType,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: ExpenseSyncModel, localId: Long): ExpenseEntity {
        return ExpenseEntity(
            id = localId,
            title = syncModel.title,
            amount = syncModel.amount,
            category = syncModel.category,
            note = syncModel.note,
            timestamp = syncModel.timestamp,
            paymentMethod = syncModel.paymentMethod,
            expenseType = syncModel.expenseType
        )
    }

    // 8. STOCK LOGS MAPPINGS
    suspend fun toSyncModel(
        entity: StockLogEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): StockLogSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "stock_logs", entity.id)
        val menuItemSyncId = resolveOrCreateSyncId(syncRecordDao, "menu_items", entity.menuItemId)
        return StockLogSyncModel(
            syncId = syncId,
            menuItemSyncId = menuItemSyncId,
            menuItemName = entity.menuItemName,
            changeAmount = entity.changeAmount,
            type = entity.type,
            note = entity.note,
            timestamp = entity.timestamp,
            version = version,
            isDeleted = isDeleted
        )
    }

    suspend fun toEntity(
        syncModel: StockLogSyncModel,
        localId: Long,
        syncRecordDao: SyncRecordDao
    ): StockLogEntity {
        val localMenuItemId = resolveLocalId(syncRecordDao, "menu_items", syncModel.menuItemSyncId)
        return StockLogEntity(
            id = localId,
            menuItemId = localMenuItemId,
            menuItemName = syncModel.menuItemName,
            changeAmount = syncModel.changeAmount,
            type = syncModel.type,
            note = syncModel.note,
            timestamp = syncModel.timestamp
        )
    }

    // 9. OFFERS MAPPINGS
    suspend fun toSyncModel(
        entity: OfferEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): OfferSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "offers", entity.id)
        return OfferSyncModel(
            syncId = syncId,
            name = entity.name,
            discountType = entity.discountType,
            discountValue = entity.discountValue,
            startDate = entity.startDate,
            endDate = entity.endDate,
            minOrderAmount = entity.minOrderAmount,
            maxDiscountAmount = entity.maxDiscountAmount,
            isActive = entity.isActive,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: OfferSyncModel, localId: Long): OfferEntity {
        return OfferEntity(
            id = localId,
            name = syncModel.name,
            discountType = syncModel.discountType,
            discountValue = syncModel.discountValue,
            startDate = syncModel.startDate,
            endDate = syncModel.endDate,
            minOrderAmount = syncModel.minOrderAmount,
            maxDiscountAmount = syncModel.maxDiscountAmount,
            isActive = syncModel.isActive
        )
    }

    // 10. NOTIFICATIONS MAPPINGS
    suspend fun toSyncModel(
        entity: NotificationEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): NotificationSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "notifications", entity.id)
        // Check if targetId is an integer and could represent an item, otherwise use as string
        val targetSyncId = if (entity.targetId != null) {
            val potentialId = entity.targetId.toLongOrNull()
            if (potentialId != null) {
                // Check in menu_items or fallback
                val resolvedId = resolveOrCreateSyncId(syncRecordDao, "menu_items", potentialId)
                resolvedId.ifBlank { entity.targetId }
            } else {
                entity.targetId
            }
        } else {
            ""
        }
        return NotificationSyncModel(
            syncId = syncId,
            type = entity.type,
            title = entity.title,
            message = entity.message,
            targetSyncId = targetSyncId,
            timestamp = entity.timestamp,
            isRead = entity.isRead,
            version = version,
            isDeleted = isDeleted
        )
    }

    suspend fun toEntity(
        syncModel: NotificationSyncModel,
        localId: Long,
        syncRecordDao: SyncRecordDao
    ): NotificationEntity {
        // Map back targetSyncId to localId string representation if it resolves
        val localTargetId = if (syncModel.targetSyncId.isNotBlank()) {
            val resolvedLocal = resolveLocalId(syncRecordDao, "menu_items", syncModel.targetSyncId)
            if (resolvedLocal > 0) resolvedLocal.toString() else syncModel.targetSyncId
        } else {
            null
        }
        return NotificationEntity(
            id = localId,
            type = syncModel.type,
            title = syncModel.title,
            message = syncModel.message,
            targetId = localTargetId,
            timestamp = syncModel.timestamp,
            isRead = syncModel.isRead
        )
    }

    // 11. TABLES MAPPINGS
    suspend fun toSyncModel(
        entity: TableEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): TableSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "tables", entity.id)
        return TableSyncModel(
            syncId = syncId,
            name = entity.name,
            capacity = entity.capacity,
            isActive = entity.isActive,
            accountId = entity.accountId,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: TableSyncModel, localId: Long): TableEntity {
        return TableEntity(
            id = localId,
            name = syncModel.name,
            capacity = syncModel.capacity,
            isActive = syncModel.isActive,
            accountId = syncModel.accountId
        )
    }

    // 12. STAFF FOOD MAPPINGS
    suspend fun toSyncModel(
        entity: StaffFoodEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): StaffFoodSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "staff_food", entity.id)
        return StaffFoodSyncModel(
            syncId = syncId,
            staffName = entity.staffName,
            productName = entity.productName,
            quantity = entity.quantity,
            unitPrice = entity.unitPrice,
            totalPrice = entity.totalPrice,
            timestamp = entity.timestamp,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: StaffFoodSyncModel, localId: Long): StaffFoodEntity {
        return StaffFoodEntity(
            id = localId,
            staffName = syncModel.staffName,
            productName = syncModel.productName,
            quantity = syncModel.quantity,
            unitPrice = syncModel.unitPrice,
            totalPrice = syncModel.totalPrice,
            timestamp = syncModel.timestamp
        )
    }

    // 13. RECEIPT SETTINGS MAPPINGS
    suspend fun toSyncModel(
        entity: ReceiptSettingEntity,
        syncRecordDao: SyncRecordDao,
        version: Long = 1L,
        isDeleted: Boolean = false
    ): ReceiptSettingSyncModel {
        val syncId = resolveOrCreateSyncId(syncRecordDao, "receipt_settings", entity.id.toLong())
        return ReceiptSettingSyncModel(
            syncId = syncId,
            shopName = entity.shopName,
            phone = entity.phone,
            address = entity.address,
            email = entity.email,
            website = entity.website,
            logoUri = entity.logoUri,
            footerText = entity.footerText,
            currencySymbol = entity.currencySymbol,
            currencyCode = entity.currencyCode,
            isTaxEnabled = entity.isTaxEnabled,
            taxRate = entity.taxRate,
            showShopName = entity.showShopName,
            showLogo = entity.showLogo,
            showPhone = entity.showPhone,
            showAddress = entity.showAddress,
            showOrderNumber = entity.showOrderNumber,
            showDateTime = entity.showDateTime,
            showCustomerName = entity.showCustomerName,
            showOrderType = entity.showOrderType,
            showItems = entity.showItems,
            showQuantity = entity.showQuantity,
            showItemPrice = entity.showItemPrice,
            showSubtotal = entity.showSubtotal,
            showDiscount = entity.showDiscount,
            showTax = entity.showTax,
            showTotal = entity.showTotal,
            showPaymentStatus = entity.showPaymentStatus,
            showFooter = entity.showFooter,
            version = version,
            isDeleted = isDeleted
        )
    }

    fun toEntity(syncModel: ReceiptSettingSyncModel, localId: Int): ReceiptSettingEntity {
        return ReceiptSettingEntity(
            id = localId,
            shopName = syncModel.shopName,
            phone = syncModel.phone,
            address = syncModel.address,
            email = syncModel.email,
            website = syncModel.website,
            logoUri = syncModel.logoUri,
            footerText = syncModel.footerText,
            currencySymbol = syncModel.currencySymbol,
            currencyCode = syncModel.currencyCode,
            isTaxEnabled = syncModel.isTaxEnabled,
            taxRate = syncModel.taxRate,
            showShopName = syncModel.showShopName,
            showLogo = syncModel.showLogo,
            showPhone = syncModel.showPhone,
            showAddress = syncModel.showAddress,
            showOrderNumber = syncModel.showOrderNumber,
            showDateTime = syncModel.showDateTime,
            showCustomerName = syncModel.showCustomerName,
            showOrderType = syncModel.showOrderType,
            showItems = syncModel.showItems,
            showQuantity = syncModel.showQuantity,
            showItemPrice = syncModel.showItemPrice,
            showSubtotal = syncModel.showSubtotal,
            showDiscount = syncModel.showDiscount,
            showTax = syncModel.showTax,
            showTotal = syncModel.showTotal,
            showPaymentStatus = syncModel.showPaymentStatus,
            showFooter = syncModel.showFooter
        )
    }
}
