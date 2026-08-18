package com.restaurant.pos.data.sync

import com.restaurant.pos.data.db.*

fun OrderEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "orderNumber" to orderNumber,
        "orderType" to orderType,
        "tableNumber" to tableNumber,
        "customerName" to customerName,
        "note" to note,
        "subtotal" to subtotal,
        "discount" to discount,
        "tax" to tax,
        "total" to total,
        "paymentMethod" to paymentMethod,
        "isPaid" to isPaid,
        "status" to status,
        "timestamp" to timestamp,
        "tableId" to tableId,
    )
}

fun mapToOrderEntity(map: Map<String, Any?>): OrderEntity {
    return OrderEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        orderNumber = map["orderNumber"] as? String ?: "",
        orderType = map["orderType"] as? String ?: "",
        tableNumber = map["tableNumber"] as? String ?: "",
        customerName = map["customerName"] as? String ?: "",
        note = map["note"] as? String ?: "",
        subtotal = (map["subtotal"] as? Number)?.toDouble() ?: 0.0,
        discount = (map["discount"] as? Number)?.toDouble() ?: 0.0,
        tax = (map["tax"] as? Number)?.toDouble() ?: 0.0,
        total = (map["total"] as? Number)?.toDouble() ?: 0.0,
        paymentMethod = map["paymentMethod"] as? String ?: "",
        isPaid = map["isPaid"] as? Boolean ?: false,
        status = map["status"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        tableId = (map["tableId"] as? Number)?.toLong(),
    )
}

fun OfferEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "discountType" to discountType,
        "discountValue" to discountValue,
        "startDate" to startDate,
        "endDate" to endDate,
        "minOrderAmount" to minOrderAmount,
        "maxDiscountAmount" to maxDiscountAmount,
        "isActive" to isActive,
    )
}

fun mapToOfferEntity(map: Map<String, Any?>): OfferEntity {
    return OfferEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        name = map["name"] as? String ?: "",
        discountType = map["discountType"] as? String ?: "",
        discountValue = (map["discountValue"] as? Number)?.toDouble() ?: 0.0,
        startDate = (map["startDate"] as? Number)?.toLong() ?: 0L,
        endDate = (map["endDate"] as? Number)?.toLong() ?: 0L,
        minOrderAmount = (map["minOrderAmount"] as? Number)?.toDouble() ?: 0.0,
        maxDiscountAmount = (map["maxDiscountAmount"] as? Number)?.toDouble() ?: 0.0,
        isActive = map["isActive"] as? Boolean ?: false,
    )
}

fun CategoryEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "itemCount" to itemCount,
        "iconName" to iconName,
        "imageUrl" to imageUrl,
    )
}

fun mapToCategoryEntity(map: Map<String, Any?>): CategoryEntity {
    return CategoryEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        name = map["name"] as? String ?: "",
        itemCount = (map["itemCount"] as? Number)?.toInt() ?: 0,
        iconName = map["iconName"] as? String ?: "",
        imageUrl = map["imageUrl"] as? String ?: "",
    )
}

fun UserEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "emailOrPhone" to emailOrPhone,
        "name" to name,
        "role" to role,
        "firebaseUid" to firebaseUid,
        "isCurrentSession" to false,
        "isActive" to isActive,
        "permissions" to permissions,
    )
}

fun mapToUserEntity(map: Map<String, Any?>): UserEntity {
    return UserEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        emailOrPhone = map["emailOrPhone"] as? String ?: "",
        name = map["name"] as? String ?: "",
        role = map["role"] as? String ?: "",
        passwordHash = map["passwordHash"] as? String ?: "",
        firebaseUid = map["firebaseUid"] as? String,
        isCurrentSession = map["isCurrentSession"] as? Boolean ?: false,
        isActive = map["isActive"] as? Boolean ?: false,
        permissions = map["permissions"] as? String ?: "",
    )
}

fun StockLogEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "menuItemId" to menuItemId,
        "menuItemName" to menuItemName,
        "changeAmount" to changeAmount,
        "type" to type,
        "note" to note,
        "timestamp" to timestamp,
    )
}

