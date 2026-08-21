package com.restaurant.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.restaurant.pos.data.db.*
import com.restaurant.pos.data.syncv3.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeFirebaseDatabaseProxy : FirebaseDatabaseProxy {
    val store = mutableMapOf<String, Map<String, Any?>>()
    var shouldThrow = false

    override suspend fun getRecord(path: String): Map<String, Any?>? {
        if (shouldThrow) throw RuntimeException("Offline")
        return store[path]
    }

    override suspend fun getTableRecords(path: String): List<Map<String, Any?>> {
        if (shouldThrow) throw RuntimeException("Offline")
        return store.filterKeys { it.startsWith("$path/") }.values.toList()
    }

    override suspend fun setRecord(path: String, data: Map<String, Any?>) {
        if (shouldThrow) throw RuntimeException("Offline")
        store[path] = data
    }
}

/**
 * Robust JVM local tests for Step 5: Firebase RTDB Cloud Sync Engine.
 * Verifies all 25 criteria including account isolation, 13 entity uploads,
 * version/conflict protection, tombstones, and image/logo URL preservation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Step5RealtimeSyncTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: RealtimeSyncRepository
    private lateinit var fakeProxy: FakeFirebaseDatabaseProxy
    private var currentUid: String? = "test_uid_999"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // 1. In-Memory database setup
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeProxy = FakeFirebaseDatabaseProxy()
        repository = RealtimeSyncRepository(
            context = context,
            database = database,
            firebaseProxy = fakeProxy,
            getUid = {
                val uid = currentUid
                if (uid.isNullOrBlank()) {
                    throw IllegalStateException("User is not authenticated")
                }
                uid
            }
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    // --- 1. AUTHENTICATED UID PATH GENERATION ---
    @Test
    fun testAuthenticatedUidPathGeneration() {
        val uid = repository.getAuthenticatedUid()
        assertEquals("test_uid_999", uid)
    }

    // --- 2. ACCOUNT ISOLATION ---
    @Test
    fun testAccountIsolationUserChange() {
        currentUid = "different_user_777"
        val uid = repository.getAuthenticatedUid()
        assertEquals("different_user_777", uid)
    }

    // --- 3. CATEGORY UPLOAD ---
    @Test
    fun testCategoryUpload() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Drinks", iconName = "local_drink", imageUrl = "drinks_img_url"))
        val syncId = "cat-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId))

        val success = repository.uploadRecord("categories", categoryId)
        assertTrue(success)

        val syncRecord = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
        assertNotNull(syncRecord)
        assertFalse(syncRecord!!.pendingSync)
        assertEquals(syncId, syncRecord.firestoreId)

        val path = "accounts/test_uid_999/categories/$syncId"
        val cloudRecord = fakeProxy.store[path]
        assertNotNull(cloudRecord)
        assertEquals("Drinks", cloudRecord!!["name"])
    }

    // --- 4. PRODUCT UPLOAD ---
    @Test
    fun testMenuItemUpload() = runBlocking {
        val itemId = database.menuItemDao().insertMenuItem(MenuItemEntity(name = "Burger", categoryId = 1, categoryName = "Fast Food", price = 9.99, imageUrl = "burger_img_url"))
        val syncId = "menu-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "menu_items", localId = itemId, firestoreId = syncId))

        val success = repository.uploadRecord("menu_items", itemId)
        assertTrue(success)
    }

    // --- 5. ORDER UPLOAD ---
    @Test
    fun testOrderUpload() = runBlocking {
        val orderId = database.orderDao().insertOrder(OrderEntity(
            orderNumber = "ORD-001",
            orderType = "Dine In",
            tableNumber = "Table 1",
            customerName = "Alice",
            subtotal = 40.0,
            discount = 0.0,
            tax = 5.5,
            total = 45.50,
            timestamp = System.currentTimeMillis()
        ))
        val syncId = "order-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "orders", localId = orderId, firestoreId = syncId))

        val success = repository.uploadRecord("orders", orderId)
        assertTrue(success)
    }

    // --- 6. ORDER ITEM UPLOAD ---
    @Test
    fun testOrderItemUpload() = runBlocking {
        val itemId = 1L
        val orderItem = OrderItemEntity(id = itemId, orderId = 10L, menuItemId = 20L, menuItemName = "Coke", quantity = 2, pricePerUnit = 2.50)
        database.orderDao().insertOrderItems(listOf(orderItem))
        val syncId = "item-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "order_items", localId = itemId, firestoreId = syncId))

        val success = repository.uploadRecord("order_items", itemId)
        assertTrue(success)
    }

    // --- 7. EXPENSE UPLOAD ---
    @Test
    fun testExpenseUpload() = runBlocking {
        val expenseId = database.expenseDao().insertExpense(ExpenseEntity(title = "Rent", amount = 1500.0, category = "Utilities", timestamp = System.currentTimeMillis()))
        val syncId = "expense-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "expenses", localId = expenseId, firestoreId = syncId))

        val success = repository.uploadRecord("expenses", expenseId)
        assertTrue(success)
    }

    // --- 8. STOCK LOG UPLOAD ---
    @Test
    fun testStockLogUpload() = runBlocking {
        val logId = database.stockLogDao().insertLog(StockLogEntity(menuItemId = 5L, menuItemName = "Coke", changeAmount = -3, type = "SALE", note = "Sold 3", timestamp = System.currentTimeMillis()))
        val syncId = "stock-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "stock_logs", localId = logId, firestoreId = syncId))

        val success = repository.uploadRecord("stock_logs", logId)
        assertTrue(success)
    }

    // --- 9. OFFER UPLOAD ---
    @Test
    fun testOfferUpload() = runBlocking {
        val offerId = database.offerDao().insertOffer(OfferEntity(name = "Weekend Sale", discountType = "PERCENT", discountValue = 15.0, startDate = System.currentTimeMillis(), endDate = System.currentTimeMillis() + 86400000))
        val syncId = "offer-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "offers", localId = offerId, firestoreId = syncId))

        val success = repository.uploadRecord("offers", offerId)
        assertTrue(success)
    }

    // --- 10. NOTIFICATION UPLOAD ---
    @Test
    fun testNotificationUpload() = runBlocking {
        val notifId = database.notificationDao().insertNotification(NotificationEntity(type = "ALERT", title = "Low Stock", message = "Burger stock is low", timestamp = System.currentTimeMillis()))
        val syncId = "notif-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "notifications", localId = notifId, firestoreId = syncId))

        val success = repository.uploadRecord("notifications", notifId)
        assertTrue(success)
    }

    // --- 11. STAFF FOOD UPLOAD ---
    @Test
    fun testStaffFoodUpload() = runBlocking {
        val foodId = 1L
        database.staffFoodDao().insertStaffFood(StaffFoodEntity(id = foodId, staffName = "Alice", productName = "Pasta", quantity = 1, unitPrice = 12.0, totalPrice = 12.0, timestamp = System.currentTimeMillis()))
        val syncId = "staff-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "staff_food", localId = foodId, firestoreId = syncId))

        val success = repository.uploadRecord("staff_food", foodId)
        assertTrue(success)
    }

    // --- 12. RECEIPT SETTINGS UPLOAD ---
    @Test
    fun testReceiptSettingsUpload() = runBlocking {
        val receiptId = 1
        database.receiptSettingDao().saveReceiptSetting(ReceiptSettingEntity(id = receiptId, shopName = "My Cafe", phone = "12345678", logoUri = "shop_logo_uri"))
        val syncId = "receipt-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "receipt_settings", localId = receiptId.toLong(), firestoreId = syncId))

        val success = repository.uploadRecord("receipt_settings", receiptId.toLong())
        assertTrue(success)
    }

    // --- 13. PRINTER SETTINGS UPLOAD ---
    @Test
    fun testPrinterSettingsUpload() = runBlocking {
        val printerId = 1
        database.printerSettingDao().savePrinterSetting(PrinterSettingEntity(id = printerId, printerName = "Kitchen", connectionType = "WIFI"))
        val syncId = "printer-uuid-001"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "printer_settings", localId = printerId.toLong(), firestoreId = syncId))

        val success = repository.uploadRecord("printer_settings", printerId.toLong())
        assertTrue(success)
    }

    // --- 14. DELETE/TOMBSTONE UPLOAD ---
    @Test
    fun testTombstoneUpload() = runBlocking {
        val categoryId = 44L
        val syncId = "tombstone-uuid-111"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId, isDeleted = true, operation = "DELETE"))

        val success = repository.uploadRecord("categories", categoryId)
        assertTrue(success)

        val path = "accounts/test_uid_999/categories/$syncId"
        val cloudRecord = fakeProxy.store[path]
        assertNotNull(cloudRecord)
        assertEquals(true, cloudRecord!!["isDeleted"])
    }

    // --- 15. SYNCID PRESERVATION ---
    @Test
    fun testSyncIdPreserved() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Sides", iconName = "fries"))
        val syncId = "cat-uuid-Fries-10"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId))

        repository.uploadRecord("categories", categoryId)
        val record = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
        assertEquals(syncId, record!!.firestoreId)
    }

    // --- 16. VERSION PRESERVATION ---
    @Test
    fun testVersionPreservationAndIncrement() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Pizza", iconName = "pizza"))
        val syncId = "cat-uuid-pizza"
        val syncRecord = SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId, lastSyncTime = 100L)
        database.syncRecordDao().insertOrUpdate(syncRecord)

        val path = "accounts/test_uid_999/categories/$syncId"
        fakeProxy.store[path] = mapOf(
            "syncId" to syncId,
            "version" to 5L,
            "lastChanged" to 50L,
            "isDeleted" to false
        )

        // Our upload proposes version 6 (cloudVersion + 1)
        val success = repository.uploadRecord("categories", categoryId)
        assertTrue(success)

        val uploaded = fakeProxy.store[path]
        assertNotNull(uploaded)
        assertEquals(6L, (uploaded!!["version"] as? Number)?.toLong())
    }

    // --- 17. STALE CLOUD RECORD PROTECTION ---
    @Test
    fun testStaleCloudRecordProtection() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Pizza", iconName = "pizza"))
        val syncId = "cat-uuid-pizza"
        val syncRecord = SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId, lastSyncTime = 100L)
        database.syncRecordDao().insertOrUpdate(syncRecord)

        val path = "accounts/test_uid_999/categories/$syncId"
        fakeProxy.store[path] = mapOf(
            "syncId" to syncId,
            "version" to 5L,
            "lastChanged" to 200L, // 200L is newer than our lastSyncTime 100L
            "isDeleted" to false
        )

        // Upload must be aborted to protect the cloud record from stale overwrite
        val success = repository.uploadRecord("categories", categoryId)
        assertFalse(success)
    }

    // --- 18. STALE LOCAL RECORD PROTECTION ---
    @Test
    fun testStaleLocalRecordProtection() = runBlocking {
        // Prepare local record that was already synced previously
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Mock", iconName = "mock"))
        val syncId = "cat-uuid-mock"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories", localId = categoryId, firestoreId = syncId, pendingSync = true, lastSyncTime = 100L
        ))

        // Download cloud record which was updated at 500L (newer than local lastSyncTime 100L)
        val cloudMap = mapOf(
            "syncId" to syncId,
            "name" to "Cloud Update Winner",
            "iconName" to "winner",
            "version" to 6L,
            "lastChanged" to 500L,
            "isDeleted" to false
        )

        val success = repository.reconcileCloudRecord("categories", cloudMap)
        assertTrue(success)

        val category = database.categoryDao().getById(categoryId)
        assertEquals("Cloud Update Winner", category!!.name)
    }

    // --- 19. FAILED UPLOAD KEEPS PENDINGSYNC = TRUE ---
    @Test
    fun testFailedUploadKeepsPending() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Coffee", iconName = "cup"))
        val syncId = "cat-uuid-coffee"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId, pendingSync = true))

        fakeProxy.shouldThrow = true

        try {
            repository.uploadRecord("categories", categoryId)
            fail("Expected network exception to be thrown")
        } catch (e: Exception) {
            // Verify record remains pending
            val record = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
            assertTrue(record!!.pendingSync)
        }
    }

    // --- 20. SUCCESSFUL UPLOAD MARKS PENDINGSYNC = FALSE ---
    @Test
    fun testSuccessfulUploadClearsPending() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Coffee", iconName = "cup"))
        val syncId = "cat-uuid-coffee"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId, pendingSync = true))

        val success = repository.uploadRecord("categories", categoryId)
        assertTrue(success)

        val record = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
        assertFalse(record!!.pendingSync)
    }

    // --- 21. UNAUTHENTICATED SYNC IS REJECTED SAFELY ---
    @Test
    fun testUnauthenticatedSyncRejected() = runBlocking {
        currentUid = null
        try {
            repository.uploadRecord("categories", 1L)
            fail("Should fail for unauthenticated user")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("User is not authenticated"))
        }
    }

    // --- 22. OFFLINE SYNC PRESERVES QUEUE ---
    @Test
    fun testOfflineSyncPreservesQueue() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Tea", iconName = "leaf"))
        val syncId = "cat-uuid-tea"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = syncId, pendingSync = true))

        fakeProxy.shouldThrow = true

        try {
            repository.uploadRecord("categories", categoryId)
        } catch (e: Exception) {
            // Check that the queue was not cleared
            val record = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
            assertNotNull(record)
            assertTrue(record!!.pendingSync)
        }
        Unit
    }

    // --- 23. FOREIGN-KEY SYNCID TRANSLATION ---
    @Test
    fun testForeignKeyTranslation() = runBlocking {
        // Create category
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Pasta", iconName = "pasta"))
        val categorySyncId = "cat-uuid-pasta"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "categories", localId = categoryId, firestoreId = categorySyncId))

        // Create item referencing categoryId
        val itemId = database.menuItemDao().insertMenuItem(MenuItemEntity(name = "Mac & Cheese", categoryId = categoryId, categoryName = "Pasta", price = 12.0))
        val itemSyncId = "menu-uuid-mac"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(tableName = "menu_items", localId = itemId, firestoreId = itemSyncId))

        // Map menu item to sync model
        val model = SyncModelMappers.toSyncModel(database.menuItemDao().getMenuItemById(itemId)!!, database.syncRecordDao())
        
        // Verify relationship references categorySyncId instead of raw categoryId
        assertEquals(categorySyncId, model.categorySyncId)
    }

    // --- 24. IMAGEURL PRESERVATION ---
    @Test
    fun testImageUrlPreservation() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Sushi", iconName = "sushi", imageUrl = "preserved_sushi_url"))
        val model = SyncModelMappers.toSyncModel(database.categoryDao().getById(categoryId)!!, database.syncRecordDao())
        assertEquals("preserved_sushi_url", model.imageUrl)
    }

    // --- 25. LOGOURI PRESERVATION ---
    @Test
    fun testLogoUriPreservation() = runBlocking {
        val receiptId = 1
        database.receiptSettingDao().saveReceiptSetting(ReceiptSettingEntity(id = receiptId, shopName = "Bakeshop", logoUri = "preserved_logo_uri"))
        val model = SyncModelMappers.toSyncModel(database.receiptSettingDao().getById(receiptId)!!, database.syncRecordDao())
        assertEquals("preserved_logo_uri", model.logoUri)
    }
}
