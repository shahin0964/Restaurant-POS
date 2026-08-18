package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_logs")
data class StockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuItemId: Long,
    val menuItemName: String,
    val changeAmount: Int,
    val type: String,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
