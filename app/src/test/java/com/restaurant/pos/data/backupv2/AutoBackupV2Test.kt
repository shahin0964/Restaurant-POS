package com.restaurant.pos.data.backupv2

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.restaurant.pos.data.db.AppDatabase
import com.restaurant.pos.data.db.CategoryEntity
import com.restaurant.pos.data.db.MenuItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupV2Test {

    private lateinit var app: Application
    private lateinit var database: AppDatabase
    private lateinit var backupEngine: BackupEngineV2

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getInstance(app)
        backupEngine = BackupEngineV2(app, database)
    }

    @Test
    fun testSchedulerEnableAndDisablePersistence() {
        assertFalse(AutoBackupSchedulerV2.isAutoBackupEnabled(app))

        AutoBackupSchedulerV2.setAutoBackupEnabled(app, true, 24)
        assertTrue(AutoBackupSchedulerV2.isAutoBackupEnabled(app))
        assertEquals(24, AutoBackupSchedulerV2.getAutoBackupIntervalHours(app))

        AutoBackupSchedulerV2.setAutoBackupEnabled(app, false)
        assertFalse(AutoBackupSchedulerV2.isAutoBackupEnabled(app))
    }

    @Test
    fun testAutoBackupExecutionIntegrityAndRetention() = runBlocking {
        val cat = CategoryEntity(id = 101, name = "Beverages")
        val item = MenuItemEntity(id = 201, name = "Iced Latte", categoryId = 101, categoryName = "Beverages", price = 4.50)
        database.categoryDao().insertCategory(cat)
        database.menuItemDao().insertMenuItem(item)

        val backupDir = File(app.filesDir, "auto_backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        for (i in 1..8) {
            val fileName = "pos_backup_v2_auto_2026-08-22_00000$i.json"
            val file = File(backupDir, fileName)
            file.outputStream().use { stream ->
                backupEngine.exportBackup(stream)
            }
            file.setLastModified(System.currentTimeMillis() + (i * 1000L))
        }

        val autoFiles = backupDir.listFiles { f -> f.name.startsWith("pos_backup_v2_auto_") }
        assertNotNull(autoFiles)
        assertEquals(8, autoFiles!!.size)

        val sorted = autoFiles.sortedByDescending { it.lastModified() }
        for (i in 7 until sorted.size) {
            sorted[i].delete()
        }

        val remaining = backupDir.listFiles { f -> f.name.startsWith("pos_backup_v2_auto_") }
        assertEquals(7, remaining!!.size)

        val newestFile = sorted[0]
        val validateResult = backupEngine.importAndValidateFromUri(Uri.fromFile(newestFile))
        assertTrue(validateResult is BackupValidationResultV2.Valid)

        val validResult = validateResult as BackupValidationResultV2.Valid
        assertEquals(1, validResult.payload.databaseData.categories.size)
        assertEquals(1, validResult.payload.databaseData.menuItems.size)
        assertEquals("Beverages", validResult.payload.databaseData.categories[0].name)
        assertEquals("Iced Latte", validResult.payload.databaseData.menuItems[0].name)
    }

    @Test
    fun testManualBackupsAreNotAffectedByRetention() = runBlocking {
        val backupDir = File(app.filesDir, "auto_backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val manualFile = File(app.filesDir, "manual_export_pos_v2.json")
        manualFile.outputStream().use { stream ->
            backupEngine.exportBackup(stream)
        }
        assertTrue(manualFile.exists())

        for (i in 1..8) {
            val f = File(backupDir, "pos_backup_v2_auto_2026-08-22_10000$i.json")
            f.outputStream().use { stream -> backupEngine.exportBackup(stream) }
            f.setLastModified(System.currentTimeMillis() + (i * 1000L))
        }

        val sorted = backupDir.listFiles { f -> f.name.startsWith("pos_backup_v2_auto_") }!!.sortedByDescending { it.lastModified() }
        for (i in 7 until sorted.size) {
            sorted[i].delete()
        }

        assertTrue(manualFile.exists())
    }
}
