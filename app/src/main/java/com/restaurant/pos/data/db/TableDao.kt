package com.restaurant.pos.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TableDao {
    @Query("SELECT * FROM tables WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TableEntity?
    @Query("SELECT * FROM tables WHERE isActive = 1 ORDER BY id ASC")
    fun getAllTables(): Flow<List<TableEntity>>

    @Query("SELECT * FROM tables")
    suspend fun getAllTablesSync(): List<TableEntity>

    @Query("SELECT * FROM tables WHERE id = :id LIMIT 1")
    suspend fun getTableById(id: Long): TableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TableEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<TableEntity>)

    @Update
    suspend fun updateTable(table: TableEntity)

    @Query("UPDATE tables SET isActive = 0 WHERE id = :id")
    suspend fun softDeleteTable(id: Long)

    @Query("DELETE FROM tables WHERE id = :id")
    suspend fun deleteTable(id: Long)

    @Query("DELETE FROM tables")
    suspend fun clearAllTables()
}
