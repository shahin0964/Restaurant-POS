package com.restaurant.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockLogDao {
    @Query("SELECT * FROM stock_logs WHERE menuItemId = :menuItemId ORDER BY timestamp DESC")
    fun getLogsForMenuItem(menuItemId: Long): Flow<List<StockLogEntity>>

    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllStockLogs(): Flow<List<StockLogEntity>>

    @Query("SELECT * FROM stock_logs ORDER BY id ASC")
    suspend fun getAllStockLogsSync(): List<StockLogEntity>

    @Query("SELECT * FROM stock_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): StockLogEntity?

    @Query("DELETE FROM stock_logs WHERE id = :id")
    suspend fun deleteStockLogById(id: Long)

    @Query("DELETE FROM stock_logs")
    suspend fun clearAllStockLogs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockLogs(logs: List<StockLogEntity>)

    @Insert
    suspend fun insertLog(log: StockLogEntity): Long
}
