package com.restaurant.pos.data.repository

import com.restaurant.pos.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale

class RestaurantRepository(
    private val categoryDao: CategoryDao,
    private val menuItemDao: MenuItemDao,
    private val orderDao: OrderDao,
    private val expenseDao: ExpenseDao,
    private val stockLogDao: StockLogDao? = null,
    private val notificationRepo: NotificationRepository? = null,
    private val tableDao: TableDao? = null
) {
    val categories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val menuItems: Flow<List<MenuItemEntity>> = menuItemDao.getAllMenuItems()
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()
    
    fun getAllTables(accountId: String): Flow<List<TableEntity>> = tableDao?.getAllTables(accountId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val allStockLogs: Flow<List<StockLogEntity>> = stockLogDao?.getAllStockLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val pendingOrders: Flow<List<OrderWithItems>> = orderDao.getOrdersByStatus("Pending")
    val preparingOrders: Flow<List<OrderWithItems>> = orderDao.getOrdersByStatus("Preparing")
    val readyOrders: Flow<List<OrderWithItems>> = orderDao.getOrdersByStatus("Ready")
    val totalOrdersCount: Flow<Int> = orderDao.getOrderCount()
    val totalSalesAmount: Flow<Double?> = orderDao.getTotalSales()

    suspend fun removeSampleTables(accountId: String) {
        val allTables = tableDao?.getAllTablesSync(accountId) ?: return
        val sampleNames = listOf("Table 1", "Table 2", "Table 3", "Table 4", "Table 5", "Table 6")
        allTables.forEach { table ->
            if (sampleNames.contains(table.name)) {
                tableDao.deleteTable(table.id)
            }
        }
    }

    suspend fun addTable(name: String, capacity: Int = 4, accountId: String): Long {
        val table = TableEntity(name = name, capacity = capacity, accountId = accountId)
        return tableDao?.insertTable(table) ?: 0L
    }

    suspend fun updateTable(table: TableEntity) {
        tableDao?.updateTable(table)
    }

    suspend fun deleteTable(id: Long) {
        tableDao?.deleteTable(id)
    }

    private suspend fun checkAndTriggerStockNotification(item: MenuItemEntity, newStock: Int) {
        if (newStock == 0) {
            notificationRepo?.emitNotification(
                type = "OUT_OF_STOCK",
                title = "OUT OF STOCK",
                message = "${item.name}\nRemaining: 0",
                targetId = "OUT_OF_STOCK_${item.id}_0"
            )
        } else if (newStock <= item.lowStockThreshold) {
            notificationRepo?.emitNotification(
                type = "LOW_STOCK",
                title = "LOW STOCK",
                message = "${item.name}\nRemaining: $newStock",
                targetId = "LOW_STOCK_${item.id}_$newStock"
            )
        }
    }

    fun getStockLogsForMenuItem(menuItemId: Long): Flow<List<StockLogEntity>> {
        return stockLogDao?.getLogsForMenuItem(menuItemId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun updateItemStock(
        menuItemId: Long,
        newQuantity: Int,
        unit: String? = null,
        lowStockThreshold: Int? = null,
        reasonNote: String = ""
    ) {
        val item = menuItemDao.getMenuItemById(menuItemId) ?: return
        val oldQty = item.stockQuantity
        val finalUnit = unit?.ifBlank { item.unit } ?: item.unit
        val finalThreshold = lowStockThreshold ?: item.lowStockThreshold
        val updatedItem = item.copy(
            stockQuantity = newQuantity,
            unit = finalUnit,
            lowStockThreshold = finalThreshold
        )
        menuItemDao.updateMenuItem(updatedItem)
        checkAndTriggerStockNotification(updatedItem, newQuantity)

        val diff = newQuantity - oldQty
        if (diff != 0 || reasonNote.isNotBlank()) {
            stockLogDao?.insertLog(
                StockLogEntity(
                    menuItemId = menuItemId,
                    menuItemName = item.name,
                    changeAmount = diff,
                    type = if (diff > 0) "ADD" else if (diff < 0) "REDUCE" else "ADJUST",
                    note = reasonNote.ifBlank { if (diff > 0) "+$diff Stock Added" else "$diff Stock Reduced" }
                )
            )
        }
    }

    suspend fun addExpense(title: String, amount: Double, category: String, note: String, paymentMethod: String = "Cash", expenseType: String = "OPERATING"): Long {
        return expenseDao.insertExpense(
            ExpenseEntity(
                title = title,
                amount = amount,
                category = category,
                note = note,
                timestamp = System.currentTimeMillis(),
                paymentMethod = paymentMethod,
                expenseType = expenseType
            )
        )
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun saveMenuItem(
        id: Long = 0,
        name: String,
        categoryName: String,
        price: Double,
        costPrice: Double = 0.0,
        description: String,
        imageUrl: String,
        isAvailable: Boolean,
        stockQuantity: Int = 20,
        unit: String = "pcs",
        lowStockThreshold: Int = 10
    ): Long {
        var categoryId: Long = 1
        val allCats = categoryDao.getAllCategories().first()
        val existingCat = allCats.find { it.name.equals(categoryName, ignoreCase = true) }
        if (existingCat != null) {
            categoryId = existingCat.id
        } else if (categoryName.isNotBlank()) {
            categoryId = categoryDao.insertCategory(CategoryEntity(name = categoryName.trim()))
        }
        val existingItem = if (id != 0L) menuItemDao.getMenuItemById(id) else null
        val item = MenuItemEntity(
            id = id,
            name = name,
            categoryId = categoryId,
            categoryName = if (categoryName.isBlank()) "General" else categoryName.trim(),
            price = price,
            costPrice = costPrice,
            description = description,
            imageUrl = imageUrl,
            isAvailable = isAvailable,
            stockQuantity = if (existingItem != null && stockQuantity == 20) existingItem.stockQuantity else stockQuantity,
            unit = if (existingItem != null && unit == "pcs") existingItem.unit else unit,
            lowStockThreshold = if (existingItem != null && lowStockThreshold == 10) existingItem.lowStockThreshold else lowStockThreshold
        )
        return if (id == 0L) {
            menuItemDao.insertMenuItem(item)
        } else {
            menuItemDao.updateMenuItem(item)
            id
        }
    }

    suspend fun deleteMenuItem(item: MenuItemEntity) {
        menuItemDao.deleteMenuItem(item)
    }

    suspend fun updateMenuItem(item: MenuItemEntity) {
        menuItemDao.updateMenuItem(item)
    }

    suspend fun addItemsToExistingOrder(orderId: Long, cartItems: List<CartItem>, activeTaxRate: Double? = null) {
        val orderWithItems = orderDao.getOrderById(orderId) ?: return
        val order = orderWithItems.order
        val existingItems = orderWithItems.items.toMutableList()

        for (cartItem in cartItems) {
            val matchingIndex = existingItems.indexOfFirst {
                it.menuItemId == cartItem.menuItem.id && it.note == cartItem.note
            }
            if (matchingIndex >= 0) {
                val existing = existingItems[matchingIndex]
                val updated = existing.copy(quantity = existing.quantity + cartItem.quantity)
                orderDao.insertOrderItems(listOf(updated))
                existingItems[matchingIndex] = updated
            } else {
                val newItem = OrderItemEntity(
                    orderId = orderId,
                    menuItemId = cartItem.menuItem.id,
                    menuItemName = cartItem.menuItem.name,
                    quantity = cartItem.quantity,
                    pricePerUnit = cartItem.menuItem.price,
                    note = cartItem.note,
                    costPriceAtSale = cartItem.menuItem.costPrice
                )
                orderDao.insertOrderItems(listOf(newItem))
                existingItems.add(newItem)
            }
        }

        // Recalculate Subtotal from all items in this order
        val refreshedOrderWithItems = orderDao.getOrderById(orderId) ?: return
        val newSubtotal = refreshedOrderWithItems.items.sumOf { it.pricePerUnit * it.quantity }

        // Recalculate Discount according to existing discount rate
        val discountRate = if (order.subtotal > 0.0 && order.discount > 0.0) {
            order.discount / order.subtotal
        } else {
            0.0
        }
        val newDiscount = if (discountRate > 0.0) {
            newSubtotal * discountRate
        } else {
            order.discount
        }

        // Recalculate Tax using existing tax rate or active tax rate setting
        val taxRate = activeTaxRate ?: if (order.subtotal > 0.0 && order.tax > 0.0) {
            val netOld = (order.subtotal - order.discount).coerceAtLeast(1.0)
            (order.tax / netOld) * 100.0
        } else {
            0.0
        }

        val netAmount = (newSubtotal - newDiscount).coerceAtLeast(0.0)
        val newTax = if (taxRate > 0.0) netAmount * (taxRate / 100.0) else 0.0
        val newTotal = (netAmount + newTax).coerceAtLeast(0.0)

        orderDao.insertOrder(
            order.copy(
                subtotal = newSubtotal,
                discount = newDiscount,
                tax = newTax,
                total = newTotal
            )
        )
    }

    suspend fun saveCategory(category: CategoryEntity): Long {
        return if (category.id == 0L) {
            categoryDao.insertCategory(category)
        } else {
            categoryDao.updateCategory(category)
            val items = menuItemDao.getAllMenuItemsSync()
            items.filter { it.categoryId == category.id }.forEach { item ->
                menuItemDao.updateMenuItem(item.copy(categoryName = category.name))
            }
            category.id
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.deleteCategory(category)
        val items = menuItemDao.getAllMenuItemsSync()
        items.filter { it.categoryId == category.id }.forEach { item ->
            menuItemDao.updateMenuItem(item.copy(categoryId = 1L, categoryName = "General"))
        }
    }

    fun getMenuItemsByCategory(categoryId: Long): Flow<List<MenuItemEntity>> {
        return menuItemDao.getMenuItemsByCategory(categoryId)
    }

    suspend fun getOrderById(orderId: Long): OrderWithItems? {
        return orderDao.getOrderById(orderId)
    }

    suspend fun getOrderByNumber(orderNumber: String): OrderWithItems? {
        return orderDao.getOrderByNumber(orderNumber)
    }

    suspend fun createOrder(
        orderType: String,
        tableNumber: String,
        customerName: String,
        note: String,
        subtotal: Double,
        discount: Double,
        tax: Double,
        total: Double,
        paymentMethod: String,
        cartItems: List<CartItem>,
        tableId: Long? = null
    ): Long {
        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1L

        val todayOrders = orderDao.getOrderNumbersInRange(startOfDay, endOfDay)
        val maxTodaySeq = todayOrders.mapNotNull { num ->
            val clean = num.trim()
            if (clean.startsWith("ORD-", ignoreCase = true)) {
                clean.substringAfter("ORD-").toIntOrNull()
            } else if (clean.startsWith("ORD", ignoreCase = true)) {
                clean.substringAfter("ORD").toIntOrNull()
            } else if (clean.startsWith("#")) {
                clean.substringAfter("#").toIntOrNull()?.let { if (it < 1000) it else null }
            } else {
                clean.toIntOrNull()
            }
        }.maxOrNull() ?: 0

        val nextSeq = maxTodaySeq + 1
        val orderNum = String.format(Locale.US, "ORD-%03d", nextSeq)

        val order = OrderEntity(
            orderNumber = orderNum,
            orderType = orderType,
            tableNumber = tableNumber,
            customerName = if (customerName.isBlank()) "Walk-in Customer" else customerName,
            note = note,
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            total = total,
            paymentMethod = paymentMethod,
            isPaid = false,
            status = "Pending",
            timestamp = currentTime,
            tableId = tableId
        )

        val orderId = orderDao.insertOrder(order)
        val itemsToInsert = cartItems.map {
            OrderItemEntity(
                orderId = orderId,
                menuItemId = it.menuItem.id,
                menuItemName = it.menuItem.name,
                quantity = it.quantity,
                pricePerUnit = it.menuItem.price,
                note = it.note,
                costPriceAtSale = it.menuItem.costPrice
            )
        }
        orderDao.insertOrderItems(itemsToInsert)

        val formattedTotal = String.format(Locale.US, "%.0f", total)
        notificationRepo?.emitNotification(
            type = "NEW_ORDER",
            title = "NEW ORDER",
            message = "Order $orderNum\n৳$formattedTotal",
            targetId = "NEW_ORDER_$orderId"
        )

        // Deduct inventory stock for created order
        for (cartItem in cartItems) {
            val menuItem = menuItemDao.getMenuItemById(cartItem.menuItem.id)
            if (menuItem != null) {
                val newStock = (menuItem.stockQuantity - cartItem.quantity).coerceAtLeast(0)
                val updatedItem = menuItem.copy(stockQuantity = newStock)
                menuItemDao.updateMenuItem(updatedItem)
                stockLogDao?.insertLog(
                    StockLogEntity(
                        menuItemId = menuItem.id,
                        menuItemName = menuItem.name,
                        changeAmount = -cartItem.quantity,
                        type = "SALE",
                        note = "Sold ${cartItem.quantity} (Order $orderNum)"
                    )
                )
                checkAndTriggerStockNotification(updatedItem, newStock)
            }
        }

        return orderId
    }

    suspend fun updateOrderStatus(orderId: Long, newStatus: String) {
        orderDao.updateOrderStatus(orderId, newStatus)
        if (newStatus.equals("Ready", ignoreCase = true)) {
            val orderWithItems = orderDao.getOrderById(orderId)
            val orderNum = orderWithItems?.order?.orderNumber ?: "#$orderId"
            notificationRepo?.emitNotification(
                type = "ORDER_READY",
                title = "ORDER READY",
                message = "Order $orderNum is ready",
                targetId = "ORDER_READY_$orderId"
            )
        }
    }

    suspend fun markOrderAsPaid(orderId: Long) {
        orderDao.updateOrderPaymentAndStatus(orderId, status = "Completed", isPaid = true)
        val orderWithItems = orderDao.getOrderById(orderId)
        if (orderWithItems != null) {
            val orderNum = orderWithItems.order.orderNumber
            val formattedTotal = String.format(Locale.US, "%.0f", orderWithItems.order.total)
            notificationRepo?.emitNotification(
                type = "PAYMENT_CONFIRMED",
                title = "PAYMENT CONFIRMED",
                message = "Order $orderNum\n৳$formattedTotal",
                targetId = "PAYMENT_CONFIRMED_$orderId"
            )
        }
    }

    suspend fun clearExistingProductsAndCategories() {
        menuItemDao.clearAll()
        categoryDao.clearAll()
    }

    suspend fun seedDatabaseIfNeeded() {
        // Sample tables removal as requested
    }
}

data class CartItem(
    val menuItem: MenuItemEntity,
    var quantity: Int,
    var note: String = ""
)
