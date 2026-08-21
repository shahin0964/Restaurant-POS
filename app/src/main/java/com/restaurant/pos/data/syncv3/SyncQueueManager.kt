package com.restaurant.pos.data.syncv3

import com.restaurant.pos.data.db.SyncRecordDao
import com.restaurant.pos.data.db.SyncRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manage the offline local sync queue by ordering, scheduling, and tracking
 * the synchronization states of Room database entity records.
 */
class SyncQueueManager(private val syncRecordDao: SyncRecordDao) {
    private val TAG = "SyncQueueManager"

    // Map defining relationship ordering priorities to guarantee referential integrity
    private val priorityMap = mapOf(
        "categories" to 1,
        "tables" to 2,
        "users" to 3,
        "menu_items" to 4,
        "offers" to 5,
        "receipt_settings" to 6,
        "printer_settings" to 7,
        "orders" to 8,
        "order_items" to 9,
        "stock_logs" to 10,
        "expenses" to 11,
        "notifications" to 12,
        "staff_food" to 13
    )

    // Keep track of failed synchronization attempt count for retry throttling
    private val failedSyncAttempts = mutableMapOf<Long, Int>()

    /**
     * Gets all currently pending sync records ordered by relationship priority
     * to resolve dependencies (e.g. Categories synced before Menu Items).
     */
    suspend fun getOrderedPendingQueue(): List<SyncRecordEntity> {
        val rawPending = syncRecordDao.getPendingSyncRecords()
        
        // Deduplicate in memory by firestoreId (stable sync ID) to prevent double-processing.
        // We take the latest occurrence.
        val deduplicated = rawPending.associateBy { it.firestoreId }.values.toList()

        return deduplicated.sortedWith(compareBy(
            { priorityMap[it.tableName] ?: 99 }, // Relationship order primary key
            { it.id }                           // Insertion order secondary key
        ))
    }

    /**
     * Marks a record as successfully synchronized.
     */
    suspend fun markAsCompleted(recordId: Long, syncId: String, timestamp: Long = System.currentTimeMillis()) {
        syncRecordDao.markSynced(recordId, syncId, timestamp)
        failedSyncAttempts.remove(recordId)
        println("[$TAG] Record $recordId marked completed with syncId: $syncId")
    }

    /**
     * Marks a record as failed, keeping it in the pending queue to retry later.
     */
    fun markAsFailed(recordId: Long, reason: String) {
        val attempts = failedSyncAttempts.getOrDefault(recordId, 0) + 1
        failedSyncAttempts[recordId] = attempts
        println("[$TAG] Sync failed for record $recordId (Attempt $attempts): $reason")
    }

    /**
     * Resets the failure counter to allow direct retries.
     */
    fun clearFailedRetries() {
        failedSyncAttempts.clear()
    }

    /**
     * Gets the failure attempts for a specific record.
     */
    fun getFailedAttempts(recordId: Long): Int {
        return failedSyncAttempts.getOrDefault(recordId, 0)
    }
}
