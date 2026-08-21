package com.restaurant.pos.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterSettingDao {
    @Query("SELECT * FROM printer_settings WHERE id = 1 LIMIT 1")
    fun getPrinterSetting(): Flow<PrinterSettingEntity?>

    @Query("SELECT * FROM printer_settings WHERE id = 1 LIMIT 1")
    suspend fun getPrinterSettingSync(): PrinterSettingEntity?

    @Query("SELECT * FROM printer_settings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): PrinterSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePrinterSetting(setting: PrinterSettingEntity)

    @Query("DELETE FROM printer_settings")
    suspend fun clearPrinterSettings()
}
