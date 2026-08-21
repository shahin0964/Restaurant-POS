package com.restaurant.pos.data.syncv3

/**
 * Cloud models representing POS database entities in Firebase Realtime Database.
 * Each class has full default parameter values to enable automatic Firebase deserialization.
 */

data class CategorySyncModel(
    val syncId: String = "",
    val name: String = "",
    val itemCount: Int = 0,
    val iconName: String = "",
    val imageUrl: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class MenuItemSyncModel(
    val syncId: String = "",
    val name: String = "",
    val categorySyncId: String = "",
    val categoryName: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val imageUrl: String = "",
    val isAvailable: Boolean = true,
    val stockQuantity: Int = 0,
    val unit: String = "",
    val lowStockThreshold: Int = 0,
    val costPrice: Double = 0.0,
    val discountEnabled: Boolean = false,
    val discountValue: Double = 0.0,
    val discountType: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class OrderSyncModel(
    val syncId: String = "",
    val orderNumber: String = "",
    val orderType: String = "",
    val tableNumber: String = "",
    val customerName: String = "",
    val note: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String = "",
    val isPaid: Boolean = false,
    val status: String = "",
    val timestamp: Long = 0L,
    val tableSyncId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class OrderItemSyncModel(
    val syncId: String = "",
    val orderSyncId: String = "",
    val menuItemSyncId: String = "",
    val menuItemName: String = "",
    val quantity: Int = 0,
    val pricePerUnit: Double = 0.0,
    val note: String = "",
    val costPriceAtSale: Double = 0.0,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class UserSyncModel(
    val syncId: String = "",
    val emailOrPhone: String = "",
    val name: String = "",
    val role: String = "",
    val passwordHash: String = "",
    val firebaseUid: String = "",
    val isActive: Boolean = true,
    val permissions: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class PrinterSettingSyncModel(
    val syncId: String = "",
    val connectionType: String = "",
    val printerName: String = "",
    val macAddress: String = "",
    val ipAddress: String = "",
    val port: Int = 0,
    val paperSize: String = "",
    val autoPrintOnOrder: Boolean = false,
    val isConnected: Boolean = false,
    val printerType: String = "",
    val bluetoothAddress: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class ExpenseSyncModel(
    val syncId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val note: String = "",
    val timestamp: Long = 0L,
    val paymentMethod: String = "",
    val expenseType: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class StockLogSyncModel(
    val syncId: String = "",
    val menuItemSyncId: String = "",
    val menuItemName: String = "",
    val changeAmount: Int = 0,
    val type: String = "",
    val note: String = "",
    val timestamp: Long = 0L,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class OfferSyncModel(
    val syncId: String = "",
    val name: String = "",
    val discountType: String = "",
    val discountValue: Double = 0.0,
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val minOrderAmount: Double = 0.0,
    val maxDiscountAmount: Double = 0.0,
    val isActive: Boolean = false,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class NotificationSyncModel(
    val syncId: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val targetSyncId: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class TableSyncModel(
    val syncId: String = "",
    val name: String = "",
    val capacity: Int = 0,
    val isActive: Boolean = false,
    val accountId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class StaffFoodSyncModel(
    val syncId: String = "",
    val staffName: String = "",
    val productName: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val timestamp: Long = 0L,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)

data class ReceiptSettingSyncModel(
    val syncId: String = "",
    val shopName: String = "",
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val website: String = "",
    val logoUri: String = "",
    val footerText: String = "",
    val currencySymbol: String = "",
    val currencyCode: String = "",
    val isTaxEnabled: Boolean = false,
    val taxRate: Double = 0.0,
    val showShopName: Boolean = false,
    val showLogo: Boolean = false,
    val showPhone: Boolean = false,
    val showAddress: Boolean = false,
    val showOrderNumber: Boolean = false,
    val showDateTime: Boolean = false,
    val showCustomerName: Boolean = false,
    val showOrderType: Boolean = false,
    val showItems: Boolean = false,
    val showQuantity: Boolean = false,
    val showItemPrice: Boolean = false,
    val showSubtotal: Boolean = false,
    val showDiscount: Boolean = false,
    val showTax: Boolean = false,
    val showTotal: Boolean = false,
    val showPaymentStatus: Boolean = false,
    val showFooter: Boolean = false,
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val lastChanged: Any? = null
)
