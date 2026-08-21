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
class Step7AccountReconciliationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncRepository: RealtimeSyncRepository
    private lateinit var cloudListener: RealtimeCloudListener
    private lateinit var reconciliationManager: AccountReconciliationManager
    private lateinit var fakeProxy: FakeFirebaseDatabaseProxy
    private var activeUid = "user_abc_111"

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

    // --- 1. NEW DEVICE SCENARIO (EMPTY LOCAL, POPULATED CLOUD) ---
    @Test
    fun testOnboardingFromEmptyLocalRoom() = runBlocking {
        // Setup cloud data for current user
        val categoryPath = "accounts/$activeUid/categories/cat-123"
        val categoryData = mapOf(
            "syncId" to "cat-123",
            "name" to "Gourmet Burgers",
            "itemCount" to 0,
            "iconName" to "burger",
            "imageUrl" to "https://img.com/burger.jpg",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 1000L
        )
        fakeProxy.store[categoryPath] = categoryData

        // Verify local Room is empty initially
        assertTrue(database.categoryDao().getAllCategoriesSync().isEmpty())

        // Run full reconciliation
        reconciliationManager.initializeAndReconcile(activeUid)

        // Verify Room is populated with Gourmet Burgers
        val localCategories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, localCategories.size)
        assertEquals("Gourmet Burgers", localCategories[0].name)
        assertEquals("https://img.com/burger.jpg", localCategories[0].imageUrl)

        // Verify sync_records entry
        val syncRecord = database.syncRecordDao().getRecordByFirestoreId("categories", "cat-123")
        assertNotNull(syncRecord)
        assertFalse(syncRecord!!.pendingSync)
        assertEquals(1000L, syncRecord.lastSyncTime)
    }

    // --- 2. ACCOUNT DATA ISOLATION (WIPE UNRELATED OLD ACCOUNT DATA ON SWITCH) ---
    @Test
    fun testSwitchingAccountsCompletelyWipesOldLocalData() = runBlocking {
        // 1. Setup local database with User A's data
        val oldCategoryLocalId = database.categoryDao().insertCategory(CategoryEntity(name = "User A Category"))
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = oldCategoryLocalId,
            firestoreId = "cat-user-a",
            lastSyncTime = 500L,
            pendingSync = false
        ))
        // Store User A as last reconciled UID in prefs
        context.getSharedPreferences("pos_sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_reconciled_uid", "user_A_999")
            .apply()

        // 2. Setup User B's distinct cloud data
        activeUid = "user_B_888" // We switch the active user to User B
        val categoryPath = "accounts/$activeUid/categories/cat-user-b"
        val categoryData = mapOf(
            "syncId" to "cat-user-b",
            "name" to "User B Pizza",
            "itemCount" to 0,
            "iconName" to "pizza",
            "imageUrl" to "",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 1200L
        )
        fakeProxy.store[categoryPath] = categoryData

        // 3. Trigger reconciliation for User B
        reconciliationManager.initializeAndReconcile(activeUid)

        // 4. Verify User A's local Category has been completely deleted/wiped
        val allCategories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, allCategories.size)
        assertEquals("User B Pizza", allCategories[0].name)

        // Verify old mapping is gone
        val oldRecord = database.syncRecordDao().getRecordByFirestoreId("categories", "cat-user-a")
        assertNull(oldRecord)

        // Verify new mapping is created
        val newRecord = database.syncRecordDao().getRecordByFirestoreId("categories", "cat-user-b")
        assertNotNull(newRecord)
        assertFalse(newRecord!!.pendingSync)
    }

    // --- 3. EXTRA LOCAL RECORDS RESOLUTION (DELETED REMOTELY) ---
    @Test
    fun testResolvesExtraSyncedLocalRecordNotPresentInCloud() = runBlocking {
        // 1. Store a synced category locally
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Remotely Deleted Category"))
        val syncId = "cat-extra-999"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            lastSyncTime = 200L,
            pendingSync = false
        ))

        // 2. Reconciliation has empty cloud
        reconciliationManager.initializeAndReconcile(activeUid)

        // 3. Verify local category is deleted
        val remainingCategories = database.categoryDao().getAllCategoriesSync()
        assertTrue(remainingCategories.isEmpty())

        // 4. Verify sync record is marked isDeleted = true, pendingSync = false
        val finalRecord = database.syncRecordDao().getRecordByFirestoreId("categories", syncId)
        assertNotNull(finalRecord)
        assertTrue(finalRecord!!.isDeleted)
        assertFalse(finalRecord.pendingSync)
    }

    // --- 4. PRESERVE LOCAL PENDING CHANGES ---
    @Test
    fun testPreservesValidLocalPendingChangesOnReconciliation() = runBlocking {
        // 1. Local category has pending unsynced changes
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Pending Local Dessert"))
        val syncId = "cat-pending-111"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            lastSyncTime = 1500L, // local timestamp is newer
            pendingSync = true,
            operation = "UPDATE"
        ))

        // 2. Cloud has stale/older category with same syncId
        val categoryPath = "accounts/$activeUid/categories/cat-pending-111"
        val cloudStaleData = mapOf(
            "syncId" to syncId,
            "name" to "Old Stale Cloud Dessert",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 1000L // cloud is older (1000L < 1500L)
        )
        fakeProxy.store[categoryPath] = cloudStaleData

        // 3. Run reconciliation
        reconciliationManager.initializeAndReconcile(activeUid)

        // 4. Verify local pending dessert is preserved and not overwritten
        val allCategories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, allCategories.size)
        assertEquals("Pending Local Dessert", allCategories[0].name)

        val finalRecord = database.syncRecordDao().getRecordByFirestoreId("categories", syncId)
        assertNotNull(finalRecord)
        assertTrue(finalRecord!!.pendingSync) // still pending
    }
}
