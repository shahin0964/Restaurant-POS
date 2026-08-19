package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val price: Double,
    val description: String = "",
    val imageUrl: String = "",
    val isAvailable: Boolean = true,
    val stockQuantity: Int = 20,
    val unit: String = "pcs",
    val lowStockThreshold: Int = 10,
    val costPrice: Double = 0.0,
    val discountEnabled: Boolean = false,
    val discountValue: Double = 0.0,
    val discountType: String = "PERCENTAGE"
)
