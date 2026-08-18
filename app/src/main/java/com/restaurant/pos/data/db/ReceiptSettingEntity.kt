package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipt_settings")
data class ReceiptSettingEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "",
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val website: String = "",
    val logoUri: String = "",
    val footerText: String = "",

    val currencySymbol: String = "৳",
    val currencyCode: String = "BDT",

    val isTaxEnabled: Boolean = false,
    val taxRate: Double = 0.0,

    val showShopName: Boolean = true,
    val showLogo: Boolean = true,
    val showPhone: Boolean = true,
    val showAddress: Boolean = true,
    val showOrderNumber: Boolean = true,
    val showDateTime: Boolean = true,
    val showCustomerName: Boolean = true,
    val showOrderType: Boolean = true,
    val showItems: Boolean = true,
    val showQuantity: Boolean = true,
    val showItemPrice: Boolean = true,
    val showSubtotal: Boolean = true,
    val showDiscount: Boolean = true,
    val showTax: Boolean = true,
    val showTotal: Boolean = true,
    val showPaymentStatus: Boolean = true,
    val showFooter: Boolean = true
)
