package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_records",
    indices = [
        Index(value = ["tableName", "localId"], unique = true),
        Index(value = ["firestoreId"], unique = true)
    ]
)
data class SyncRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val localId: Long,
    val firestoreId: String,
    val lastSyncTime: Long = 0L,
    val pendingSync: Boolean = true,
    val operation: String = "INSERT",
    val isDeleted: Boolean = false
)
