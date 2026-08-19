package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff_food")
data class StaffFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffName: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val timestamp: Long
)
