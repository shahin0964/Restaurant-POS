package com.restaurant.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffFoodDao {
    @Insert
    suspend fun insertStaffFood(entry: StaffFoodEntity)

    @Query("SELECT * FROM staff_food WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getStaffFoodForDate(startOfDay: Long, endOfDay: Long): Flow<List<StaffFoodEntity>>

    @Query("DELETE FROM staff_food WHERE id = :id")
    suspend fun deleteStaffFood(id: Long)
}
