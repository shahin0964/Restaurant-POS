package com.restaurant.pos.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Query("SELECT * FROM orders ORDER BY id ASC")
    suspend fun getAllOrderEntities(): List<OrderEntity>

    @Query("SELECT * FROM order_items ORDER BY id ASC")
    suspend fun getAllOrderItemEntities(): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE id = :id LIMIT 1")
    suspend fun getOrderItemById(id: Long): OrderItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY timestamp DESC")
    fun getOrdersByStatus(status: String): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: Long): OrderWithItems?

    @Transaction
    @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
    suspend fun getOrderByNumber(orderNumber: String): OrderWithItems?

    @Query("SELECT orderNumber FROM orders WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    suspend fun getOrderNumbersInRange(startOfDay: Long, endOfDay: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String)

    @Query("UPDATE orders SET status = :status, isPaid = :isPaid WHERE id = :orderId")
    suspend fun updateOrderPaymentAndStatus(orderId: Long, status: String, isPaid: Boolean)

    @Query("SELECT COUNT(*) FROM orders")
    fun getOrderCount(): Flow<Int>

    @Query("SELECT SUM(total) FROM orders WHERE isPaid = 1 AND status != 'Cancelled'")
    fun getTotalSales(): Flow<Double?>

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrderById(id: Long)

    @Query("DELETE FROM order_items WHERE id = :id")
    suspend fun deleteOrderItemById(id: Long)

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()
}
