package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tables")
data class TableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val capacity: Int = 4,
    val isActive: Boolean = true
)