fun mapToStockLogEntity(map: Map<String, Any?>): StockLogEntity {
    return StockLogEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        menuItemId = (map["menuItemId"] as? Number)?.toLong() ?: 0L,
        menuItemName = map["menuItemName"] as? String ?: "",
        changeAmount = (map["changeAmount"] as? Number)?.toInt() ?: 0,
        type = map["type"] as? String ?: "",
        note = map["note"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
    )
}

fun ExpenseEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "title" to title,
        "amount" to amount,
        "category" to category,
        "note" to note,
        "timestamp" to timestamp,
        "paymentMethod" to paymentMethod,
        "expenseType" to expenseType,
    )
}

fun mapToExpenseEntity(map: Map<String, Any?>): ExpenseEntity {
    return ExpenseEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        title = map["title"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        category = map["category"] as? String ?: "",
        note = map["note"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        paymentMethod = map["paymentMethod"] as? String ?: "Cash",
        expenseType = map["expenseType"] as? String ?: "OPERATING",
    )
}

fun MenuItemEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "categoryId" to categoryId,
        "categoryName" to categoryName,
        "price" to price,
        "description" to description,
        "imageUrl" to imageUrl,
        "isAvailable" to isAvailable,
        "stockQuantity" to stockQuantity,
        "unit" to unit,
        "lowStockThreshold" to lowStockThreshold,
        "costPrice" to costPrice,
    )
}

fun mapToMenuItemEntity(map: Map<String, Any?>): MenuItemEntity {
    return MenuItemEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        name = map["name"] as? String ?: "",
        categoryId = (map["categoryId"] as? Number)?.toLong() ?: 0L,
        categoryName = map["categoryName"] as? String ?: "",
        price = (map["price"] as? Number)?.toDouble() ?: 0.0,
        description = map["description"] as? String ?: "",
        imageUrl = map["imageUrl"] as? String ?: "",
        isAvailable = map["isAvailable"] as? Boolean ?: false,
        stockQuantity = (map["stockQuantity"] as? Number)?.toInt() ?: 0,
        unit = map["unit"] as? String ?: "",
        lowStockThreshold = (map["lowStockThreshold"] as? Number)?.toInt() ?: 0,
        costPrice = (map["costPrice"] as? Number)?.toDouble() ?: 0.0,
    )
}

fun TableEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "capacity" to capacity,
        "isActive" to isActive,
    )
}

fun mapToTableEntity(map: Map<String, Any?>): TableEntity {
    return TableEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        name = map["name"] as? String ?: "",
        capacity = (map["capacity"] as? Number)?.toInt() ?: 0,
        isActive = map["isActive"] as? Boolean ?: false,
    )
}

fun NotificationEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "type" to type,
        "title" to title,
        "message" to message,
        "targetId" to targetId,
        "timestamp" to timestamp,
        "isRead" to isRead,
    )
}

fun mapToNotificationEntity(map: Map<String, Any?>): NotificationEntity {
    return NotificationEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        type = map["type"] as? String ?: "",
        title = map["title"] as? String ?: "",
        message = map["message"] as? String ?: "",
        targetId = map["targetId"] as? String,
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        isRead = map["isRead"] as? Boolean ?: false,
    )
}

fun PrinterSettingEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "connectionType" to connectionType,
        "printerName" to printerName,
        "macAddress" to macAddress,
        "ipAddress" to ipAddress,
        "port" to port,
        "paperSize" to paperSize,
        "autoPrintOnOrder" to autoPrintOnOrder,
        "isConnected" to isConnected,
        "printerType" to printerType,
        "bluetoothAddress" to bluetoothAddress,
    )
}

fun mapToPrinterSettingEntity(map: Map<String, Any?>): PrinterSettingEntity {
    return PrinterSettingEntity(
        id = (map["id"] as? Number)?.toInt() ?: 0,
        connectionType = map["connectionType"] as? String ?: "",
        printerName = map["printerName"] as? String ?: "",
        macAddress = map["macAddress"] as? String ?: "",
        ipAddress = map["ipAddress"] as? String ?: "",
        port = (map["port"] as? Number)?.toInt() ?: 0,
        paperSize = map["paperSize"] as? String ?: "",
        autoPrintOnOrder = map["autoPrintOnOrder"] as? Boolean ?: false,
        isConnected = map["isConnected"] as? Boolean ?: false,
        printerType = map["printerType"] as? String ?: "",
        bluetoothAddress = map["bluetoothAddress"] as? String ?: "",
    )
}

