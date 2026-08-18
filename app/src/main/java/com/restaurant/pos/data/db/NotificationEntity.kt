package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "NEW_ORDER", "LOW_STOCK", "OUT_OF_STOCK", "PAYMENT_CONFIRMED", "ORDER_READY"
    val title: String,
    val message: String,
    val targetId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
