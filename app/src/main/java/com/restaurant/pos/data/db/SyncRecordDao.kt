package com.restaurant.pos.data.db

import androidx.room.*

@Dao
interface SyncRecordDao {
    @Query("SELECT * FROM sync_records WHERE tableName = :tableName AND localId = :localId LIMIT 1")
    suspend fun getRecordByLocalId(tableName: String, localId: Long): SyncRecordEntity?

    @Query("SELECT * FROM sync_records WHERE tableName = :tableName AND firestoreId = :firestoreId LIMIT 1")
    suspend fun getRecordByFirestoreId(tableName: String, firestoreId: String): SyncRecordEntity?

    @Query("SELECT * FROM sync_records WHERE pendingSync = 1")
    suspend fun getPendingSyncRecords(): List<SyncRecordEntity>

    @Query("SELECT * FROM sync_records ORDER BY id ASC")
    suspend fun getAllSyncRecordsSync(): List<SyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: SyncRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<SyncRecordEntity>)

    @Query("UPDATE sync_records SET pendingSync = 0, lastSyncTime = :syncTime, firestoreId = :firestoreId WHERE id = :id")
    suspend fun markSynced(id: Long, firestoreId: String, syncTime: Long)

    @Delete
    suspend fun delete(entity: SyncRecordEntity)

    @Query("DELETE FROM sync_records")
    suspend fun clearAll()
}
