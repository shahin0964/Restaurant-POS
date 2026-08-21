package com.restaurant.pos.data.syncv3

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.restaurant.pos.data.db.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Repository to interface with Firebase Realtime Database for Step 5.
 * It provides operations to upload individual/batches of pending records,
 * perform dynamic conflict safety checks, maintain tombstones, and download
 * cloud data in a type-safe manner.
 */
interface FirebaseDatabaseProxy {
    suspend fun getRecord(path: String): Map<String, Any?>?
    suspend fun getTableRecords(path: String): List<Map<String, Any?>>
    suspend fun setRecord(path: String, data: Map<String, Any?>)
}

class RealtimeFirebaseProxy : FirebaseDatabaseProxy {
    override suspend fun getRecord(path: String): Map<String, Any?>? = suspendCancellableCoroutine { continuation ->
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference(path)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (snapshot.exists()) {
                        continuation.resume(snapshot.value as? Map<String, Any?>)
                    } else {
                        continuation.resume(null)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
    }

    override suspend fun getTableRecords(path: String): List<Map<String, Any?>> = suspendCancellableCoroutine { continuation ->
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference(path)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val results = mutableListOf<Map<String, Any?>>()
                    if (snapshot.exists()) {
                        for (child in snapshot.children) {
                            val map = child.value as? Map<String, Any?>
                            if (map != null) {
                                results.add(map)
                            }
                        }
                    }
                    continuation.resume(results)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
    }

    override suspend fun setRecord(path: String, data: Map<String, Any?>): Unit = suspendCancellableCoroutine { continuation ->
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference(path).setValue(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(task.exception ?: RuntimeException("Firebase write failed"))
                }
            }
    }
}

/**
 * Repository to interface with Firebase Realtime Database for Step 5.
 * It provides operations to upload individual/batches of pending records,
 * perform dynamic conflict safety checks, maintain tombstones, and download
 * cloud data in a type-safe manner.
 */
class RealtimeSyncRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val firebaseProxy: FirebaseDatabaseProxy = RealtimeFirebaseProxy(),
    private val getUid: () -> String = {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("Database operations are prohibited: User is not authenticated.")
        }
        uid
    }
) {
    private val TAG = "RealtimeSyncRepository"
    private val syncRecordDao = database.syncRecordDao()

    /**
     * Checks if Firebase authentication session is active. Returns the UID.
     * Throws IllegalStateException if not authenticated, keeping operations isolated.
     */
    fun getAuthenticatedUid(): String {
        return getUid()
    }

    /**
     * Main entry point to upload a single local record with strict conflict safety and versioning.
     */
    suspend fun uploadRecord(tableName: String, localId: Long): Boolean {
        val uid = getAuthenticatedUid()

        // 1. Fetch local sync record
        val syncRecord = syncRecordDao.getRecordByLocalId(tableName, localId)
            ?: return false // If no sync record exists, cannot sync.

        val syncId = syncRecord.firestoreId

        // 2. Conflict resolution: Fetch current cloud version
        val path = "accounts/$uid/$tableName/$syncId"
        val cloudMap = firebaseProxy.getRecord(path)
        var cloudVersion = 0L
        var cloudLastChanged = 0L

        if (cloudMap != null) {
            cloudVersion = (cloudMap["version"] as? Number)?.toLong() ?: 0L
            cloudLastChanged = (cloudMap["lastChanged"] as? Number)?.toLong() ?: 0L
        }

        // 3. Determine localVersion
        // If first upload: version 1. Else cloudVersion + 1.
        val proposedVersion = if (cloudVersion == 0L) 1L else cloudVersion + 1L

        // Check if cloud record is actually newer (conflict)
        if (cloudMap != null && syncRecord.lastSyncTime > 0L) {
            if (cloudLastChanged > syncRecord.lastSyncTime) {
                // Cloud was modified after our last sync! We should not blindly overwrite.
                println("[$TAG] Conflict detected for $tableName/$syncId. Cloud is newer. Upload aborted.")
                return false
            }
        }

        // 4. Resolve and serialize with proposed version
        val uploadMap = if (syncRecord.isDeleted) {
            mapOf(
                "syncId" to syncId,
                "version" to proposedVersion,
                "isDeleted" to true,
                "lastChanged" to ServerValue.TIMESTAMP
            )
        } else {
            val localModelMap = resolveAndMapLocalEntity(tableName, localId, false)
                ?: return false // If local record cannot be found or mapped, stop.
            injectVersionAndDeletedState(tableName, localModelMap, proposedVersion, false)
        }

        // 5. Write to RTDB under /accounts/{uid}/{tableName}/{syncId}
        try {
            firebaseProxy.setRecord(path, uploadMap)
            // 6. Update local sync record upon successful write
            syncRecordDao.markSynced(syncRecord.id, syncId, System.currentTimeMillis())
            LocalVersionTracker.setLocalVersion(context, tableName, syncId, proposedVersion)
            println("[$TAG] Uploaded $tableName/$syncId successfully with version $proposedVersion.")
            return true
        } catch (e: Exception) {
            println("[$TAG] Failed to upload $tableName/$syncId: ${e.message}")
            throw e
        }
    }

    /**
     * Processes the pending queue in relationships order.
     */
    suspend fun uploadPendingQueue(): Int {
        val queueManager = SyncQueueManager(syncRecordDao)
        val pendingRecords = queueManager.getOrderedPendingQueue()
        if (pendingRecords.isEmpty()) return 0

        var successCount = 0
        for (record in pendingRecords) {
            try {
                val success = uploadRecord(record.tableName, record.localId)
                if (success) {
                    successCount++
                } else {
                    queueManager.markAsFailed(record.id, "Conflict or unable to map record.")
                }
            } catch (e: Exception) {
                queueManager.markAsFailed(record.id, e.message ?: "Unknown error")
            }
        }
        return successCount
    }

    /**
     * Downloads a single cloud record by syncId.
     */
    suspend fun downloadRecord(tableName: String, syncId: String): Map<String, Any>? {
        val uid = getAuthenticatedUid()
        val path = "accounts/$uid/$tableName/$syncId"
        return firebaseProxy.getRecord(path) as? Map<String, Any>
    }

    /**
     * Downloads all cloud records for a specific table.
     */
    suspend fun downloadTable(tableName: String): List<Map<String, Any>> {
        val uid = getAuthenticatedUid()
        val path = "accounts/$uid/$tableName"
        return firebaseProxy.getTableRecords(path).map { it as Map<String, Any> }
    }

    /**
     * Reconciles a single downloaded cloud record safely into the local database.
     */
    suspend fun reconcileCloudRecord(tableName: String, cloudMap: Map<String, Any>): Boolean {
        val syncId = cloudMap["syncId"] as? String ?: return false
        val cloudVersion = (cloudMap["version"] as? Number)?.toLong() ?: 1L
        val cloudLastChanged = (cloudMap["lastChanged"] as? Number)?.toLong() ?: 0L
        val isDeleted = cloudMap["isDeleted"] as? Boolean ?: false

        // 1. Get or create a local sync record mapping
        val existingSyncRecord = syncRecordDao.getRecordByFirestoreId(tableName, syncId)
        val localId = existingSyncRecord?.localId ?: 0L

        // Preserve local changes if they are pending and newer
        if (existingSyncRecord != null && existingSyncRecord.pendingSync) {
            // Check tie breaker
            if (existingSyncRecord.lastSyncTime >= cloudLastChanged) {
                println("[$TAG] Local change is newer or identical. Skipping cloud download.")
                return false
            }
        }

        // Handle deletions (tombstones)
        if (isDeleted) {
            if (localId > 0L) {
                deleteLocalEntity(tableName, localId)
                val postDeleteSyncRecord = syncRecordDao.getRecordByLocalId(tableName, localId)
                val finalSyncRecord = SyncRecordEntity(
                    id = postDeleteSyncRecord?.id ?: existingSyncRecord?.id ?: 0,
                    tableName = tableName,
                    localId = localId,
                    firestoreId = syncId,
                    lastSyncTime = cloudLastChanged,
                    pendingSync = false,
                    operation = "DELETE",
                    isDeleted = true
                )
                syncRecordDao.insertOrUpdate(finalSyncRecord)
                LocalVersionTracker.setLocalVersion(context, tableName, syncId, cloudVersion)
            }
            return true
        }

        // Resolve and map model back to local entity, then write to Room
        try {
            val resolvedLocalId = writeCloudModelToRoom(tableName, localId, cloudMap)
            if (resolvedLocalId > 0L) {
                // Upsert sync_records reference safely
                val syncRecord = SyncRecordEntity(
                    id = existingSyncRecord?.id ?: 0,
                    tableName = tableName,
                    localId = resolvedLocalId,
                    firestoreId = syncId,
                    lastSyncTime = cloudLastChanged,
                    pendingSync = false,
                    operation = "UPDATE",
                    isDeleted = false
                )
                syncRecordDao.insertOrUpdate(syncRecord)
                LocalVersionTracker.setLocalVersion(context, tableName, syncId, cloudVersion)
                return true
            }
        } catch (e: Exception) {
            println("[$TAG] Error reconciling $tableName/$syncId: ${e.message}")
        }
        return false
    }

    /**
     * Downloads and reconciles all account tables in parallel/sequence.
     */
    suspend fun downloadAccountData(): Map<String, Int> {
        val tables = listOf(
            "categories", "tables", "users", "menu_items", "offers",
            "receipt_settings", "printer_settings", "orders", "order_items",
            "stock_logs", "expenses", "notifications", "staff_food"
        )
        val statusMap = mutableMapOf<String, Int>()
        for (table in tables) {
            val cloudRecords = downloadTable(table)
            var count = 0
            for (record in cloudRecords) {
                if (reconcileCloudRecord(table, record)) {
                    count++
                }
            }
            statusMap[table] = count
        }
        return statusMap
    }

    /**
     * Resolves and maps local database entity to a raw sync model representation.
     */
    private suspend fun resolveAndMapLocalEntity(tableName: String, localId: Long, isDeleted: Boolean): Any? {
        if (isDeleted) {
            // Tombstone doesn't require loading a full business record from DB.
            return null
        }
        return when (tableName) {
            "categories" -> {
                val entity = database.categoryDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "menu_items" -> {
                val entity = database.menuItemDao().getMenuItemById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "orders" -> {
                val entity = database.orderDao().getOrderById(localId)?.order ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "order_items" -> {
                val entity = database.orderDao().getOrderItemById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "users" -> {
                val entity = database.userDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "tables" -> {
                val entity = database.tableDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "expenses" -> {
                val entity = database.expenseDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "stock_logs" -> {
                val entity = database.stockLogDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "offers" -> {
                val entity = database.offerDao().getOfferById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "notifications" -> {
                val entity = database.notificationDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "staff_food" -> {
                val entity = database.staffFoodDao().getById(localId) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "receipt_settings" -> {
                val entity = database.receiptSettingDao().getById(localId.toInt()) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            "printer_settings" -> {
                val entity = database.printerSettingDao().getById(localId.toInt()) ?: return null
                SyncModelMappers.toSyncModel(entity, syncRecordDao)
            }
            else -> null
        }
    }

    /**
     * Serializes model with server timestamp, injecting version and isDeleted.
     */
    private fun injectVersionAndDeletedState(
        tableName: String,
        model: Any?,
        version: Long,
        isDeleted: Boolean
    ): Map<String, Any?> {
        if (isDeleted || model == null) {
            return mapOf(
                "syncId" to (model?.let { getSyncIdFromModel(it) } ?: ""),
                "version" to version,
                "isDeleted" to true,
                "lastChanged" to ServerValue.TIMESTAMP
            )
        }
        return when (tableName) {
            "categories" -> ModelSerializer.categoryToMap(model as CategorySyncModel, version, isDeleted)
            "menu_items" -> ModelSerializer.menuItemToMap(model as MenuItemSyncModel, version, isDeleted)
            "orders" -> ModelSerializer.orderToMap(model as OrderSyncModel, version, isDeleted)
            "order_items" -> ModelSerializer.orderItemToMap(model as OrderItemSyncModel, version, isDeleted)
            "users" -> ModelSerializer.userToMap(model as UserSyncModel, version, isDeleted)
            "tables" -> ModelSerializer.tableToMap(model as TableSyncModel, version, isDeleted)
            "expenses" -> ModelSerializer.expenseToMap(model as ExpenseSyncModel, version, isDeleted)
            "stock_logs" -> ModelSerializer.stockLogToMap(model as StockLogSyncModel, version, isDeleted)
            "offers" -> ModelSerializer.offerToMap(model as OfferSyncModel, version, isDeleted)
            "notifications" -> ModelSerializer.notificationToMap(model as NotificationSyncModel, version, isDeleted)
            "staff_food" -> ModelSerializer.staffFoodToMap(model as StaffFoodSyncModel, version, isDeleted)
            "receipt_settings" -> ModelSerializer.receiptSettingToMap(model as ReceiptSettingSyncModel, version, isDeleted)
            "printer_settings" -> ModelSerializer.printerSettingToMap(model as PrinterSettingSyncModel, version, isDeleted)
            else -> emptyMap()
        }
    }

    private fun getSyncIdFromModel(model: Any): String {
        return try {
            val field = model.javaClass.getDeclaredField("syncId")
            field.isAccessible = true
            field.get(model) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Deserializes cloud map back to Room and writes it.
     */
    private suspend fun writeCloudModelToRoom(tableName: String, localId: Long, cloudMap: Map<String, Any>): Long {
        return when (tableName) {
            "categories" -> {
                val model = ModelParser.parseCategory(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId)
                val insertedId = database.categoryDao().insertCategory(entity)
                if (localId <= 0L) insertedId else localId
            }
            "menu_items" -> {
                val model = ModelParser.parseMenuItem(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId, syncRecordDao)
                val insertedId = database.menuItemDao().insertMenuItem(entity)
                if (localId <= 0L) insertedId else localId
            }
            "orders" -> {
                val model = ModelParser.parseOrder(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId, syncRecordDao)
                val insertedId = database.orderDao().insertOrder(entity)
                if (localId <= 0L) insertedId else localId
            }
            "order_items" -> {
                val model = ModelParser.parseOrderItem(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId, syncRecordDao)
                val insertedId = database.orderDao().insertOrderItem(entity)
                if (localId <= 0L) insertedId else localId
            }
            "users" -> {
                val model = ModelParser.parseUser(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId)
                val insertedId = database.userDao().insertUser(entity)
                if (localId <= 0L) insertedId else localId
            }
            "tables" -> {
                val model = ModelParser.parseTable(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId)
                val insertedId = database.tableDao().insertTable(entity)
                if (localId <= 0L) insertedId else localId
            }
            "expenses" -> {
                val model = ModelParser.parseExpense(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId)
                val insertedId = database.expenseDao().insertExpense(entity)
                if (localId <= 0L) insertedId else localId
            }
            "stock_logs" -> {
                val model = ModelParser.parseStockLog(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId, syncRecordDao)
                val insertedId = database.stockLogDao().insertLog(entity)
                if (localId <= 0L) insertedId else localId
            }
            "offers" -> {
                val model = ModelParser.parseOffer(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId)
                val insertedId = database.offerDao().insertOffer(entity)
                if (localId <= 0L) insertedId else localId
            }
            "notifications" -> {
                val model = ModelParser.parseNotification(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId, syncRecordDao)
                val insertedId = database.notificationDao().insertNotification(entity)
                if (localId <= 0L) insertedId else localId
            }
            "staff_food" -> {
                val model = ModelParser.parseStaffFood(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId)
                val insertedId = database.staffFoodDao().insertStaffFood(entity)
                if (localId <= 0L) insertedId else localId
            }
            "receipt_settings" -> {
                val model = ModelParser.parseReceiptSetting(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId.toInt())
                database.receiptSettingDao().saveReceiptSetting(entity)
                if (localId <= 0L) entity.id.toLong() else localId
            }
            "printer_settings" -> {
                val model = ModelParser.parsePrinterSetting(cloudMap)
                val entity = SyncModelMappers.toEntity(model, localId.toInt())
                database.printerSettingDao().savePrinterSetting(entity)
                if (localId <= 0L) entity.id.toLong() else localId
            }
            else -> 0L
        }
    }

    internal suspend fun deleteLocalEntity(tableName: String, localId: Long) {
        when (tableName) {
            "categories" -> database.categoryDao().deleteCategoryById(localId)
            "menu_items" -> database.menuItemDao().deleteMenuItemById(localId)
            "orders" -> {
                // Support delete if exists in DAO, otherwise no-op as deleting history is safety restricted.
            }
            "tables" -> database.tableDao().deleteTable(localId)
            "offers" -> database.offerDao().deleteOfferById(localId)
            "notifications" -> database.notificationDao().deleteNotification(localId)
        }
    }
}

/**
 * Manual, reflection-free serializers to cleanly inject ServerValue.TIMESTAMP.
 */
object ModelSerializer {
    fun categoryToMap(model: CategorySyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "name" to model.name,
        "itemCount" to model.itemCount,
        "iconName" to model.iconName,
        "imageUrl" to model.imageUrl,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun menuItemToMap(model: MenuItemSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "name" to model.name,
        "categorySyncId" to model.categorySyncId,
        "categoryName" to model.categoryName,
        "price" to model.price,
        "description" to model.description,
        "imageUrl" to model.imageUrl,
        "isAvailable" to model.isAvailable,
        "stockQuantity" to model.stockQuantity,
        "unit" to model.unit,
        "lowStockThreshold" to model.lowStockThreshold,
        "costPrice" to model.costPrice,
        "discountEnabled" to model.discountEnabled,
        "discountValue" to model.discountValue,
        "discountType" to model.discountType,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun orderToMap(model: OrderSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "orderNumber" to model.orderNumber,
        "orderType" to model.orderType,
        "tableNumber" to model.tableNumber,
        "customerName" to model.customerName,
        "note" to model.note,
        "subtotal" to model.subtotal,
        "discount" to model.discount,
        "tax" to model.tax,
        "total" to model.total,
        "paymentMethod" to model.paymentMethod,
        "isPaid" to model.isPaid,
        "status" to model.status,
        "timestamp" to model.timestamp,
        "tableSyncId" to model.tableSyncId,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun orderItemToMap(model: OrderItemSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "orderSyncId" to model.orderSyncId,
        "menuItemSyncId" to model.menuItemSyncId,
        "menuItemName" to model.menuItemName,
        "quantity" to model.quantity,
        "pricePerUnit" to model.pricePerUnit,
        "note" to model.note,
        "costPriceAtSale" to model.costPriceAtSale,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun userToMap(model: UserSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "emailOrPhone" to model.emailOrPhone,
        "name" to model.name,
        "role" to model.role,
        "passwordHash" to model.passwordHash,
        "firebaseUid" to model.firebaseUid,
        "isActive" to model.isActive,
        "permissions" to model.permissions,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun printerSettingToMap(model: PrinterSettingSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "connectionType" to model.connectionType,
        "printerName" to model.printerName,
        "macAddress" to model.macAddress,
        "ipAddress" to model.ipAddress,
        "port" to model.port,
        "paperSize" to model.paperSize,
        "autoPrintOnOrder" to model.autoPrintOnOrder,
        "isConnected" to model.isConnected,
        "printerType" to model.printerType,
        "bluetoothAddress" to model.bluetoothAddress,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun expenseToMap(model: ExpenseSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "title" to model.title,
        "amount" to model.amount,
        "category" to model.category,
        "note" to model.note,
        "timestamp" to model.timestamp,
        "paymentMethod" to model.paymentMethod,
        "expenseType" to model.expenseType,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun stockLogToMap(model: StockLogSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "menuItemSyncId" to model.menuItemSyncId,
        "menuItemName" to model.menuItemName,
        "changeAmount" to model.changeAmount,
        "type" to model.type,
        "note" to model.note,
        "timestamp" to model.timestamp,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun offerToMap(model: OfferSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "name" to model.name,
        "discountType" to model.discountType,
        "discountValue" to model.discountValue,
        "startDate" to model.startDate,
        "endDate" to model.endDate,
        "minOrderAmount" to model.minOrderAmount,
        "maxDiscountAmount" to model.maxDiscountAmount,
        "isActive" to model.isActive,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun notificationToMap(model: NotificationSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "type" to model.type,
        "title" to model.title,
        "message" to model.message,
        "targetSyncId" to model.targetSyncId,
        "timestamp" to model.timestamp,
        "isRead" to model.isRead,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun tableToMap(model: TableSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "name" to model.name,
        "capacity" to model.capacity,
        "isActive" to model.isActive,
        "accountId" to model.accountId,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun staffFoodToMap(model: StaffFoodSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "staffName" to model.staffName,
        "productName" to model.productName,
        "quantity" to model.quantity,
        "unitPrice" to model.unitPrice,
        "totalPrice" to model.totalPrice,
        "timestamp" to model.timestamp,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )

    fun receiptSettingToMap(model: ReceiptSettingSyncModel, overrideVersion: Long? = null, overrideIsDeleted: Boolean? = null): Map<String, Any?> = mapOf(
        "syncId" to model.syncId,
        "shopName" to model.shopName,
        "phone" to model.phone,
        "address" to model.address,
        "email" to model.email,
        "website" to model.website,
        "logoUri" to model.logoUri,
        "footerText" to model.footerText,
        "currencySymbol" to model.currencySymbol,
        "currencyCode" to model.currencyCode,
        "isTaxEnabled" to model.isTaxEnabled,
        "taxRate" to model.taxRate,
        "showShopName" to model.showShopName,
        "showLogo" to model.showLogo,
        "showPhone" to model.showPhone,
        "showAddress" to model.showAddress,
        "showOrderNumber" to model.showOrderNumber,
        "showDateTime" to model.showDateTime,
        "showCustomerName" to model.showCustomerName,
        "showOrderType" to model.showOrderType,
        "showItems" to model.showItems,
        "showQuantity" to model.showQuantity,
        "showItemPrice" to model.showItemPrice,
        "showSubtotal" to model.showSubtotal,
        "showDiscount" to model.showDiscount,
        "showTax" to model.showTax,
        "showTotal" to model.showTotal,
        "showPaymentStatus" to model.showPaymentStatus,
        "showFooter" to model.showFooter,
        "version" to (overrideVersion ?: model.version),
        "isDeleted" to (overrideIsDeleted ?: model.isDeleted),
        "lastChanged" to ServerValue.TIMESTAMP
    )
}

/**
 * Manual, reflection-free parsers to cleanly deserialize cloud map values.
 */
object ModelParser {
    fun parseCategory(map: Map<String, Any>): CategorySyncModel = CategorySyncModel(
        syncId = map["syncId"] as? String ?: "",
        name = map["name"] as? String ?: "",
        itemCount = (map["itemCount"] as? Number)?.toInt() ?: 0,
        iconName = map["iconName"] as? String ?: "",
        imageUrl = map["imageUrl"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseMenuItem(map: Map<String, Any>): MenuItemSyncModel = MenuItemSyncModel(
        syncId = map["syncId"] as? String ?: "",
        name = map["name"] as? String ?: "",
        categorySyncId = map["categorySyncId"] as? String ?: "",
        categoryName = map["categoryName"] as? String ?: "",
        price = (map["price"] as? Number)?.toDouble() ?: 0.0,
        description = map["description"] as? String ?: "",
        imageUrl = map["imageUrl"] as? String ?: "",
        isAvailable = map["isAvailable"] as? Boolean ?: true,
        stockQuantity = (map["stockQuantity"] as? Number)?.toInt() ?: 0,
        unit = map["unit"] as? String ?: "",
        lowStockThreshold = (map["lowStockThreshold"] as? Number)?.toInt() ?: 0,
        costPrice = (map["costPrice"] as? Number)?.toDouble() ?: 0.0,
        discountEnabled = map["discountEnabled"] as? Boolean ?: false,
        discountValue = (map["discountValue"] as? Number)?.toDouble() ?: 0.0,
        discountType = map["discountType"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseOrder(map: Map<String, Any>): OrderSyncModel = OrderSyncModel(
        syncId = map["syncId"] as? String ?: "",
        orderNumber = map["orderNumber"] as? String ?: "",
        orderType = map["orderType"] as? String ?: "",
        tableNumber = map["tableNumber"] as? String ?: "",
        customerName = map["customerName"] as? String ?: "",
        note = map["note"] as? String ?: "",
        subtotal = (map["subtotal"] as? Number)?.toDouble() ?: 0.0,
        discount = (map["discount"] as? Number)?.toDouble() ?: 0.0,
        tax = (map["tax"] as? Number)?.toDouble() ?: 0.0,
        total = (map["total"] as? Number)?.toDouble() ?: 0.0,
        paymentMethod = map["paymentMethod"] as? String ?: "",
        isPaid = map["isPaid"] as? Boolean ?: false,
        status = map["status"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        tableSyncId = map["tableSyncId"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseOrderItem(map: Map<String, Any>): OrderItemSyncModel = OrderItemSyncModel(
        syncId = map["syncId"] as? String ?: "",
        orderSyncId = map["orderSyncId"] as? String ?: "",
        menuItemSyncId = map["menuItemSyncId"] as? String ?: "",
        menuItemName = map["menuItemName"] as? String ?: "",
        quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
        pricePerUnit = (map["pricePerUnit"] as? Number)?.toDouble() ?: 0.0,
        note = map["note"] as? String ?: "",
        costPriceAtSale = (map["costPriceAtSale"] as? Number)?.toDouble() ?: 0.0,
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseUser(map: Map<String, Any>): UserSyncModel = UserSyncModel(
        syncId = map["syncId"] as? String ?: "",
        emailOrPhone = map["emailOrPhone"] as? String ?: "",
        name = map["name"] as? String ?: "",
        role = map["role"] as? String ?: "",
        passwordHash = map["passwordHash"] as? String ?: "",
        firebaseUid = map["firebaseUid"] as? String ?: "",
        isActive = map["isActive"] as? Boolean ?: true,
        permissions = map["permissions"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parsePrinterSetting(map: Map<String, Any>): PrinterSettingSyncModel = PrinterSettingSyncModel(
        syncId = map["syncId"] as? String ?: "",
        connectionType = map["connectionType"] as? String ?: "",
        printerName = map["printerName"] as? String ?: "",
        macAddress = map["macAddress"] as? String ?: "",
        ipAddress = map["ipAddress"] as? String ?: "",
        port = (map["port"] as? Number)?.toInt() ?: 0,
        paperSize = map["paperSize"] as? String ?: "",
        autoPrintOnOrder = map["autoPrintOnOrder"] as? Boolean ?: false,
        isConnected = map["isConnected"] as? Boolean ?: false,
        printerType = map["printerType"] as? String ?: "",
        bluetoothAddress = map["bluetoothAddress"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseExpense(map: Map<String, Any>): ExpenseSyncModel = ExpenseSyncModel(
        syncId = map["syncId"] as? String ?: "",
        title = map["title"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        category = map["category"] as? String ?: "",
        note = map["note"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        paymentMethod = map["paymentMethod"] as? String ?: "",
        expenseType = map["expenseType"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseStockLog(map: Map<String, Any>): StockLogSyncModel = StockLogSyncModel(
        syncId = map["syncId"] as? String ?: "",
        menuItemSyncId = map["menuItemSyncId"] as? String ?: "",
        menuItemName = map["menuItemName"] as? String ?: "",
        changeAmount = (map["changeAmount"] as? Number)?.toInt() ?: 0,
        type = map["type"] as? String ?: "",
        note = map["note"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseOffer(map: Map<String, Any>): OfferSyncModel = OfferSyncModel(
        syncId = map["syncId"] as? String ?: "",
        name = map["name"] as? String ?: "",
        discountType = map["discountType"] as? String ?: "",
        discountValue = (map["discountValue"] as? Number)?.toDouble() ?: 0.0,
        startDate = (map["startDate"] as? Number)?.toLong() ?: 0L,
        endDate = (map["endDate"] as? Number)?.toLong() ?: 0L,
        minOrderAmount = (map["minOrderAmount"] as? Number)?.toDouble() ?: 0.0,
        maxDiscountAmount = (map["maxDiscountAmount"] as? Number)?.toDouble() ?: 0.0,
        isActive = map["isActive"] as? Boolean ?: false,
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseNotification(map: Map<String, Any>): NotificationSyncModel = NotificationSyncModel(
        syncId = map["syncId"] as? String ?: "",
        type = map["type"] as? String ?: "",
        title = map["title"] as? String ?: "",
        message = map["message"] as? String ?: "",
        targetSyncId = map["targetSyncId"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        isRead = map["isRead"] as? Boolean ?: false,
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseTable(map: Map<String, Any>): TableSyncModel = TableSyncModel(
        syncId = map["syncId"] as? String ?: "",
        name = map["name"] as? String ?: "",
        capacity = (map["capacity"] as? Number)?.toInt() ?: 0,
        isActive = map["isActive"] as? Boolean ?: false,
        accountId = map["accountId"] as? String ?: "",
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseStaffFood(map: Map<String, Any>): StaffFoodSyncModel = StaffFoodSyncModel(
        syncId = map["syncId"] as? String ?: "",
        staffName = map["staffName"] as? String ?: "",
        productName = map["productName"] as? String ?: "",
        quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
        unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
        totalPrice = (map["totalPrice"] as? Number)?.toDouble() ?: 0.0,
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )

    fun parseReceiptSetting(map: Map<String, Any>): ReceiptSettingSyncModel = ReceiptSettingSyncModel(
        syncId = map["syncId"] as? String ?: "",
        shopName = map["shopName"] as? String ?: "",
        phone = map["phone"] as? String ?: "",
        address = map["address"] as? String ?: "",
        email = map["email"] as? String ?: "",
        website = map["website"] as? String ?: "",
        logoUri = map["logoUri"] as? String ?: "",
        footerText = map["footerText"] as? String ?: "",
        currencySymbol = map["currencySymbol"] as? String ?: "",
        currencyCode = map["currencyCode"] as? String ?: "",
        isTaxEnabled = map["isTaxEnabled"] as? Boolean ?: false,
        taxRate = (map["taxRate"] as? Number)?.toDouble() ?: 0.0,
        showShopName = map["showShopName"] as? Boolean ?: false,
        showLogo = map["showLogo"] as? Boolean ?: false,
        showPhone = map["showPhone"] as? Boolean ?: false,
        showAddress = map["showAddress"] as? Boolean ?: false,
        showOrderNumber = map["showOrderNumber"] as? Boolean ?: false,
        showDateTime = map["showDateTime"] as? Boolean ?: false,
        showCustomerName = map["showCustomerName"] as? Boolean ?: false,
        showOrderType = map["showOrderType"] as? Boolean ?: false,
        showItems = map["showItems"] as? Boolean ?: false,
        showQuantity = map["showQuantity"] as? Boolean ?: false,
        showItemPrice = map["showItemPrice"] as? Boolean ?: false,
        showSubtotal = map["showSubtotal"] as? Boolean ?: false,
        showDiscount = map["showDiscount"] as? Boolean ?: false,
        showTax = map["showTax"] as? Boolean ?: false,
        showTotal = map["showTotal"] as? Boolean ?: false,
        showPaymentStatus = map["showPaymentStatus"] as? Boolean ?: false,
        showFooter = map["showFooter"] as? Boolean ?: false,
        version = (map["version"] as? Number)?.toLong() ?: 1L,
        isDeleted = map["isDeleted"] as? Boolean ?: false,
        lastChanged = map["lastChanged"]
    )
}
