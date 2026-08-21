package com.restaurant.pos.data.syncv3

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Foundation layer for Firebase Realtime Database.
 * This class guarantees strict account isolation by dynamically referencing nodes
 * scoped exclusively under the currently authenticated Firebase User ID (UID).
 */
object RealtimeDatabaseFoundation {
    private const val TAG = "RealtimeDatabaseFoundation"

    // Reference collections matching Step 1 POS schema mapping
    const val NODE_METADATA = "metadata"
    const val NODE_CATEGORIES = "categories"
    const val NODE_MENU_ITEMS = "menu_items"
    const val NODE_ORDERS = "orders"
    const val NODE_ORDER_ITEMS = "order_items"
    const val NODE_TABLES = "tables"
    const val NODE_EXPENSES = "expenses"
    const val NODE_STOCK_LOGS = "stock_logs"
    const val NODE_OFFERS = "offers"
    const val NODE_NOTIFICATIONS = "notifications"
    const val NODE_STAFF_FOOD = "staff_food"
    const val NODE_RECEIPT_SETTINGS = "receipt_settings"
    const val NODE_PRINTER_SETTINGS = "printer_settings"

    /**
     * Get the Firebase Realtime Database instance.
     */
    val database: FirebaseDatabase
        get() = FirebaseDatabase.getInstance()

    /**
     * Retrieves the current authenticated Firebase User ID.
     * Throws [IllegalStateException] if there is no logged-in user to prevent
     * any unauthenticated operations on the database.
     */
    val currentUid: String
        get() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Log.e(TAG, "Attempted to access Realtime Database without an authenticated session.")
                throw IllegalStateException("Database operations are prohibited: User is not authenticated.")
            }
            return uid
        }

    /**
     * Gets the root DatabaseReference for the authenticated user: /accounts/{uid}
     */
    fun getAccountRootRef(): DatabaseReference {
        return database.getReference("accounts").child(currentUid)
    }

    /**
     * Gets the Reference for the account metadata: /accounts/{uid}/metadata
     */
    fun getMetadataRef(): DatabaseReference = getAccountRootRef().child(NODE_METADATA)

    /**
     * Gets the Reference for the categories collection: /accounts/{uid}/categories
     */
    fun getCategoriesRef(): DatabaseReference = getAccountRootRef().child(NODE_CATEGORIES)

    /**
     * Gets the Reference for the menu items collection: /accounts/{uid}/menu_items
     */
    fun getMenuItemsRef(): DatabaseReference = getAccountRootRef().child(NODE_MENU_ITEMS)

    /**
     * Gets the Reference for the orders collection: /accounts/{uid}/orders
     */
    fun getOrdersRef(): DatabaseReference = getAccountRootRef().child(NODE_ORDERS)

    /**
     * Gets the Reference for the order items collection: /accounts/{uid}/order_items
     */
    fun getOrderItemsRef(): DatabaseReference = getAccountRootRef().child(NODE_ORDER_ITEMS)

    /**
     * Gets the Reference for the tables collection: /accounts/{uid}/tables
     */
    fun getTablesRef(): DatabaseReference = getAccountRootRef().child(NODE_TABLES)

    /**
     * Gets the Reference for the expenses collection: /accounts/{uid}/expenses
     */
    fun getExpensesRef(): DatabaseReference = getAccountRootRef().child(NODE_EXPENSES)

    /**
     * Gets the Reference for the stock logs collection: /accounts/{uid}/stock_logs
     */
    fun getStockLogsRef(): DatabaseReference = getAccountRootRef().child(NODE_STOCK_LOGS)

    /**
     * Gets the Reference for the offers collection: /accounts/{uid}/offers
     */
    fun getOffersRef(): DatabaseReference = getAccountRootRef().child(NODE_OFFERS)

    /**
     * Gets the Reference for the notifications collection: /accounts/{uid}/notifications
     */
    fun getNotificationsRef(): DatabaseReference = getAccountRootRef().child(NODE_NOTIFICATIONS)

    /**
     * Gets the Reference for the staff food collection: /accounts/{uid}/staff_food
     */
    fun getStaffFoodRef(): DatabaseReference = getAccountRootRef().child(NODE_STAFF_FOOD)

    /**
     * Gets the Reference for the receipt settings collection: /accounts/{uid}/receipt_settings
     */
    fun getReceiptSettingsRef(): DatabaseReference = getAccountRootRef().child(NODE_RECEIPT_SETTINGS)

    /**
     * Gets the Reference for the printer settings collection: /accounts/{uid}/printer_settings
     */
    fun getPrinterSettingsRef(): DatabaseReference = getAccountRootRef().child(NODE_PRINTER_SETTINGS)
}
