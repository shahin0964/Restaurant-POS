package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val discountType: String, // "PERCENTAGE" or "FIXED"
    val discountValue: Double,
    val startDate: Long,
    val endDate: Long,
    val minOrderAmount: Double = 0.0,
    val maxDiscountAmount: Double = 0.0,
    val isActive: Boolean = true
)
