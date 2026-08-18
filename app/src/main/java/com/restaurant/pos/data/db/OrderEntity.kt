package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String, // e.g. "#1058"
    val orderType: String,   // "Dine In", "Take Away", "Delivery"
    val tableNumber: String, // e.g. "Table 05"
    val customerName: String,// e.g. "Walk-in Customer"
    val note: String = "",
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val total: Double,
    val paymentMethod: String = "Cash", // "Cash", "Card", "Mobile"
    val isPaid: Boolean = true,
    val status: String = "Pending",     // "Pending", "Preparing", "Ready", "Completed", "Cancelled"
    val timestamp: Long = System.currentTimeMillis(),
    val tableId: Long? = null
)
