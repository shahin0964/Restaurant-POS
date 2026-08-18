package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String = "General",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Cash",
    val expenseType: String = "OPERATING"
)
