package com.restaurant.pos.data.repository

import com.restaurant.pos.data.db.StaffFoodDao
import com.restaurant.pos.data.db.StaffFoodEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class StaffFoodRepository(private val staffFoodDao: StaffFoodDao) {
    suspend fun addStaffFood(entry: StaffFoodEntity) {
        staffFoodDao.insertStaffFood(entry)
    }

    fun getStaffFoodForDate(dateMillis: Long): Flow<List<StaffFoodEntity>> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1L
        return staffFoodDao.getStaffFoodForDate(startOfDay, endOfDay)
    }

    suspend fun deleteStaffFood(id: Long) {
        staffFoodDao.deleteStaffFood(id)
    }
}
