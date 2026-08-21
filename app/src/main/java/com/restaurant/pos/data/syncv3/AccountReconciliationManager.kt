package com.restaurant.pos.data.syncv3

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.restaurant.pos.data.db.AppDatabase
import com.restaurant.pos.data.db.SyncRecordEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ReconciliationState {
    object Idle : ReconciliationState()
    data class Reconciling(val currentTable: String, val progress: Int, val total: Int) : ReconciliationState()
    object Completed : ReconciliationState()
    data class Failed(val error: String) : ReconciliationState()
}

/**
 * Coordinates initial account onboarding/switching, full cloud-to-local reconciliation,
 * and launches the RealtimeCloudListener upon successful synchronization.
 */
class AccountReconciliationManager(
    private val context: Context,
    private val database: AppDatabase,
    private val syncRepository: RealtimeSyncRepository,
    private val cloudListener: RealtimeCloudListener
) {
    private val TAG = "AccountReconciliationManager"
    private val syncRecordDao = database.syncRecordDao()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _reconciliationState = MutableStateFlow<ReconciliationState>(ReconciliationState.Idle)
    val reconciliationState: StateFlow<ReconciliationState> = _reconciliationState.asStateFlow()

    private var activeAuthStateListener: FirebaseAuth.AuthStateListener? = null
    private var lastObservedUid: String? = null

    /**
     * Auth state listener that detects when a Firebase user is logged in.
     * Triggers the full cloud reconciliation in the background, then spins up the cloud listener.
     */
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            if (lastObservedUid != uid) {
                lastObservedUid = uid
                Log.i(TAG, "Auth transition detected. activeUid=$uid. Launching reconciliation job...")
                managerScope.launch {
                    try {
                        _reconciliationState.value = ReconciliationState.Reconciling("Initializing", 0, 13)
                        initializeAndReconcile(uid)
                        _reconciliationState.value = ReconciliationState.Completed
                    } catch (e: Exception) {
                        Log.e(TAG, "Critical reconciliation failure for UID $uid: ${e.message}", e)
                        _reconciliationState.value = ReconciliationState.Failed(e.message ?: "Unknown error")
                        // Start listener anyway as a fallback so user can interact with POS
                        cloudListener.startListening()
                    }
                }
            }
        } else {
            if (lastObservedUid != null) {
                Log.i(TAG, "User logged out. Stopping cloud listener.")
                lastObservedUid = null
                cloudListener.stopListening()
                _reconciliationState.value = ReconciliationState.Idle
            }
        }
    }

    /**
     * Registers this manager to listen for authentication changes.
     */
    fun register() {
        if (activeAuthStateListener == null) {
            activeAuthStateListener = authStateListener
            FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        }
    }

    /**
     * Unregisters the auth state listener and terminates listening.
     */
    fun unregister() {
        activeAuthStateListener?.let {
            FirebaseAuth.getInstance().removeAuthStateListener(it)
            activeAuthStateListener = null
        }
        cloudListener.stopListening()
    }

    /**
     * Runs the reconciliation procedure: wipes existing database tables if account switching occurred,
     * pulls down all table data from the cloud in dependency order, resolves missing/extra records,
     * preserves pending changes, and starts RealtimeCloudListener.
     */
    suspend fun initializeAndReconcile(uid: String) {
        val sharedPrefs = context.getSharedPreferences("pos_sync_prefs", Context.MODE_PRIVATE)
        val lastReconciledUid = sharedPrefs.getString("last_reconciled_uid", null)

        // 1. Isolate user accounts: if switching accounts, wipe the local DB
        if (lastReconciledUid != null && lastReconciledUid != uid) {
            Log.i(TAG, "Account switch detected: $lastReconciledUid -> $uid. Triggering a complete Room wipe.")
            clearLocalAccountData()
        }

        sharedPrefs.edit().putString("last_reconciled_uid", uid).apply()

        // 2. Tables list in strict relational/dependency order (parents before children)
        val tables = listOf(
            "categories",
            "tables",
            "users",
            "menu_items",
            "offers",
            "receipt_settings",
            "printer_settings",
            "orders",
            "order_items",
            "stock_logs",
            "expenses",
            "notifications",
            "staff_food"
        )

        Log.i(TAG, "Starting full cloud to local table pull...")
        for ((index, table) in tables.withIndex()) {
            _reconciliationState.value = ReconciliationState.Reconciling(table, index + 1, tables.size)
            try {
                Log.d(TAG, "Downloading cloud entries for: $table")
                val cloudRecords = syncRepository.downloadTable(table)
                Log.d(TAG, "Downloaded ${cloudRecords.size} cloud records for $table")

                // Reconcile each cloud record into Room
                for (record in cloudRecords) {
                    try {
                        syncRepository.reconcileCloudRecord(table, record)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reconciling record on $table: ${e.message}")
                    }
                }

                // Identify extra local records that don't exist in cloud (deleted remotely)
                resolveExtraRecords(table, cloudRecords)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to download/reconcile table $table: ${e.message}", e)
            }
        }

        Log.i(TAG, "Account initialization and reconciliation fully completed. Initializing real-time listener.")
        cloudListener.startListening()
    }

    /**
     * Checks if local records have been marked synced but are completely absent from the cloud,
     * indicating a remote deletion. Deletes them locally.
     */
    private suspend fun resolveExtraRecords(tableName: String, cloudRecords: List<Map<String, Any>>) {
        val cloudSyncIds = cloudRecords.mapNotNull { it["syncId"] as? String }.toSet()
        val allSyncRecords = syncRecordDao.getAllSyncRecordsSync()
        
        // Find local records for this table that are NOT pending sync and NOT deleted
        val syncedLocalRecords = allSyncRecords.filter {
            it.tableName == tableName && !it.pendingSync && !it.isDeleted
        }

        for (localRec in syncedLocalRecords) {
            if (localRec.firestoreId !in cloudSyncIds) {
                Log.i(TAG, "[$tableName] Extra local record detected (deleted on other device): firestoreId=${localRec.firestoreId}, localId=${localRec.localId}. Removing locally.")
                try {
                    syncRepository.deleteLocalEntity(tableName, localRec.localId)
                    syncRecordDao.insertOrUpdate(localRec.copy(
                        isDeleted = true,
                        pendingSync = false,
                        operation = "DELETE"
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete extra local record ${localRec.firestoreId}: ${e.message}")
                }
            }
        }
    }

    /**
     * Wipes all 14 synchronization-relevant Room tables and clears local sync versions.
     */
    private suspend fun clearLocalAccountData() {
        database.withTransaction {
            val tables = listOf(
                "categories", "menu_items", "orders", "order_items", "users",
                "printer_settings", "expenses", "stock_logs", "offers", 
                "receipt_settings", "notifications", "tables", "staff_food", "sync_records"
            )
            val db = database.openHelper.writableDatabase
            db.execSQL("PRAGMA foreign_keys = OFF")
            for (table in tables) {
                try {
                    db.execSQL("DELETE FROM `$table`")
                } catch (e: Exception) {
                    Log.e(TAG, "Error wiping table $table during database clear: ${e.message}")
                }
            }
        }
        LocalVersionTracker.clear(context)
    }
}
