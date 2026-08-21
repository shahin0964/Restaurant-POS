package com.restaurant.pos.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptSettingDao {
    @Query("SELECT * FROM receipt_settings WHERE id = 1 LIMIT 1")
    fun getReceiptSetting(): Flow<ReceiptSettingEntity?>

    @Query("SELECT * FROM receipt_settings WHERE id = 1 LIMIT 1")
    suspend fun getReceiptSettingSync(): ReceiptSettingEntity?

    @Query("SELECT * FROM receipt_settings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ReceiptSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReceiptSetting(setting: ReceiptSettingEntity)

    @Query("DELETE FROM receipt_settings")
    suspend fun clearReceiptSettings()
}
