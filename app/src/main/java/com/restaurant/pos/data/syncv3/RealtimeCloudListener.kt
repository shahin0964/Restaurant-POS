package com.restaurant.pos.data.syncv3

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.restaurant.pos.data.db.AppDatabase
import kotlinx.coroutines.*

class RealtimeCloudListener(
    private val context: Context,
    private val database: AppDatabase,
    private val syncRepository: RealtimeSyncRepository,
    private val firebaseProxy: FirebaseDatabaseProxy = RealtimeFirebaseProxy()
) {
    private val TAG = "RealtimeCloudListener"
    private val syncRecordDao = database.syncRecordDao()

    private val listenerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Keep track of active listener references to allow complete unregistration/cleanup
    private val activeListeners = mutableMapOf<DatabaseReference, ChildEventListener>()

    private var currentListeningUid: String? = null
    private var reconciliationManager: AccountReconciliationManager? = null

    private val tables = listOf(
        "categories",
        "tables",
        "users",
        "menu_items",
        "orders",
        "order_items",
        "stock_logs",
        "expenses",
        "offers",
        "notifications",
        "staff_food",
        "receipt_settings",
        "printer_settings"
    )

    /**
     * Registers the automatic Firebase Auth state listener via AccountReconciliationManager.
     */
    fun registerAuthListener() {
        if (reconciliationManager == null) {
            reconciliationManager = AccountReconciliationManager(context, database, syncRepository, this)
        }
        reconciliationManager?.register()
    }

    /**
     * Unregisters the Firebase Auth state listener and cleans up any active DB listeners.
     */
    fun unregisterAuthListener() {
        reconciliationManager?.unregister()
        stopListening()
    }

    /**
     * Starts listening to all 13 tables for the currently logged-in Firebase user.
     * Safely cleans up old listeners first to avoid memory leaks or cross-account contamination.
     */
    @Synchronized
    fun startListening() {
        val uid = try {
            syncRepository.getAuthenticatedUid()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start listening: No authenticated Firebase user. Error: ${e.message}")
            stopListening()
            return
        }

        if (currentListeningUid == uid) {
            Log.d(TAG, "Already listening to account UID: $uid")
            return
        }

        // 1. If we were listening to another user, stop first
        stopListening()

        currentListeningUid = uid
        Log.i(TAG, "Initializing real-time database listeners for account UID: $uid")

        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "Skipping socket-level listener registration: Default FirebaseApp is not initialized.")
            return
        }

        val databaseInstance = FirebaseDatabase.getInstance("https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/")

        for (table in tables) {
            val path = "accounts/$uid/$table"
            val ref = databaseInstance.getReference(path)

            val childListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    handleSnapshotChange(table, snapshot, uid)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    handleSnapshotChange(table, snapshot, uid)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    // Actual deletions are handled via `isDeleted = true` tombstones within onChildAdded/onChildChanged
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // No-op
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Listener cancelled for node $path: ${error.message}")
                }
            }

            ref.addChildEventListener(childListener)
            activeListeners[ref] = childListener
            Log.d(TAG, "Registered ChildEventListener on path: $path")
        }
    }

    /**
     * Stops and completely unregisters all active database listeners.
     */
    @Synchronized
    fun stopListening() {
        if (activeListeners.isEmpty() && currentListeningUid == null) return

        Log.i(TAG, "Stopping and clean-up of listeners for account UID: $currentListeningUid")
        for ((ref, listener) in activeListeners) {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener from reference ${ref.path}: ${e.message}")
            }
        }
        activeListeners.clear()
        currentListeningUid = null
    }

    private fun handleSnapshotChange(tableName: String, snapshot: DataSnapshot, uid: String) {
        val cloudMap = snapshot.value as? Map<String, Any?> ?: return
        val syncId = snapshot.key ?: return
        handleSnapshotMap(tableName, syncId, cloudMap, uid)
    }

    fun handleSnapshotMap(tableName: String, syncId: String, cloudMap: Map<String, Any?>, uid: String): Job {
        // Clean null values safely
        val cleanMap = cloudMap.filterValues { it != null } as Map<String, Any>

        return listenerScope.launch {
            try {
                // Ensure parent records exist first before reconciling this dependent record
                ensureParentRecordsExist(tableName, cleanMap, uid)

                // Reconcile the incoming cloud record into local Room
                reconcileCloudRecordInternal(tableName, cleanMap, uid)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing child event for $tableName/$syncId: ${e.message}", e)
            }
        }
    }

    private suspend fun ensureParentRecordsExist(tableName: String, cloudMap: Map<String, Any>, uid: String) {
        when (tableName) {
            "menu_items" -> {
                val categorySyncId = cloudMap["categorySyncId"] as? String
                ensureParentExists(tableName, "categories", categorySyncId, uid)
            }
            "orders" -> {
                val tableSyncId = cloudMap["tableSyncId"] as? String
                ensureParentExists(tableName, "tables", tableSyncId, uid)
            }
            "order_items" -> {
                val orderSyncId = cloudMap["orderSyncId"] as? String
                val menuItemSyncId = cloudMap["menuItemSyncId"] as? String
                ensureParentExists(tableName, "orders", orderSyncId, uid)
                ensureParentExists(tableName, "menu_items", menuItemSyncId, uid)
            }
            "stock_logs" -> {
                val menuItemSyncId = cloudMap["menuItemSyncId"] as? String
                ensureParentExists(tableName, "menu_items", menuItemSyncId, uid)
            }
        }
    }

    private suspend fun ensureParentExists(tableName: String, parentTable: String, parentSyncId: String?, uid: String) {
        if (parentSyncId.isNullOrBlank()) return
        val parentRecord = syncRecordDao.getRecordByFirestoreId(parentTable, parentSyncId)
        if (parentRecord == null) {
            // Parent doesn't exist locally! Fetch and reconcile it first
            val path = "accounts/$uid/$parentTable/$parentSyncId"
            val parentCloudMap = firebaseProxy.getRecord(path)
            if (parentCloudMap != null) {
                // Recursively ensure any grandparent records exist first
                val cleanParentMap = parentCloudMap.filterValues { it != null } as Map<String, Any>
                ensureParentRecordsExist(parentTable, cleanParentMap, uid)
                reconcileCloudRecordInternal(parentTable, cleanParentMap, uid)
            }
        }
    }

    private suspend fun reconcileCloudRecordInternal(tableName: String, cloudMap: Map<String, Any>, uid: String): Boolean {
        val syncId = cloudMap["syncId"] as? String ?: return false
        val cloudVersion = (cloudMap["version"] as? Number)?.toLong() ?: 1L
        val cloudLastChanged = (cloudMap["lastChanged"] as? Number)?.toLong() ?: 0L

        // Conflict check / Tie-breaker
        val localVersion = LocalVersionTracker.getLocalVersion(context, tableName, syncId)
        if (cloudVersion < localVersion) {
            Log.d(TAG, "[$tableName] Ignored: Cloud version ($cloudVersion) < Local version ($localVersion) for $syncId.")
            return false
        }

        if (cloudVersion == localVersion) {
            val existingSyncRecord = syncRecordDao.getRecordByFirestoreId(tableName, syncId)
            val localLastChanged = existingSyncRecord?.lastSyncTime ?: 0L
            if (cloudLastChanged <= localLastChanged) {
                Log.d(TAG, "[$tableName] Ignored: Cloud timestamp ($cloudLastChanged) <= Local timestamp ($localLastChanged) for identical version of $syncId.")
                return false
            }
        }

        // If newer or equal with newer timestamp, reconcile safely using the repository's logic
        Log.i(TAG, "[$tableName] Applying newer cloud update for syncId: $syncId (Cloud version: $cloudVersion, Local version: $localVersion)")
        return syncRepository.reconcileCloudRecord(tableName, cloudMap)
    }
}
