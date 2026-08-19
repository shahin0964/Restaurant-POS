package com.restaurant.pos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        MenuItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        UserEntity::class,
        PrinterSettingEntity::class,
        ExpenseEntity::class,
        StockLogEntity::class,
        OfferEntity::class,
        ReceiptSettingEntity::class,
        NotificationEntity::class,
        TableEntity::class,
        SyncRecordEntity::class,
        StaffFoodEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun orderDao(): OrderDao
    abstract fun userDao(): UserDao
    abstract fun printerSettingDao(): PrinterSettingDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun stockLogDao(): StockLogDao
    abstract fun offerDao(): OfferDao
    abstract fun receiptSettingDao(): ReceiptSettingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun tableDao(): TableDao
    abstract fun syncRecordDao(): SyncRecordDao
    abstract fun staffFoodDao(): StaffFoodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE menu_items ADD COLUMN stockQuantity INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'pcs'")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 10")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stock_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menuItemId` INTEGER NOT NULL,
                        `menuItemName` TEXT NOT NULL,
                        `changeAmount` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `offers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `discountType` TEXT NOT NULL,
                        `discountValue` REAL NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER NOT NULL,
                        `minOrderAmount` REAL NOT NULL DEFAULT 0.0,
                        `maxDiscountAmount` REAL NOT NULL DEFAULT 0.0,
                        `isActive` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `receipt_settings` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `shopName` TEXT NOT NULL DEFAULT '',
                        `phone` TEXT NOT NULL DEFAULT '',
                        `address` TEXT NOT NULL DEFAULT '',
                        `email` TEXT NOT NULL DEFAULT '',
                        `website` TEXT NOT NULL DEFAULT '',
                        `logoUri` TEXT NOT NULL DEFAULT '',
                        `footerText` TEXT NOT NULL DEFAULT '',
                        `currencySymbol` TEXT NOT NULL DEFAULT '৳',
                        `currencyCode` TEXT NOT NULL DEFAULT 'BDT',
                        `isTaxEnabled` INTEGER NOT NULL DEFAULT 0,
                        `taxRate` REAL NOT NULL DEFAULT 0.0,
                        `showShopName` INTEGER NOT NULL DEFAULT 1,
                        `showLogo` INTEGER NOT NULL DEFAULT 1,
                        `showPhone` INTEGER NOT NULL DEFAULT 1,
                        `showAddress` INTEGER NOT NULL DEFAULT 1,
                        `showOrderNumber` INTEGER NOT NULL DEFAULT 1,
                        `showDateTime` INTEGER NOT NULL DEFAULT 1,
                        `showCustomerName` INTEGER NOT NULL DEFAULT 1,
                        `showOrderType` INTEGER NOT NULL DEFAULT 1,
                        `showItems` INTEGER NOT NULL DEFAULT 1,
                        `showQuantity` INTEGER NOT NULL DEFAULT 1,
                        `showItemPrice` INTEGER NOT NULL DEFAULT 1,
                        `showSubtotal` INTEGER NOT NULL DEFAULT 1,
                        `showDiscount` INTEGER NOT NULL DEFAULT 1,
                        `showTax` INTEGER NOT NULL DEFAULT 1,
                        `showTotal` INTEGER NOT NULL DEFAULT 1,
                        `showPaymentStatus` INTEGER NOT NULL DEFAULT 1,
                        `showFooter` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE printer_settings ADD COLUMN connectionType TEXT NOT NULL DEFAULT 'BUILT_IN'")
                db.execSQL("ALTER TABLE printer_settings ADD COLUMN printerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE printer_settings ADD COLUMN macAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE printer_settings ADD COLUMN ipAddress TEXT NOT NULL DEFAULT '192.168.1.100'")
                db.execSQL("ALTER TABLE printer_settings ADD COLUMN port INTEGER NOT NULL DEFAULT 9100")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipt_settings ADD COLUMN currencySymbol TEXT NOT NULL DEFAULT '৳'")
                db.execSQL("ALTER TABLE receipt_settings ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'BDT'")
                db.execSQL("ALTER TABLE receipt_settings ADD COLUMN isTaxEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE receipt_settings ADD COLUMN taxRate REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `targetId` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tables` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `capacity` INTEGER NOT NULL DEFAULT 4,
                        `isActive` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE orders ADD COLUMN tableId INTEGER DEFAULT NULL")
            }
        }

        
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create sync_records table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tableName` TEXT NOT NULL,
                        `localId` INTEGER NOT NULL,
                        `firestoreId` TEXT NOT NULL,
                        `lastSyncTime` INTEGER NOT NULL,
                        `pendingSync` INTEGER NOT NULL,
                        `operation` TEXT NOT NULL,
                        `isDeleted` INTEGER NOT NULL
                    )
                """.trimIndent())
                
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_records_tableName_localId` ON `sync_records` (`tableName`, `localId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_records_firestoreId` ON `sync_records` (`firestoreId`)")
                
                // Triggers for tracking changes locally
                val tables = listOf("users", "categories", "menu_items", "orders", "order_items", "tables", "expenses", "stock_logs", "offers", "receipt_settings", "printer_settings", "notifications")
                
                for (table in tables) {
                    // Seed existing data into sync_records
                    db.execSQL("""
                        INSERT OR IGNORE INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                        SELECT '$table', id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'INSERT', 0
                        FROM `$table`
                    """.trimIndent())

                    // INSERT TRIGGER
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS `trigger_insert_$table` AFTER INSERT ON `$table`
                        BEGIN
                            INSERT INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                            VALUES ('$table', new.id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'INSERT', 0)
                            ON CONFLICT(`tableName`, `localId`) DO UPDATE SET
                                `pendingSync` = 1,
                                `operation` = 'INSERT',
                                `lastSyncTime` = strftime('%s', 'now') * 1000,
                                `isDeleted` = 0;
                        END;
                    """.trimIndent())
                    
                    // UPDATE TRIGGER
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS `trigger_update_$table` AFTER UPDATE ON `$table`
                        BEGIN
                            INSERT INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                            VALUES ('$table', new.id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'UPDATE', 0)
                            ON CONFLICT(`tableName`, `localId`) DO UPDATE SET
                                `pendingSync` = 1,
                                `operation` = 'UPDATE',
                                `lastSyncTime` = strftime('%s', 'now') * 1000,
                                `isDeleted` = 0;
                        END;
                    """.trimIndent())
                    
                    // DELETE TRIGGER
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS `trigger_delete_$table` AFTER DELETE ON `$table`
                        BEGIN
                            INSERT INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                            VALUES ('$table', old.id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'DELETE', 1)
                            ON CONFLICT(`tableName`, `localId`) DO UPDATE SET
                                `pendingSync` = 1,
                                `operation` = 'DELETE',
                                `lastSyncTime` = strftime('%s', 'now') * 1000,
                                `isDeleted` = 1;
                        END;
                    """.trimIndent())
                }
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN firebaseUid TEXT")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN permissions TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Cash'")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN costPrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE order_items ADD COLUMN costPriceAtSale REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN expenseType TEXT NOT NULL DEFAULT 'OPERATING'")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `staff_food` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `staffName` TEXT NOT NULL,
                        `productName` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPrice` REAL NOT NULL,
                        `totalPrice` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE menu_items ADD COLUMN discountEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN discountValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN discountType TEXT NOT NULL DEFAULT 'PERCENTAGE'")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tables ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun createSyncTriggers(db: SupportSQLiteDatabase) {
            val tables = listOf("users", "categories", "menu_items", "orders", "order_items", "tables", "expenses", "stock_logs", "offers", "receipt_settings", "printer_settings", "notifications")
            for (table in tables) {
                try {
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS `trigger_insert_$table` AFTER INSERT ON `$table`
                        BEGIN
                            INSERT INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                            VALUES ('$table', new.id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'INSERT', 0)
                            ON CONFLICT(`tableName`, `localId`) DO UPDATE SET
                                `pendingSync` = 1,
                                `operation` = 'INSERT',
                                `lastSyncTime` = strftime('%s', 'now') * 1000,
                                `isDeleted` = 0;
                        END;
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS `trigger_update_$table` AFTER UPDATE ON `$table`
                        BEGIN
                            INSERT INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                            VALUES ('$table', new.id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'UPDATE', 0)
                            ON CONFLICT(`tableName`, `localId`) DO UPDATE SET
                                `pendingSync` = 1,
                                `operation` = 'UPDATE',
                                `lastSyncTime` = strftime('%s', 'now') * 1000,
                                `isDeleted` = 0;
                        END;
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS `trigger_delete_$table` AFTER DELETE ON `$table`
                        BEGIN
                            INSERT INTO `sync_records` (`tableName`, `localId`, `firestoreId`, `lastSyncTime`, `pendingSync`, `operation`, `isDeleted`)
                            VALUES ('$table', old.id, lower(hex(randomblob(16))), strftime('%s', 'now') * 1000, 1, 'DELETE', 1)
                            ON CONFLICT(`tableName`, `localId`) DO UPDATE SET
                                `pendingSync` = 1,
                                `operation` = 'DELETE',
                                `lastSyncTime` = strftime('%s', 'now') * 1000,
                                `isDeleted` = 1;
                        END;
                    """.trimIndent())
                } catch (ignored: Exception) {}
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dynamic_restaurant.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        createSyncTriggers(db)
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        createSyncTriggers(db)
                    }
                })
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

