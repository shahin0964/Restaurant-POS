package com.restaurant.pos.data.backupv2

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.restaurant.pos.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupEngineV2Test {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var engine: BackupEngineV2

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = BackupEngineV2(context, db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testExportImportAndRestoreCoreEngine() = runBlocking {
        // 1. Seed initial database data
        val category = CategoryEntity(id = 15, name = "Beverages", itemCount = 2, iconName = "drink", imageUrl = "category_images/cat_15.jpg")
        val menuItem = MenuItemEntity(
            id = 101,
            name = "Coffee",
            categoryId = 15,
            categoryName = "Beverages",
            price = 3.50,
            costPrice = 1.00,
            discountEnabled = true,
            discountValue = 10.0,
            discountType = "PERCENTAGE"
        )
        val user = UserEntity(
            id = 5,
            emailOrPhone = "admin@pos.com",
            name = "Admin User",
            role = "Administrator",
            passwordHash = "hash123",
            permissions = "all"
        )
        val order = OrderEntity(
            id = 501,
            orderNumber = "#1001",
            orderType = "Dine In",
            tableNumber = "T-1",
            customerName = "John Doe",
            note = "Extra hot",
            subtotal = 3.50,
            discount = 0.35,
            tax = 0.25,
            total = 3.40,
            paymentMethod = "Cash",
            isPaid = true,
            status = "Completed",
            tableId = 1
        )
        val orderItem = OrderItemEntity(
            id = 1001,
            orderId = 501,
            menuItemId = 101,
            menuItemName = "Coffee",
            quantity = 1,
            pricePerUnit = 3.50,
            note = "Skim milk",
            costPriceAtSale = 1.00
        )

        db.categoryDao().insertCategory(category)
        db.menuItemDao().insertMenuItem(menuItem)
        db.userDao().insertUser(user)
        db.orderDao().insertOrder(order)
        db.orderDao().insertOrderItems(listOf(orderItem))

        // Seed preference
        val sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sp.edit().putString("language", "bn").putString("opening_cash", "500.0").apply()

        // 2. Perform Export
        val outputStream = ByteArrayOutputStream()
        val exportResult = engine.exportBackup(outputStream)

        assertTrue(exportResult is BackupExportResultV2.Success)
        val successExport = exportResult as BackupExportResultV2.Success
        assertEquals(19, successExport.metadata.dbVersion)
        assertEquals(1, successExport.metadata.formatVersion)

        val exportedBytes = outputStream.toByteArray()
        assertTrue(exportedBytes.isNotEmpty())

        // 3. Perform Import & Validation
        val inputStream = ByteArrayInputStream(exportedBytes)
        val validationResult = engine.importAndValidate(inputStream)

        assertTrue(validationResult is BackupValidationResultV2.Valid)
        val validImport = validationResult as BackupValidationResultV2.Valid
        val payload = validImport.payload

        assertEquals(1, payload.databaseData.categories.size)
        assertEquals(15L, payload.databaseData.categories[0].id)
        assertEquals("Beverages", payload.databaseData.categories[0].name)
        assertEquals(101L, payload.databaseData.menuItems[0].id)
        assertEquals(501L, payload.databaseData.orders[0].id)
        assertEquals(1001L, payload.databaseData.orderItems[0].id)
        assertEquals(1.00, payload.databaseData.orderItems[0].costPriceAtSale, 0.001)

        // 4. Perform Restore
        // Clear DB manually to ensure restore re-populates it from payload
        db.orderDao().clearAllOrderItems()
        db.orderDao().clearAllOrders()
        db.menuItemDao().clearAll()
        db.categoryDao().clearAll()
        db.userDao().clearAllUsers()

        val restoreResult = engine.restoreBackup(payload)
        assertTrue(restoreResult is BackupRestoreResultV2.Success)

        // 5. Verify restored DB state & exact ID preservation
        val restoredCategories = db.categoryDao().getAllCategoriesSync()
        val restoredMenuItems = db.menuItemDao().getAllMenuItemsSync()
        val restoredOrders = db.orderDao().getAllOrderEntities()
        val restoredOrderItems = db.orderDao().getAllOrderItemEntities()

        assertEquals(1, restoredCategories.size)
        assertEquals(15L, restoredCategories[0].id)
        assertEquals("Beverages", restoredCategories[0].name)

        assertEquals(1, restoredMenuItems.size)
        assertEquals(101L, restoredMenuItems[0].id)
        assertEquals(15L, restoredMenuItems[0].categoryId)

        assertEquals(1, restoredOrders.size)
        assertEquals(501L, restoredOrders[0].id)
        assertEquals("#1001", restoredOrders[0].orderNumber)

        assertEquals(1, restoredOrderItems.size)
        assertEquals(1001L, restoredOrderItems[0].id)
        assertEquals(501L, restoredOrderItems[0].orderId)
        assertEquals(101L, restoredOrderItems[0].menuItemId)

        // Verify restored preferences
        val restoredSp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        assertEquals("bn", restoredSp.getString("language", ""))
        assertEquals("500.0", restoredSp.getString("opening_cash", ""))
    }

    @Test
    fun testCorruptedBackupValidationRejection() {
        val corruptedJson = "{ \"metadata\": { \"formatVersion\": 999 } }"
        val inputStream = ByteArrayInputStream(corruptedJson.toByteArray())
        val validationResult = engine.importAndValidate(inputStream)

        assertTrue(validationResult is BackupValidationResultV2.Invalid)
        val invalidResult = validationResult as BackupValidationResultV2.Invalid
        assertTrue(invalidResult.reason.contains("Unsupported backup format version"))
    }
}
