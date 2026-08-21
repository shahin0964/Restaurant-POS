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
class Step6RealtimeListenerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncRepository: RealtimeSyncRepository
    private lateinit var cloudListener: RealtimeCloudListener
    private lateinit var fakeProxy: FakeFirebaseDatabaseProxy
    private var currentUid = "user_abc_123"

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
            getUid = { currentUid }
        )

        cloudListener = RealtimeCloudListener(
            context = context,
            database = database,
            syncRepository = syncRepository,
            firebaseProxy = fakeProxy
        )

        LocalVersionTracker.clear(context)
    }

    @After
    fun teardown() {
        database.close()
        LocalVersionTracker.clear(context)
    }

    // --- 1. REMOTE INCOMING UPDATE TO ROOM ---
    @Test
    fun testIncomingNewRecordInsertsToRoom() = runBlocking {
        val categoryMap = mapOf(
            "syncId" to "cat-111",
            "name" to "Appetizers",
            "itemCount" to 0,
            "iconName" to "star",
            "imageUrl" to "https://logo.com/app.png",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 1000L
        )

        // Simulate incoming child snapshot event
        val job = cloudListener.handleSnapshotMap("categories", "cat-111", categoryMap, currentUid)
        job.join()

        // Verify Category was written to Room
        val categories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, categories.size)
        assertEquals("Appetizers", categories[0].name)
        assertEquals("https://logo.com/app.png", categories[0].imageUrl)

        // Verify sync_records entry exists with pendingSync = false
        val syncRecord = database.syncRecordDao().getRecordByFirestoreId("categories", "cat-111")
        assertNotNull(syncRecord)
        assertFalse(syncRecord!!.pendingSync)
        assertEquals(1000L, syncRecord.lastSyncTime)
    }

    // --- 2. CONFLICT SAFETY / TIE-BREAKER LOGIC ---
    @Test
    fun testConflictSafetyVersionsAndTieBreaker() = runBlocking {
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Original Local", iconName = "star"))
        val syncId = "cat-222"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            lastSyncTime = 500L,
            pendingSync = false
        ))
        LocalVersionTracker.setLocalVersion(context, "categories", syncId, 2L)

        // Scenario A: Cloud version is older (1L < 2L) -> MUST IGNORE
        val olderCloudMap = mapOf(
            "syncId" to syncId,
            "name" to "Older Cloud",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 1000L
        )
        val jobA = cloudListener.handleSnapshotMap("categories", syncId, olderCloudMap, currentUid)
        jobA.join()

        assertEquals("Original Local", database.categoryDao().getAllCategoriesSync()[0].name)

        // Scenario B: Cloud version is same (2L == 2L) but older timestamp (400L <= 500L) -> MUST IGNORE
        val olderTimestampMap = mapOf(
            "syncId" to syncId,
            "name" to "Older Timestamp Cloud",
            "version" to 2L,
            "isDeleted" to false,
            "lastChanged" to 400L
        )
        val jobB = cloudListener.handleSnapshotMap("categories", syncId, olderTimestampMap, currentUid)
        jobB.join()

        assertEquals("Original Local", database.categoryDao().getAllCategoriesSync()[0].name)

        // Scenario C: Cloud version is same (2L == 2L) but newer timestamp (600L > 500L) -> MUST ACCEPT
        val newerTimestampMap = mapOf(
            "syncId" to syncId,
            "name" to "Newer Timestamp Cloud",
            "version" to 2L,
            "isDeleted" to false,
            "lastChanged" to 600L
        )
        val jobC = cloudListener.handleSnapshotMap("categories", syncId, newerTimestampMap, currentUid)
        jobC.join()

        assertEquals("Newer Timestamp Cloud", database.categoryDao().getAllCategoriesSync()[0].name)

        // Scenario D: Cloud version is newer (3L > 2L) -> MUST ACCEPT
        val newerCloudMap = mapOf(
            "syncId" to syncId,
            "name" to "Newer Version Cloud",
            "version" to 3L,
            "isDeleted" to false,
            "lastChanged" to 100L
        )
        val jobD = cloudListener.handleSnapshotMap("categories", syncId, newerCloudMap, currentUid)
        jobD.join()

        assertEquals("Newer Version Cloud", database.categoryDao().getAllCategoriesSync()[0].name)
    }

    // --- 3. RELATIONSHIP-SAFE CONSTRAINTS (RECURSIVE PARENT RETRIEVAL) ---
    @Test
    fun testRelationshipSafeDownloadParentBeforeChild() = runBlocking {
        // Save Category in the fake remote cloud database proxy
        val remoteCategory = mapOf(
            "syncId" to "parent-cat-999",
            "name" to "Gourmet Drinks",
            "itemCount" to 0,
            "iconName" to "glass",
            "imageUrl" to "https://logo.com/drink.png",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 2000L
        )
        fakeProxy.store["accounts/$currentUid/categories/parent-cat-999"] = remoteCategory

        // Send MenuItem referencing the above category sync ID
        val menuItemMap = mapOf(
            "syncId" to "menu-item-999",
            "name" to "Organic Iced Tea",
            "categorySyncId" to "parent-cat-999",
            "categoryName" to "Gourmet Drinks",
            "price" to 4.99,
            "description" to "Fresh organic herbal tea",
            "imageUrl" to "",
            "isAvailable" to true,
            "stockQuantity" to 100,
            "unit" to "cups",
            "version" to 1L,
            "isDeleted" to false,
            "lastChanged" to 2050L
        )

        // Trigger Menu Item download first
        val job = cloudListener.handleSnapshotMap("menu_items", "menu-item-999", menuItemMap, currentUid)
        job.join()

        // Verify that Category was recursively fetched, reconciled, and written to Room before the MenuItem
        val localCategories = database.categoryDao().getAllCategoriesSync()
        assertEquals(1, localCategories.size)
        assertEquals("Gourmet Drinks", localCategories[0].name)

        // Verify that Menu Item was successfully mapped and inserted using the dynamically resolved local category ID
        val localItems = database.menuItemDao().getAllMenuItemsSync()
        assertEquals(1, localItems.size)
        assertEquals("Organic Iced Tea", localItems[0].name)
        assertEquals(localCategories[0].id, localItems[0].categoryId)
    }

    // --- 4. TOMBSTONE RECORD HANDLING ---
    @Test
    fun testTombstoneDeletionHandling() = runBlocking {
        // Insert a local item that exists
        val categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "To Be Deleted", iconName = "star"))
        val syncId = "cat-tomb-888"
        database.syncRecordDao().insertOrUpdate(SyncRecordEntity(
            tableName = "categories",
            localId = categoryId,
            firestoreId = syncId,
            lastSyncTime = 100L,
            pendingSync = false
        ))

        // Simulate a cloud delete event (tombstone isDeleted = true)
        val cloudTombstone = mapOf(
            "syncId" to syncId,
            "version" to 2L,
            "isDeleted" to true,
            "lastChanged" to 150L
        )

        val job = cloudListener.handleSnapshotMap("categories", syncId, cloudTombstone, currentUid)
        job.join()

        // Verify that the local category record was deleted from Room
        val remainingCategories = database.categoryDao().getAllCategoriesSync()
        assertTrue(remainingCategories.isEmpty())

        // Verify that the mapping record was marked as isDeleted=true, pendingSync=false to prevent redundant cloud sync loop
        val finalRecord = database.syncRecordDao().getRecordByFirestoreId("categories", syncId)
        assertNotNull(finalRecord)
        assertTrue(finalRecord!!.isDeleted)
        assertFalse(finalRecord.pendingSync)
    }
}
