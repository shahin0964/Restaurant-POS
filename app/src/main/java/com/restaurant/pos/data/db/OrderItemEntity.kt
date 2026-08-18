package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val menuItemId: Long,
    val menuItemName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val note: String = "",
    val costPriceAtSale: Double = 0.0
)
