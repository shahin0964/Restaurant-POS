package com.restaurant.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffFoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaffFood(entry: StaffFoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaffFoodList(entries: List<StaffFoodEntity>)

    @Query("SELECT * FROM staff_food ORDER BY id ASC")
    suspend fun getAllStaffFoodSync(): List<StaffFoodEntity>

    @Query("SELECT * FROM staff_food WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getStaffFoodForDate(startOfDay: Long, endOfDay: Long): Flow<List<StaffFoodEntity>>

    @Query("DELETE FROM staff_food WHERE id = :id")
    suspend fun deleteStaffFood(id: Long)

    @Query("DELETE FROM staff_food")
    suspend fun clearAllStaffFood()
}
