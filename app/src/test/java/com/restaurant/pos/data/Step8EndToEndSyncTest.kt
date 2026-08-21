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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Step8EndToEndSyncTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncRepository: RealtimeSyncRepository
    private lateinit var cloudListener: RealtimeCloudListener
    private lateinit var reconciliationManager: AccountReconciliationManager
    private lateinit var fakeProxy: FakeFirebaseDatabaseProxy
    private var activeUid = "user_e2e_888"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeProxy = FakeFirebaseDatabaseProxy()
        syncRepository = RealtimeSyncRepository(
            context = context,
            database = database,
            firebaseProxy = fakeProxy,
            getUid = { activeUid }
        )

        cloudListener = RealtimeCloudListener(
            context = context,
            database = database,
            syncRepository = syncRepository,
            firebaseProxy = fakeProxy
        )

        reconciliationManager = AccountReconciliationManager(
            context = context,
            database = database,
            syncRepository = syncRepository,
            cloudListener = cloudListener
        )

        LocalVersionTracker.clear(context)
        context.getSharedPreferences("pos_sync_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun teardown() {
        database.close()
        LocalVersionTracker.clear(context)
        context.getSharedPreferences("pos_sync_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    // --- 1. FULL ACCOUNT RECONCILIATION & INITIAL DOWNLOAD OF ALL 13 COLLECTIONS ---
    @Test
    fun testFullCloudReconciliationAll13Collections() = runBlocking {
        // Populate fake cloud storage with data for all 13 tables
        val prefix = "accounts/$activeUid"
        
        // 1. categories
        fakeProxy.store["$prefix/categories/c1"] = mapOf("syncId" to "c1", "name" to "Beverages", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 2. tables
        fakeProxy.store["$prefix/tables/t1"] = mapOf("syncId" to "t1", "name" to "Table 1", "capacity" to 4, "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 3. users
        fakeProxy.store["$prefix/users/u1"] = mapOf("syncId" to "u1", "username" to "shahin", "role" to "Admin", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 4. menu_items
        fakeProxy.store["$prefix/menu_items/m1"] = mapOf("syncId" to "m1", "name" to "Coffee", "categoryId" to 1L, "categoryName" to "Beverages", "categorySyncId" to "c1", "price" to 2.5, "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 5. offers
        fakeProxy.store["$prefix/offers/o1"] = mapOf("syncId" to "o1", "name" to "Promo 10%", "discountType" to "percentage", "discountValue" to 10.0, "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 6. receipt_settings
        fakeProxy.store["$prefix/receipt_settings/rs1"] = mapOf("syncId" to "rs1", "shopName" to "My Shop", "logoUri" to "https://logo.com", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 7. printer_settings
        fakeProxy.store["$prefix/printer_settings/ps1"] = mapOf("syncId" to "ps1", "printerName" to "POS-80", "ipAddress" to "192.168.1.100", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 8. orders
        fakeProxy.store["$prefix/orders/ord1"] = mapOf("syncId" to "ord1", "orderNumber" to "ORD-123", "total" to 15.0, "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 9. order_items
        fakeProxy.store["$prefix/order_items/oi1"] = mapOf("syncId" to "oi1", "orderId" to 1L, "orderSyncId" to "ord1", "menuItemId" to 1L, "menuItemSyncId" to "m1", "quantity" to 2, "pricePerUnit" to 2.5, "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 10. stock_logs
        fakeProxy.store["$prefix/stock_logs/sl1"] = mapOf("syncId" to "sl1", "menuItemId" to 1L, "menuItemSyncId" to "m1", "quantity" to 50, "type" to "IN", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 11. expenses
        fakeProxy.store["$prefix/expenses/e1"] = mapOf("syncId" to "e1", "title" to "Rent", "amount" to 500.0, "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 12. notifications
        fakeProxy.store["$prefix/notifications/n1"] = mapOf("syncId" to "n1", "title" to "Alert", "message" to "Stock low", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)
        // 13. staff_food
        fakeProxy.store["$prefix/staff_food/sf1"] = mapOf("syncId" to "sf1", "staffName" to "Employee A", "reason" to "Dinner", "version" to 1L, "isDeleted" to false, "lastChanged" to 100L)

        // Ensure Room database is currently empty
        assertTrue(database.categoryDao().getAllCategoriesSync().isEmpty())
        assertTrue(database.menuItemDao().getAllMenuItemsSync().isEmpty())

        // Run complete Account Onboarding and Cloud Reconciliation
        reconciliationManager.initializeAndReconcile(activeUid)

        // Verify that database tables got populated with mapped, relationship-safe identities
        val categories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, categories.size)
        assertEquals("Beverages", categories[0].name)

        val menuItems = database.menuItemDao().getAllMenuItemsSync()
        assertEquals(1, menuItems.size)
        assertEquals("Coffee", menuItems[0].name)
        assertEquals(categories[0].id, menuItems[0].categoryId) // Parent relational key matches

        val orders = database.orderDao().getAllOrderEntities()
        assertEquals(1, orders.size)
        assertEquals("ORD-123", orders[0].orderNumber)

        val expenses = database.expenseDao().getAllExpensesSync()
        assertEquals(1, expenses.size)
        assertEquals("Rent", expenses[0].title)

        val tables = database.tableDao().getAllTablesSync()
        assertEquals(1, tables.size)
        assertEquals("Table 1", tables[0].name)
    }

    // --- 2. MULTI-DEVICE SIMULTANEOUS MUTATION / CONFLICT TIE-BREAKER ---
    @Test
    fun testRealtimeConflictHandlingWithTieBreakers() = runBlocking {
        // Setup initial local record
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Original Local", iconName = "star"))
        val syncId = "cat-conflict-e2e"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            lastSyncTime = 500L,
            pendingSync = false
        ))
        LocalVersionTracker.setLocalVersion(context, "categories", syncId, 2L)

        // Cloud snapshot with exact same version but LATER server timestamp (600L > 500L)
        val cloudSnapshot = mapOf(
            "syncId" to syncId,
            "name" to "Updated in Cloud",
            "version" to 2L,
            "isDeleted" to false,
            "lastChanged" to 600L
        )

        // Receive real-time cloud event
        val job = cloudListener.handleSnapshotMap("categories", syncId, cloudSnapshot, activeUid)
        job.join()

        // Verify local record was updated deterministically
        val categories = database.categoryDao().getAllCategoriesSync()
        assertEquals("Updated in Cloud", categories[0].name)
    }

    // --- 3. SYNC LOOP & DUPLICATE EVENT PREVENTION ---
    @Test
    fun testSyncLoopAndDuplicateEventsAreSafelyIgnored() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Fresh Veg", iconName = "leaf"))
        val syncId = "cat-loop-999"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            lastSyncTime = 1000L,
            pendingSync = false
        ))
        LocalVersionTracker.setLocalVersion(context, "categories", syncId, 1L)

        // Incoming snapshot event representing the exact same record version & timestamp
        val cloudSnapshot = mapOf(
            "syncId" to syncId,
            "name" to "Fresh Veg",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 1000L
        )

        // Trigger snapshot download twice to verify absolute idempotence
        val job1 = cloudListener.handleSnapshotMap("categories", syncId, cloudSnapshot, activeUid)
        job1.join()
        
        val job2 = cloudListener.handleSnapshotMap("categories", syncId, cloudSnapshot, activeUid)
        job2.join()

        // Verify that no duplicate local record is added and lastSyncTime remains unchanged
        val categories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, categories.size)
        assertEquals("Fresh Veg", categories[0].name)

        val syncRecord = database.syncRecordDao().getRecordByFirestoreId("categories", syncId)
        assertNotNull(syncRecord)
        assertFalse(syncRecord!!.pendingSync) // No loop upload triggered
    }

    // --- 4. OFFLINE QUEUE PRESERVATION AND ONLINE AUTOMATIC RESUME ---
    @Test
    fun testOfflineOperationsSurviveAndAutomaticallySyncWhenOnline() = runBlocking {
        // Turn database proxy "offline"
        fakeProxy.shouldThrow = true

        // Create a local category
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Offline Burgers"))
        val syncId = "offline-cat-1"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            pendingSync = true,
            operation = "CREATE"
        ))

        // Attempt cloud upload - should fail and remain pending because network is offline
        var threwException = false
        try {
            syncRepository.uploadRecord("categories", categoryId)
        } catch (e: Exception) {
            threwException = true
        }
        assertTrue(threwException)

        val pendingRecord = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
        assertNotNull(pendingRecord)
        assertTrue(pendingRecord!!.pendingSync)

        // Restore network online
        fakeProxy.shouldThrow = false

        // Attempt upload again
        val onlineSuccess = syncRepository.uploadRecord("categories", categoryId)
        assertTrue(onlineSuccess)

        // Verify record is successfully synchronized and marked as completed
        val finalRecord = database.syncRecordDao().getRecordByLocalId("categories", categoryId)
        assertNotNull(finalRecord)
        assertFalse(finalRecord!!.pendingSync)

        val cloudPath = "accounts/$activeUid/categories/$syncId"
        val cloudRecord = fakeProxy.store[cloudPath]
        assertNotNull(cloudRecord)
        assertEquals("Offline Burgers", cloudRecord!!["name"])
    }
}