fun ReceiptSettingEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "shopName" to shopName,
        "phone" to phone,
        "address" to address,
        "email" to email,
        "website" to website,
        "logoUri" to logoUri,
        "footerText" to footerText,
        "currencySymbol" to currencySymbol,
        "currencyCode" to currencyCode,
        "isTaxEnabled" to isTaxEnabled,
        "taxRate" to taxRate,
        "showShopName" to showShopName,
        "showLogo" to showLogo,
        "showPhone" to showPhone,
        "showAddress" to showAddress,
        "showOrderNumber" to showOrderNumber,
        "showDateTime" to showDateTime,
        "showCustomerName" to showCustomerName,
        "showOrderType" to showOrderType,
        "showItems" to showItems,
        "showQuantity" to showQuantity,
        "showItemPrice" to showItemPrice,
        "showSubtotal" to showSubtotal,
        "showDiscount" to showDiscount,
        "showTax" to showTax,
        "showTotal" to showTotal,
        "showPaymentStatus" to showPaymentStatus,
        "showFooter" to showFooter,
    )
}

fun mapToReceiptSettingEntity(map: Map<String, Any?>): ReceiptSettingEntity {
    return ReceiptSettingEntity(
        id = (map["id"] as? Number)?.toInt() ?: 0,
        shopName = map["shopName"] as? String ?: "",
        phone = map["phone"] as? String ?: "",
        address = map["address"] as? String ?: "",
        email = map["email"] as? String ?: "",
        website = map["website"] as? String ?: "",
        logoUri = map["logoUri"] as? String ?: "",
        footerText = map["footerText"] as? String ?: "",
        currencySymbol = map["currencySymbol"] as? String ?: "",
        currencyCode = map["currencyCode"] as? String ?: "",
        isTaxEnabled = map["isTaxEnabled"] as? Boolean ?: false,
        taxRate = (map["taxRate"] as? Number)?.toDouble() ?: 0.0,
        showShopName = map["showShopName"] as? Boolean ?: false,
        showLogo = map["showLogo"] as? Boolean ?: false,
        showPhone = map["showPhone"] as? Boolean ?: false,
        showAddress = map["showAddress"] as? Boolean ?: false,
        showOrderNumber = map["showOrderNumber"] as? Boolean ?: false,
        showDateTime = map["showDateTime"] as? Boolean ?: false,
        showCustomerName = map["showCustomerName"] as? Boolean ?: false,
        showOrderType = map["showOrderType"] as? Boolean ?: false,
        showItems = map["showItems"] as? Boolean ?: false,
        showQuantity = map["showQuantity"] as? Boolean ?: false,
        showItemPrice = map["showItemPrice"] as? Boolean ?: false,
        showSubtotal = map["showSubtotal"] as? Boolean ?: false,
        showDiscount = map["showDiscount"] as? Boolean ?: false,
        showTax = map["showTax"] as? Boolean ?: false,
        showTotal = map["showTotal"] as? Boolean ?: false,
        showPaymentStatus = map["showPaymentStatus"] as? Boolean ?: false,
        showFooter = map["showFooter"] as? Boolean ?: false,
    )
}

fun OrderItemEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "orderId" to orderId,
        "menuItemId" to menuItemId,
        "menuItemName" to menuItemName,
        "quantity" to quantity,
        "pricePerUnit" to pricePerUnit,
        "note" to note,
        "costPriceAtSale" to costPriceAtSale,
    )
}

fun mapToOrderItemEntity(map: Map<String, Any?>): OrderItemEntity {
    return OrderItemEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        orderId = (map["orderId"] as? Number)?.toLong() ?: 0L,
        menuItemId = (map["menuItemId"] as? Number)?.toLong() ?: 0L,
        menuItemName = map["menuItemName"] as? String ?: "",
        quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
        pricePerUnit = (map["pricePerUnit"] as? Number)?.toDouble() ?: 0.0,
        note = map["note"] as? String ?: "",
        costPriceAtSale = (map["costPriceAtSale"] as? Number)?.toDouble() ?: 0.0,
    )
}

