package com.restaurant.pos.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.restaurant.pos.data.db.*
import com.restaurant.pos.data.network.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class CloudSyncManager(
    context: Context,
    private val db: AppDatabase,
    private val networkObserver: NetworkConnectivityObserver
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private val prefs = context.getSharedPreferences("cloud_delta_sync_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var isOnline = false

    // Collections ordered to resolve foreign-key dependencies first
    private val collections = listOf(
        "users",
        "categories",
        "tables",
        "menu_items",
        "orders",
        "order_items",
        "expenses",
        "stock_logs",
        "offers",
        "receipt_settings",
        "printer_settings",
        "notifications"
    )

    init {
        scope.launch {
            networkObserver.isOnline.collectLatest { online ->
                isOnline = online
                if (isOnline && isAuthenticated()) {
                    Log.d("CloudSyncManager", "Device came online. Initiating delta sync.")
                    syncNow()
                } else if (!isOnline) {
                    Log.d("CloudSyncManager", "Device is offline. Local DB is primary source.")
                }
            }
        }

        // Periodic delta sync every 60 seconds when online & authenticated
        scope.launch {
            while (true) {
                delay(60000)
                if (isOnline && isAuthenticated()) {
                    syncNow()
                }
            }
        }
    }

    fun isAuthenticated(): Boolean {
        return auth.currentUser != null
    }

    private fun getLastSyncTime(collection: String): Long {
        return prefs.getLong("last_sync_$collection", 0L)
    }

    private fun setLastSyncTime(collection: String, time: Long) {
        prefs.edit().putLong("last_sync_$collection", time).apply()
    }

    fun clearAllSyncCursors() {
        prefs.edit().clear().apply()
    }

    fun syncNow() {
        if (!isOnline) {
            Log.d("CloudSyncManager", "Skipping sync: Device is offline.")
            return
        }
        if (!isAuthenticated()) {
            Log.d("CloudSyncManager", "Skipping sync: Firebase user is not authenticated.")
            return
        }

        scope.launch {
            if (!syncMutex.tryLock()) {
                Log.d("CloudSyncManager", "Sync already in progress. Skipping duplicate run.")
                return@launch
            }
            try {
                Log.d("CloudSyncManager", "Delta sync started for user: ${auth.currentUser?.email}")
                pushLocalChangesToFirestore()
                pullRemoteChangesFromFirestore()
                Log.d("CloudSyncManager", "Delta sync completed successfully.")
            } catch (e: Exception) {
                Log.e("CloudSyncManager", "Delta sync encountered error: ${e.message}", e)
            } finally {
                syncMutex.unlock()
            }
        }
    }

    suspend fun performManualBackup(): Result<String> {
        if (!isOnline) return Result.failure(Exception("Internet unavailable. Cloud Backup requires an active connection."))
        if (!isAuthenticated()) return Result.failure(Exception("Unauthenticated: Please login to Firebase to backup."))

        return try {
            withTimeout(20000L) {
                syncMutex.withLock {
                    Log.d("CloudSyncManager", "Starting manual cloud backup...")
                    ensureAllEntitiesTracked()
                    pushLocalChangesToFirestore()
                    Result.success("Cloud Backup completed successfully.")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("CloudSyncManager", "Manual backup timed out waiting for active sync")
            Result.failure(Exception("Backup operation timed out waiting for active sync. Please try again."))
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Manual backup failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun performManualRestore(): Result<String> {
        if (!isOnline) return Result.failure(Exception("Internet unavailable. Cloud Restore requires an active connection."))
        if (!isAuthenticated()) return Result.failure(Exception("Unauthenticated: Please login to Firebase to restore."))

        return try {
            withTimeout(25000L) {
                syncMutex.withLock {
                    Log.d("CloudSyncManager", "Starting manual cloud restore...")
                    // Clear cursors to force a full incremental pull
                    clearAllSyncCursors()
                    pullRemoteChangesFromFirestore()
                    Result.success("Cloud Restore completed successfully.")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("CloudSyncManager", "Manual restore timed out waiting for active sync")
            Result.failure(Exception("Restore operation timed out waiting for active sync. Please try again."))
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Manual restore failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun ensureAllEntitiesTracked() {
        val syncDao = db.syncRecordDao()
        for (tableName in collections) {
            val localIds = when (tableName) {
                "categories" -> db.categoryDao().getAllCategoriesSync().map { it.id }
                "menu_items" -> db.menuItemDao().getAllMenuItemsSync().map { it.id }
                "orders" -> db.orderDao().getAllOrderEntities().map { it.id }
                "order_items" -> db.orderDao().getAllOrderItemEntities().map { it.id }
                "users" -> db.userDao().getAllUsersSync().map { it.id }
                "expenses" -> db.expenseDao().getAllExpensesSync().map { it.id }
                "stock_logs" -> db.stockLogDao().getAllStockLogsSync().map { it.id }
                "offers" -> db.offerDao().getAllOffersSync().map { it.id }
                "tables" -> db.tableDao().getAllTablesSync().map { it.id }
                "notifications" -> db.notificationDao().getAllNotificationsSync().map { it.id }
                "receipt_settings" -> listOf(1L)
                "printer_settings" -> listOf(1L)
                else -> emptyList()
            }

            for (id in localIds) {
                val existing = syncDao.getRecordByLocalId(tableName, id)
                if (existing == null) {
                    val firestoreId = if (tableName == "users") {
                        db.userDao().getUserById(id)?.firebaseUid ?: java.util.UUID.randomUUID().toString()
                    } else {
                        java.util.UUID.randomUUID().toString()
                    }
                    syncDao.insertOrUpdate(
                        SyncRecordEntity(
                            tableName = tableName,
                            localId = id,
                            firestoreId = firestoreId,
                            pendingSync = true,
                            operation = "INSERT",
                            isDeleted = false,
                            lastSyncTime = 0L
                        )
                    )
                } else if (!existing.isDeleted) {
                    syncDao.insertOrUpdate(existing.copy(pendingSync = true))
                }
            }
        }
    }

    private suspend fun pushLocalChangesToFirestore() {
        val syncDao = db.syncRecordDao()
        val pendingRecords = syncDao.getPendingSyncRecords()
        if (pendingRecords.isEmpty()) return

        for (record in pendingRecords) {
            try {
                val collectionRef = firestore.collection(record.tableName)
                val docRef = collectionRef.document(record.firestoreId)
                val now = System.currentTimeMillis()

                if (record.isDeleted || record.operation == "DELETE") {
                    // Soft-delete marker in Firestore so other delta sync clients receive the deletion
                    val deletePayload = mapOf(
                        "_deleted" to true,
                        "_lastUpdated" to now,
                        "id" to record.localId
                    )
                    docRef.set(deletePayload, SetOptions.merge()).await()
                    syncDao.markSynced(record.id, record.firestoreId, now)
                } else {
                    val entityMap = getLocalEntityAsMap(record.tableName, record.localId)
                    if (entityMap != null) {
                        val dataToSave = entityMap.toMutableMap()
                        mapForeignKeysToRemote(record.tableName, dataToSave)
                        dataToSave["_lastUpdated"] = now
                        dataToSave["_deleted"] = false
                        docRef.set(dataToSave, SetOptions.merge()).await()
                        syncDao.markSynced(record.id, record.firestoreId, now)
                    } else {
                        // Entity no longer exists in local database -> mark as deleted
                        val deletePayload = mapOf(
                            "_deleted" to true,
                            "_lastUpdated" to now,
                            "id" to record.localId
                        )
                        docRef.set(deletePayload, SetOptions.merge()).await()
                        syncDao.insertOrUpdate(
                            record.copy(
                                isDeleted = true,
                                pendingSync = false,
                                operation = "DELETE",
                                lastSyncTime = now
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CloudSyncManager", "Failed to push record ${record.id} for table ${record.tableName}: ${e.message}", e)
            }
        }
    }

    private suspend fun pullRemoteChangesFromFirestore() {
        val syncDao = db.syncRecordDao()

        for (tableName in collections) {
            try {
                val lastSyncTime = getLastSyncTime(tableName)
                val snapshot = try {
                    if (lastSyncTime <= 0L) {
                        firestore.collection(tableName).get().await()
                    } else {
                        firestore.collection(tableName)
                            .whereGreaterThan("_lastUpdated", lastSyncTime)
                            .get()
                            .await()
                    }
                } catch (e: Exception) {
                    Log.w("CloudSyncManager", "Delta query failed for $tableName, falling back to full check", e)
                    firestore.collection(tableName).get().await()
                }

                var maxTimestamp = lastSyncTime

                for (doc in snapshot.documents) {
                    val firestoreId = doc.id
                    val remoteData = doc.data?.toMutableMap() ?: continue
                    val remoteTimestamp = (remoteData["_lastUpdated"] as? Number)?.toLong() ?: 0L
                    val isRemoteDeleted = (remoteData["_deleted"] as? Boolean) == true

                    if (remoteTimestamp > maxTimestamp) {
                        maxTimestamp = remoteTimestamp
                    }

                    val existingSyncRecord = syncDao.getRecordByFirestoreId(tableName, firestoreId)

                    if (existingSyncRecord != null) {
                        if (existingSyncRecord.isDeleted) {
                            // TOMBSTONE: Deleted locally. NEVER resurrect!
                            if (!isRemoteDeleted) {
                                // Ensure Firestore also knows it was deleted
                                val deletePayload = mapOf(
                                    "_deleted" to true,
                                    "_lastUpdated" to System.currentTimeMillis()
                                )
                                doc.reference.set(deletePayload, SetOptions.merge()).await()
                            }
                        } else {
                            if (isRemoteDeleted) {
                                // Remote says deleted: Delete local entity
                                deleteLocalEntity(tableName, existingSyncRecord.localId)
                                syncDao.insertOrUpdate(
                                    existingSyncRecord.copy(
                                        isDeleted = true,
                                        pendingSync = false,
                                        operation = "DELETE",
                                        lastSyncTime = remoteTimestamp
                                    )
                                )
                            } else {
                                // Remote updated: Update locally if remote is newer and no local pending edits
                                if (!existingSyncRecord.pendingSync && remoteTimestamp > existingSyncRecord.lastSyncTime) {
                                    mapForeignKeysToLocal(tableName, remoteData)
                                    saveRemoteEntityLocally(tableName, existingSyncRecord.localId, remoteData)
                                    syncDao.insertOrUpdate(
                                        existingSyncRecord.copy(
                                            lastSyncTime = remoteTimestamp,
                                            pendingSync = false,
                                            isDeleted = false
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // No sync record by this firestoreId
                        if (isRemoteDeleted) {
                            // Remote is already deleted: Save tombstone so we never create it
                            syncDao.insertOrUpdate(
                                SyncRecordEntity(
                                    tableName = tableName,
                                    localId = -1L,
                                    firestoreId = firestoreId,
                                    lastSyncTime = remoteTimestamp,
                                    pendingSync = false,
                                    operation = "DELETE",
                                    isDeleted = true
                                )
                            )
                        } else {
                            val matchedLocalId = findMatchingLocalEntityId(tableName, firestoreId, remoteData)
                            if (matchedLocalId != null) {
                                val existingLocalSync = syncDao.getRecordByLocalId(tableName, matchedLocalId)
                                if (existingLocalSync != null && existingLocalSync.isDeleted) {
                                    // Local entity is deleted! Tombstone prevents resurrection
                                    val deletePayload = mapOf(
                                        "_deleted" to true,
                                        "_lastUpdated" to System.currentTimeMillis()
                                    )
                                    doc.reference.set(deletePayload, SetOptions.merge()).await()
                                } else {
                                    mapForeignKeysToLocal(tableName, remoteData)
                                    saveRemoteEntityLocally(tableName, matchedLocalId, remoteData)
                                    syncDao.insertOrUpdate(
                                        SyncRecordEntity(
                                            id = existingLocalSync?.id ?: 0L,
                                            tableName = tableName,
                                            localId = matchedLocalId,
                                            firestoreId = firestoreId,
                                            lastSyncTime = remoteTimestamp,
                                            pendingSync = false,
                                            operation = "INSERT",
                                            isDeleted = false
                                        )
                                    )
                                }
                            } else {
                                // Truly new remote record
                                mapForeignKeysToLocal(tableName, remoteData)
                                val newLocalId = saveNewRemoteEntityLocally(tableName, remoteData)
                                if (newLocalId != null && newLocalId > 0L) {
                                    val autoRecord = syncDao.getRecordByLocalId(tableName, newLocalId)
                                    syncDao.insertOrUpdate(
                                        SyncRecordEntity(
                                            id = autoRecord?.id ?: 0L,
                                            tableName = tableName,
                                            localId = newLocalId,
                                            firestoreId = firestoreId,
                                            lastSyncTime = remoteTimestamp,
                                            pendingSync = false,
                                            operation = "INSERT",
                                            isDeleted = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Update cursor for this collection
                val finalSyncTime = if (maxTimestamp > lastSyncTime) {
                    maxTimestamp
                } else if (lastSyncTime == 0L) {
                    System.currentTimeMillis()
                } else {
                    lastSyncTime
                }
                setLastSyncTime(tableName, finalSyncTime)
            } catch (e: Exception) {
                Log.e("CloudSyncManager", "Failed to pull delta changes for $tableName: ${e.message}", e)
            }
        }
    }

    private suspend fun findMatchingLocalEntityId(
        tableName: String,
        firestoreId: String,
        remoteData: Map<String, Any?>
    ): Long? {
        return when (tableName) {
            "users" -> {
                val uid = (remoteData["firebaseUid"] as? String) ?: firestoreId
                val emailOrPhone = remoteData["emailOrPhone"] as? String
                val userByUid = db.userDao().getUserByFirebaseUid(uid)
                if (userByUid != null) return userByUid.id
                if (!emailOrPhone.isNullOrBlank()) {
                    val userByEmail = db.userDao().getUserByEmailOrPhone(emailOrPhone)
                    if (userByEmail != null) return userByEmail.id
                }
                null
            }
            "receipt_settings" -> 1L
            "printer_settings" -> 1L
            "categories" -> {
                val name = remoteData["name"] as? String
                if (!name.isNullOrBlank()) {
                    val all = db.categoryDao().getAllCategoriesSync()
                    all.find { it.name.equals(name.trim(), ignoreCase = true) }?.id
                } else null
            }
            else -> null
        }
    }

    private suspend fun deleteLocalEntity(tableName: String, localId: Long) {
        if (localId <= 0L) return
        try {
            when (tableName) {
                "categories" -> db.categoryDao().deleteCategoryById(localId)
                "menu_items" -> db.menuItemDao().deleteMenuItemById(localId)
                "orders" -> db.orderDao().deleteOrderById(localId)
                "order_items" -> db.orderDao().deleteOrderItemById(localId)
                "users" -> db.userDao().deleteUserById(localId)
                "expenses" -> db.expenseDao().deleteExpenseById(localId)
                "stock_logs" -> db.stockLogDao().deleteStockLogById(localId)
                "offers" -> db.offerDao().deleteOfferById(localId)
                "tables" -> db.tableDao().deleteTable(localId)
                "notifications" -> db.notificationDao().deleteNotification(localId)
            }
        } catch (e: Exception) {
            Log.e("CloudSyncManager", "Failed to delete local entity $tableName with id $localId: ${e.message}")
        }
    }

    private suspend fun mapForeignKeysToRemote(tableName: String, map: MutableMap<String, Any?>) {
        val syncDao = db.syncRecordDao()
        when (tableName) {
            "menu_items" -> {
                val categoryId = (map["categoryId"] as? Number)?.toLong() ?: 0L
                if (categoryId != 0L) {
                    val remoteId = syncDao.getRecordByLocalId("categories", categoryId)?.firestoreId
                    if (remoteId != null) map["categoryId"] = remoteId
                }
            }
            "order_items" -> {
                val orderId = (map["orderId"] as? Number)?.toLong() ?: 0L
                if (orderId != 0L) {
                    val remoteId = syncDao.getRecordByLocalId("orders", orderId)?.firestoreId
                    if (remoteId != null) map["orderId"] = remoteId
                }
                val menuItemId = (map["menuItemId"] as? Number)?.toLong() ?: 0L
                if (menuItemId != 0L) {
                    val remoteId = syncDao.getRecordByLocalId("menu_items", menuItemId)?.firestoreId
                    if (remoteId != null) map["menuItemId"] = remoteId
                }
            }
            "orders" -> {
                val tableId = (map["tableId"] as? Number)?.toLong() ?: 0L
                if (tableId != 0L) {
                    val remoteId = syncDao.getRecordByLocalId("tables", tableId)?.firestoreId
                    if (remoteId != null) map["tableId"] = remoteId
                }
            }
            "stock_logs" -> {
                val menuItemId = (map["menuItemId"] as? Number)?.toLong() ?: 0L
                if (menuItemId != 0L) {
                    val remoteId = syncDao.getRecordByLocalId("menu_items", menuItemId)?.firestoreId
                    if (remoteId != null) map["menuItemId"] = remoteId
                }
            }
        }
    }

    private suspend fun mapForeignKeysToLocal(tableName: String, map: MutableMap<String, Any?>) {
        val syncDao = db.syncRecordDao()
        when (tableName) {
            "menu_items" -> {
                val remoteId = map["categoryId"] as? String
                if (remoteId != null) {
                    val localId = syncDao.getRecordByFirestoreId("categories", remoteId)?.localId
                    if (localId != null && localId > 0) map["categoryId"] = localId
                }
            }
            "order_items" -> {
                val remoteOrderId = map["orderId"] as? String
                if (remoteOrderId != null) {
                    val localId = syncDao.getRecordByFirestoreId("orders", remoteOrderId)?.localId
                    if (localId != null && localId > 0) map["orderId"] = localId
                }
                val remoteItemId = map["menuItemId"] as? String
                if (remoteItemId != null) {
                    val localId = syncDao.getRecordByFirestoreId("menu_items", remoteItemId)?.localId
                    if (localId != null && localId > 0) map["menuItemId"] = localId
                }
            }
            "orders" -> {
                val remoteTableId = map["tableId"] as? String
                if (remoteTableId != null) {
                    val localId = syncDao.getRecordByFirestoreId("tables", remoteTableId)?.localId
                    if (localId != null && localId > 0) map["tableId"] = localId
                }
            }
            "stock_logs" -> {
                val remoteItemId = map["menuItemId"] as? String
                if (remoteItemId != null) {
                    val localId = syncDao.getRecordByFirestoreId("menu_items", remoteItemId)?.localId
                    if (localId != null && localId > 0) map["menuItemId"] = localId
                }
            }
        }
    }

    private suspend fun getLocalEntityAsMap(tableName: String, localId: Long): Map<String, Any?>? {
        return when (tableName) {
            "categories" -> db.categoryDao().getById(localId)?.toMap()
            "menu_items" -> db.menuItemDao().getMenuItemById(localId)?.toMap()
            "orders" -> db.orderDao().getOrderById(localId)?.order?.toMap()
            "users" -> db.userDao().getUserById(localId)?.toMap()
            "expenses" -> db.expenseDao().getById(localId)?.toMap()
            "stock_logs" -> db.stockLogDao().getById(localId)?.toMap()
            "offers" -> db.offerDao().getOfferById(localId)?.toMap()
            "tables" -> db.tableDao().getTableById(localId)?.toMap()
            "notifications" -> db.notificationDao().getById(localId)?.toMap()
            "receipt_settings" -> db.receiptSettingDao().getById(localId.toInt())?.toMap()
            "printer_settings" -> db.printerSettingDao().getById(localId.toInt())?.toMap()
            "order_items" -> db.orderDao().getOrderItemById(localId)?.toMap()
            else -> null
        }
    }

    private suspend fun saveRemoteEntityLocally(tableName: String, localId: Long, data: Map<String, Any?>) {
        val mappedData = data.toMutableMap()
        mappedData["id"] = localId
        when (tableName) {
            "categories" -> db.categoryDao().updateCategory(mapToCategoryEntity(mappedData))
            "menu_items" -> db.menuItemDao().updateMenuItem(mapToMenuItemEntity(mappedData))
            "orders" -> db.orderDao().insertOrder(mapToOrderEntity(mappedData))
            "order_items" -> db.orderDao().insertOrderItems(listOf(mapToOrderItemEntity(mappedData)))
            "users" -> {
                val currentFbUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
                val existingLocal = db.userDao().getUserById(localId)
                val userToSave = mapToUserEntity(mappedData)
                val shouldBeCurrentSession = existingLocal?.isCurrentSession == true || (currentFbUid != null && userToSave.firebaseUid == currentFbUid)
                db.userDao().updateUser(userToSave.copy(isCurrentSession = shouldBeCurrentSession))
            }
            "expenses" -> db.expenseDao().insertExpense(mapToExpenseEntity(mappedData))
            "stock_logs" -> db.stockLogDao().insertLog(mapToStockLogEntity(mappedData))
            "offers" -> db.offerDao().updateOffer(mapToOfferEntity(mappedData))
            "tables" -> db.tableDao().updateTable(mapToTableEntity(mappedData))
            "notifications" -> db.notificationDao().insertNotification(mapToNotificationEntity(mappedData))
            "receipt_settings" -> db.receiptSettingDao().saveReceiptSetting(mapToReceiptSettingEntity(mappedData))
            "printer_settings" -> db.printerSettingDao().savePrinterSetting(mapToPrinterSettingEntity(mappedData))
        }
    }

    private suspend fun saveNewRemoteEntityLocally(tableName: String, data: Map<String, Any?>): Long? {
        val mappedData = data.toMutableMap()
        if (tableName != "receipt_settings" && tableName != "printer_settings") {
            mappedData.remove("id")
        }

        return when (tableName) {
            "categories" -> db.categoryDao().insertCategory(mapToCategoryEntity(mappedData))
            "menu_items" -> db.menuItemDao().insertMenuItem(mapToMenuItemEntity(mappedData))
            "orders" -> db.orderDao().insertOrder(mapToOrderEntity(mappedData))
            "order_items" -> {
                db.orderDao().insertOrderItems(listOf(mapToOrderItemEntity(mappedData)))
                0L
            }
            "users" -> {
                val currentFbUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
                val userToSave = mapToUserEntity(mappedData)
                val shouldBeCurrentSession = (currentFbUid != null && userToSave.firebaseUid == currentFbUid)
                db.userDao().insertUser(userToSave.copy(isCurrentSession = shouldBeCurrentSession))
            }
            "expenses" -> db.expenseDao().insertExpense(mapToExpenseEntity(mappedData))
            "stock_logs" -> db.stockLogDao().insertLog(mapToStockLogEntity(mappedData))
            "offers" -> db.offerDao().insertOffer(mapToOfferEntity(mappedData))
            "tables" -> db.tableDao().insertTable(mapToTableEntity(mappedData))
            "notifications" -> db.notificationDao().insertNotification(mapToNotificationEntity(mappedData))
            "receipt_settings" -> {
                db.receiptSettingDao().saveReceiptSetting(mapToReceiptSettingEntity(mappedData))
                1L
            }
            "printer_settings" -> {
                db.printerSettingDao().savePrinterSetting(mapToPrinterSettingEntity(mappedData))
                1L
            }
            else -> null
        }
    }
}
