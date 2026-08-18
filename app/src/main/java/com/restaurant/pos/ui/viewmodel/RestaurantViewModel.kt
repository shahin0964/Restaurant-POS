package com.restaurant.pos.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.pos.data.backup.*
import com.restaurant.pos.data.db.*
import com.restaurant.pos.data.network.NetworkConnectivityObserver
import com.restaurant.pos.data.sync.CloudSyncManager
import com.restaurant.pos.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed class BackupOpState {
    object Idle : BackupOpState()
    data class Progress(val message: String) : BackupOpState()
    data class Success(val title: String, val detail: String) : BackupOpState()
    data class Error(val message: String) : BackupOpState()
}

class RestaurantViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _appLanguage = MutableStateFlow(sharedPrefs.getString("language", "en") ?: "en")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme", "system") ?: "system")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _openingCash = MutableStateFlow(
        sharedPrefs.getString("opening_cash", "0.0")?.toDoubleOrNull() ?: 0.0
    )
    val openingCash: StateFlow<Double> = _openingCash.asStateFlow()

    fun setOpeningCash(amount: Double) {
        val cleanAmount = if (amount < 0.0) 0.0 else amount
        sharedPrefs.edit().putString("opening_cash", cleanAmount.toString()).apply()
        _openingCash.value = cleanAmount
    }

    init {
        applyLocale(application, _appLanguage.value)
    }

    fun setAppLanguage(languageCode: String) {
        sharedPrefs.edit().putString("language", languageCode).apply()
        _appLanguage.value = languageCode
        applyLocale(getApplication(), languageCode)
    }

    fun setAppTheme(themeCode: String) {
        sharedPrefs.edit().putString("app_theme", themeCode).apply()
        _appTheme.value = themeCode
    }

    private fun forceCloudSync() {
        cloudSyncManager.syncNow()
    }

    fun applyLocale(context: Context, languageCode: String) {
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        
        val appContext = context.applicationContext
        if (appContext != null && appContext != context) {
            val appResources = appContext.resources
            val appConfig = appResources.configuration
            appConfig.setLocale(locale)
            appResources.updateConfiguration(appConfig, appResources.displayMetrics)
        }
    }

    private val database = AppDatabase.getInstance(application)
    val notificationRepo = NotificationRepository(database.notificationDao(), application)
    val authRepo = AuthRepository(application.applicationContext, database.userDao(), database.syncRecordDao())
    private val networkObserver = NetworkConnectivityObserver(application)
    private val cloudSyncManager = CloudSyncManager(application.applicationContext, database, networkObserver)
    val restaurantRepo = RestaurantRepository(
        database.categoryDao(),
        database.menuItemDao(),
        database.orderDao(),
        database.expenseDao(),
        database.stockLogDao(),
        notificationRepo,
        database.tableDao()
    )
    val printerRepo = PrinterRepository(application, database.printerSettingDao(), database.receiptSettingDao())
    val updateRepo = GitHubUpdateRepository(application)

    val allNotifications: StateFlow<List<NotificationEntity>> = notificationRepo.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unreadNotificationCount: StateFlow<Int> = notificationRepo.unreadCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch {
            notificationRepo.markAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            notificationRepo.markAllAsRead()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            notificationRepo.deleteNotification(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            notificationRepo.clearAll()
        }
    }

    fun isNotificationCategoryEnabled(categoryKey: String): Boolean {
        return notificationRepo.isCategoryEnabled(categoryKey)
    }

    fun setNotificationCategoryEnabled(categoryKey: String, enabled: Boolean) {
        notificationRepo.setCategoryEnabled(categoryKey, enabled)
    }

    val receiptSetting: StateFlow<ReceiptSettingEntity?> = printerRepo.receiptSetting.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun saveReceiptSetting(setting: ReceiptSettingEntity) {
        viewModelScope.launch {
            printerRepo.saveReceiptSetting(setting)
            forceCloudSync()
        }
    }

    val allStockLogs: StateFlow<List<StockLogEntity>> = restaurantRepo.allStockLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun getStockLogsForMenuItem(menuItemId: Long): Flow<List<StockLogEntity>> {
        return restaurantRepo.getStockLogsForMenuItem(menuItemId)
    }

    fun updateItemStock(
        menuItemId: Long,
        newQuantity: Int,
        unit: String? = null,
        lowStockThreshold: Int? = null,
        reasonNote: String = ""
    ) {
        viewModelScope.launch {
            restaurantRepo.updateItemStock(menuItemId, newQuantity, unit, lowStockThreshold, reasonNote)
            forceCloudSync()
        }
    }

    // Auth State
    val currentUser: StateFlow<UserEntity?> = authRepo.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allUsers: StateFlow<List<UserEntity>> = authRepo.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveStaffUser(
        id: Long = 0,
        name: String,
        emailOrPhone: String,
        role: String,
        password: String,
        isActive: Boolean,
        permissions: String = "",
        onResult: (Result<UserEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val currentUserId = currentUser.value?.id
            val result = authRepo.saveStaffUser(
                id = id,
                name = name,
                emailOrPhone = emailOrPhone,
                role = role,
                password = password,
                isActive = isActive,
                permissions = permissions,
                currentUserId = currentUserId
            )
            forceCloudSync()
            onResult(result)
        }
    }

    fun hasPermission(permission: com.restaurant.pos.data.model.AppPermission): Boolean {
        return currentUser.value?.hasPermission(permission) ?: false
    }

    fun hasPermission(permissionKey: String): Boolean {
        return currentUser.value?.hasPermission(permissionKey) ?: false
    }

    fun isCurrentUserAdmin(): Boolean {
        return currentUser.value?.isAdmin() ?: false
    }

    fun deleteStaffUser(user: UserEntity, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val currentUserId = currentUser.value?.id
            val result = authRepo.deleteStaffUser(user, currentUserId)
            forceCloudSync()
            onResult(result)
        }
    }


    val allExpenses: StateFlow<List<ExpenseEntity>> = restaurantRepo.allExpenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addExpense(title: String, amount: Double, category: String, note: String = "", paymentMethod: String = "Cash", expenseType: String = "OPERATING") {
        viewModelScope.launch {
            restaurantRepo.addExpense(title, amount, category, note, paymentMethod, expenseType)
            forceCloudSync()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            restaurantRepo.deleteExpense(expense)
            forceCloudSync()
        }
    }

    fun saveMenuItem(
        id: Long = 0,
        name: String,
        categoryName: String,
        price: Double,
        costPrice: Double = 0.0,
        description: String,
        imageUrl: String,
        isAvailable: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            restaurantRepo.saveMenuItem(
                id = id,
                name = name,
                categoryName = categoryName,
                price = price,
                costPrice = costPrice,
                description = description,
                imageUrl = imageUrl,
                isAvailable = isAvailable
            )
            forceCloudSync()
            onComplete()
        }
    }

    fun deleteMenuItem(item: MenuItemEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            restaurantRepo.deleteMenuItem(item)
            forceCloudSync()
            onComplete()
        }
    }

    fun saveCategory(category: CategoryEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            restaurantRepo.saveCategory(category)
            forceCloudSync()
            onComplete()
        }
    }

    fun deleteCategory(category: CategoryEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            restaurantRepo.deleteCategory(category)
            forceCloudSync()
            onComplete()
        }
    }

    fun saveImageToInternalStorage(uri: android.net.Uri): String {
        return try {
            val context = getApplication<Application>().applicationContext
            val imagesDir = java.io.File(context.filesDir, "item_images").apply { if (!exists()) mkdirs() }
            val file = java.io.File(imagesDir, "item_${System.currentTimeMillis()}.jpg")

            // Decode image bounds to calculate optimal sample size
            var inputStream = context.contentResolver.openInputStream(uri)
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val maxDimension = 800
            var sampleSize = 1
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth > maxDimension || origHeight > maxDimension) {
                val halfWidth = origWidth / 2
                val halfHeight = origHeight / 2
                while ((halfWidth / sampleSize) >= maxDimension && (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            // Decode sampled bitmap to prevent out of memory
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap != null) {
                var finalBitmap = bitmap
                try {
                    val exifInputStream = context.contentResolver.openInputStream(uri)
                    if (exifInputStream != null) {
                        val exif = android.media.ExifInterface(exifInputStream)
                        val orientation = exif.getAttributeInt(
                            android.media.ExifInterface.TAG_ORIENTATION,
                            android.media.ExifInterface.ORIENTATION_NORMAL
                        )
                        val matrix = android.graphics.Matrix()
                        when (orientation) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                        }
                        if (!matrix.isIdentity) {
                            finalBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        exifInputStream.close()
                    }
                } catch (_: Exception) {
                    // Fallback to decoded bitmap
                }

                java.io.FileOutputStream(file).use { outputStream ->
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                    outputStream.flush()
                }
                if (finalBitmap != bitmap) {
                    bitmap.recycle()
                }
                file.absolutePath
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    java.io.FileOutputStream(file).use { out ->
                        stream.copyTo(out)
                    }
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri.toString()
        }
    }

    // Data Flows
    val categories: StateFlow<List<CategoryEntity>> = restaurantRepo.categories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val menuItems: StateFlow<List<MenuItemEntity>> = restaurantRepo.menuItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allOrders: StateFlow<List<OrderWithItems>> = restaurantRepo.allOrders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val pendingOrders: StateFlow<List<OrderWithItems>> = restaurantRepo.pendingOrders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val preparingOrders: StateFlow<List<OrderWithItems>> = restaurantRepo.preparingOrders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val readyOrders: StateFlow<List<OrderWithItems>> = restaurantRepo.readyOrders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalOrdersCount: StateFlow<Int> = restaurantRepo.totalOrdersCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val totalSalesAmount: StateFlow<Double?> = restaurantRepo.totalSalesAmount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val printerSetting: StateFlow<PrinterSettingEntity?> = printerRepo.printerSetting.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrinterSettingEntity()
    )

    val allTables: StateFlow<List<TableEntity>> = restaurantRepo.allTables.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedTableId = MutableStateFlow<Long?>(null)
    val selectedTableId: StateFlow<Long?> = _selectedTableId.asStateFlow()

    fun setSelectedTableId(id: Long?) {
        _selectedTableId.value = id
    }

    fun getActiveOrderForTable(table: TableEntity, ordersList: List<OrderWithItems>): OrderWithItems? {
        val tableNameClean = table.name.trim().lowercase()
        return ordersList.firstOrNull { orderWithItems ->
            val o = orderWithItems.order
            val isDineIn = o.orderType.lowercase().contains("dine")
            val isActive = o.status != "Completed" && o.status != "Cancelled"
            if (!isDineIn || !isActive) return@firstOrNull false

            val orderTableClean = o.tableNumber.trim().lowercase()
            val matchesId = o.tableId != null && o.tableId == table.id
            val matchesName = orderTableClean == tableNameClean ||
                    orderTableClean == "table $tableNameClean" ||
                    tableNameClean == "table $orderTableClean" ||
                    (tableNameClean.replace("table", "").trim().isNotBlank() &&
                     tableNameClean.replace("table", "").trim() == orderTableClean.replace("table", "").trim())

            matchesId || matchesName
        }
    }

    fun addTable(name: String, capacity: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (name.isBlank()) {
            onError("Table name cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                restaurantRepo.addTable(name.trim(), capacity)
                forceCloudSync()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add table")
            }
        }
    }

    fun updateTable(table: TableEntity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (table.name.isBlank()) {
            onError("Table name cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                restaurantRepo.updateTable(table)
                forceCloudSync()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update table")
            }
        }
    }

    fun deleteTable(table: TableEntity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val activeOrders = allOrders.value
        val activeOrder = getActiveOrderForTable(table, activeOrders)
        if (activeOrder != null) {
            onError("This table has an active order and cannot be deleted.")
            return
        }
        viewModelScope.launch {
            try {
                restaurantRepo.deleteTable(table.id)
                if (_selectedTableId.value == table.id) {
                    _selectedTableId.value = null
                }
                forceCloudSync()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete table")
            }
        }
    }

    // Cart & Order In Progress State
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _orderType = MutableStateFlow("Dine In") // "Dine In", "Take Away", "Delivery"
    val orderType: StateFlow<String> = _orderType.asStateFlow()

    private val _tableNumber = MutableStateFlow("Table 05")
    val tableNumber: StateFlow<String> = _tableNumber.asStateFlow()

    private val _customerName = MutableStateFlow("Walk-in Customer")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _orderNote = MutableStateFlow("")
    val orderNote: StateFlow<String> = _orderNote.asStateFlow()

    private val _discount = MutableStateFlow(40.0)
    val discount: StateFlow<Double> = _discount.asStateFlow()

    val tax: StateFlow<Double> = combine(_cartItems, _discount, receiptSetting) { cart, disc, setting ->
        if (setting == null || !setting.isTaxEnabled || setting.taxRate <= 0.0) {
            0.0
        } else {
            val sub = cart.sumOf { it.menuItem.price * it.quantity }
            val net = (sub - disc).coerceAtLeast(0.0)
            val calc = net * (setting.taxRate / 100.0)
            if (calc.isNaN() || calc.isInfinite() || calc < 0.0) 0.0 else calc
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    private val _selectedOrderForDetails = MutableStateFlow<OrderWithItems?>(null)
    val selectedOrderForDetails: StateFlow<OrderWithItems?> = _selectedOrderForDetails.asStateFlow()

    private val _selectedMenuItem = MutableStateFlow<MenuItemEntity?>(null)
    val selectedMenuItem: StateFlow<MenuItemEntity?> = _selectedMenuItem.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.seedDefaultUserIfNeeded()
            val hasClearedLegacy = sharedPrefs.getBoolean("has_removed_existing_products_and_categories_v1", false)
            if (!hasClearedLegacy) {
                restaurantRepo.clearExistingProductsAndCategories()
                sharedPrefs.edit().putBoolean("has_removed_existing_products_and_categories_v1", true).apply()
            }
            restaurantRepo.seedDatabaseIfNeeded()
        }
    }

    fun setOrderType(type: String) { _orderType.value = type }
    fun setTableNumber(table: String) { _tableNumber.value = table }
    fun setCustomerName(name: String) { _customerName.value = name }
    fun setOrderNote(note: String) { _orderNote.value = note }
    fun setDiscount(disc: Double) { _discount.value = disc }
    fun setSelectedMenuItem(item: MenuItemEntity?) { _selectedMenuItem.value = item }
    fun setSelectedOrderDetails(order: OrderWithItems?) { _selectedOrderForDetails.value = order }

    fun addToCart(item: MenuItemEntity, quantity: Int = 1) {
        val current = _cartItems.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.menuItem.id == item.id }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            current.add(CartItem(menuItem = item, quantity = quantity))
        }
        _cartItems.value = current
    }

    fun updateCartQuantity(item: MenuItemEntity, newQty: Int) {
        if (newQty <= 0) {
            _cartItems.value = _cartItems.value.filter { it.menuItem.id != item.id }
        } else {
            val current = _cartItems.value.toMutableList()
            val index = current.indexOfFirst { it.menuItem.id == item.id }
            if (index >= 0) {
                current[index] = current[index].copy(quantity = newQty)
                _cartItems.value = current
            }
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    fun calculateSubtotal(): Double {
        return _cartItems.value.sumOf { it.menuItem.price * it.quantity }
    }

    fun calculateTax(): Double {
        val setting = receiptSetting.value
        if (setting == null || !setting.isTaxEnabled || setting.taxRate <= 0.0) return 0.0
        val sub = calculateSubtotal()
        val net = (sub - _discount.value).coerceAtLeast(0.0)
        val calc = net * (setting.taxRate / 100.0)
        return if (calc.isNaN() || calc.isInfinite() || calc < 0.0) 0.0 else calc
    }

    fun calculateTotal(): Double {
        val sub = calculateSubtotal()
        val taxVal = calculateTax()
        val total = sub - _discount.value + taxVal
        return if (total < 0) 0.0 else total
    }

    fun placeOrder(paymentMethod: String, onOrderPlaced: (Long) -> Unit) {
        viewModelScope.launch {
            val subtotal = calculateSubtotal()
            val taxVal = calculateTax()
            val total = calculateTotal()
            val selectedTId = if (_orderType.value.lowercase().contains("dine")) _selectedTableId.value else null
            val orderId = restaurantRepo.createOrder(
                orderType = _orderType.value,
                tableNumber = _tableNumber.value,
                customerName = _customerName.value,
                note = _orderNote.value,
                subtotal = subtotal,
                discount = _discount.value,
                tax = taxVal,
                total = total,
                paymentMethod = paymentMethod,
                cartItems = _cartItems.value,
                tableId = selectedTId
            )
            val orderWithItems = restaurantRepo.getOrderById(orderId)
            if (orderWithItems != null && (printerSetting.value?.autoPrintOnOrder == true)) {
                printerRepo.printOrderReceipt(orderWithItems)
            }
            clearCart()
            _selectedOrderForDetails.value = orderWithItems
            forceCloudSync()
            onOrderPlaced(orderId)
        }
    }

    fun updateOrderStatus(orderId: Long, newStatus: String) {
        viewModelScope.launch {
            restaurantRepo.updateOrderStatus(orderId, newStatus)
            val updated = restaurantRepo.getOrderById(orderId)
            if (_selectedOrderForDetails.value?.order?.id == orderId) {
                _selectedOrderForDetails.value = updated
            }
            forceCloudSync()
        }
    }

    fun confirmOrderPayment(orderId: Long, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                restaurantRepo.markOrderAsPaid(orderId)
                val updated = restaurantRepo.getOrderById(orderId)
                if (_selectedOrderForDetails.value?.order?.id == orderId) {
                    _selectedOrderForDetails.value = updated
                }
                forceCloudSync()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to update order in database")
            }
        }
    }

    fun markAllAsReady() {
        viewModelScope.launch {
            pendingOrders.value.forEach {
                restaurantRepo.updateOrderStatus(it.order.id, "Ready")
            }
            preparingOrders.value.forEach {
                restaurantRepo.updateOrderStatus(it.order.id, "Ready")
            }
            forceCloudSync()
        }
    }

    fun savePrinterSetting(setting: PrinterSettingEntity) {
        viewModelScope.launch {
            printerRepo.savePrinterSetting(setting)
            forceCloudSync()
        }
    }

    fun printCurrentOrder(orderId: Long, onResult: (PrintResult) -> Unit) {
        viewModelScope.launch {
            val freshOrder = restaurantRepo.getOrderById(orderId)
            if (freshOrder == null) {
                onResult(com.restaurant.pos.data.repository.PrintResult(false, "Order not found. Please try again."))
                return@launch
            }
            val result = printerRepo.printOrderReceipt(freshOrder)
            onResult(result)
        }
    }

    fun printTestReceipt(setting: PrinterSettingEntity? = null, onResult: (PrintResult) -> Unit) {
        viewModelScope.launch {
            val pSetting = setting ?: printerSetting.value ?: PrinterSettingEntity()
            val result = printerRepo.printTestReceipt(pSetting)
            onResult(result)
        }
    }


    fun checkForGitHubUpdates() {
        viewModelScope.launch {
            val info = updateRepo.checkForUpdates(com.restaurant.pos.BuildConfig.VERSION_NAME)
            _updateInfo.value = info
        }
    }

    // Backup & Restore Engine & States
    val backupManager = BackupManager(getApplication(), database)

    private val _cloudOperationState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val cloudOperationState: StateFlow<BackupOpState> = _cloudOperationState.asStateFlow()

    fun cloudBackup() {
        val user = currentUser.value
        if (user == null) {
            _cloudOperationState.value = BackupOpState.Error("Unauthenticated: Please login to use Cloud Backup.")
            return
        }

        viewModelScope.launch {
            _cloudOperationState.value = BackupOpState.Progress("Preparing cloud backup...")
            val result = cloudSyncManager.performManualBackup()
            if (result.isSuccess) {
                _cloudOperationState.value = BackupOpState.Success(
                    title = "Cloud Backup Successful",
                    detail = "All your local POS data has been securely backed up to the cloud."
                )
            } else {
                _cloudOperationState.value = BackupOpState.Error(result.exceptionOrNull()?.message ?: "Cloud backup failed.")
            }
        }
    }

    fun cloudRestore() {
        val user = currentUser.value
        if (user == null) {
            _cloudOperationState.value = BackupOpState.Error("Unauthenticated: Please login to use Cloud Restore.")
            return
        }

        viewModelScope.launch {
            _cloudOperationState.value = BackupOpState.Progress("Downloading cloud data...")
            val result = cloudSyncManager.performManualRestore()
            if (result.isSuccess) {
                _cloudOperationState.value = BackupOpState.Success(
                    title = "Cloud Restore Successful",
                    detail = "Your application data has been synchronized with the cloud backup."
                )
            } else {
                _cloudOperationState.value = BackupOpState.Error(result.exceptionOrNull()?.message ?: "Cloud restore failed.")
            }
        }
    }

    fun clearCloudOpState() {
        _cloudOperationState.value = BackupOpState.Idle
    }

    private val _backupOperationState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val backupOperationState: StateFlow<BackupOpState> = _backupOperationState.asStateFlow()

    private val _lastBackupInfo = MutableStateFlow<BackupFileInfo?>(null)
    val lastBackupInfo: StateFlow<BackupFileInfo?> = _lastBackupInfo.asStateFlow()

    private val _pendingRestoreData = MutableStateFlow<ParsedBackupData?>(null)
    val pendingRestoreData: StateFlow<ParsedBackupData?> = _pendingRestoreData.asStateFlow()

    private val _recentBackups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val recentBackups: StateFlow<List<BackupFileInfo>> = _recentBackups.asStateFlow()

    init {
        loadRecentBackupsFromPrefs()
    }

    private fun loadRecentBackupsFromPrefs() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("recent_backups_json", null) ?: return
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<BackupFileInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    BackupFileInfo(
                        fileName = o.optString("fileName", ""),
                        uriString = o.optString("uriString", ""),
                        createdAtFormatted = o.optString("createdAtFormatted", ""),
                        sizeFormatted = o.optString("sizeFormatted", ""),
                        recordSummary = o.optString("recordSummary", ""),
                        timestamp = o.optLong("timestamp", 0L)
                    )
                )
            }
            _recentBackups.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveRecentBackupsToPrefs(list: List<BackupFileInfo>) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            list.forEach { info ->
                val o = JSONObject().apply {
                    put("fileName", info.fileName)
                    put("uriString", info.uriString)
                    put("createdAtFormatted", info.createdAtFormatted)
                    put("sizeFormatted", info.sizeFormatted)
                    put("recordSummary", info.recordSummary)
                    put("timestamp", info.timestamp)
                }
                arr.put(o)
            }
            prefs.edit().putString("recent_backups_json", arr.toString()).apply()
            _recentBackups.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addRecentBackup(info: BackupFileInfo) {
        val current = _recentBackups.value.filter { it.uriString != info.uriString }
        val updated = listOf(info) + current
        saveRecentBackupsToPrefs(updated.take(10))
    }

    fun removeRecentBackup(info: BackupFileInfo) {
        val updated = _recentBackups.value.filter { it.uriString != info.uriString }
        saveRecentBackupsToPrefs(updated)
    }

    fun createBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _backupOperationState.value = BackupOpState.Progress("Creating backup...")
            kotlinx.coroutines.delay(200)
            _backupOperationState.value = BackupOpState.Progress("Validating backup...")
            kotlinx.coroutines.delay(200)

            val result = backupManager.createBackup(uri)
            when (result) {
                is BackupResult.Success -> {
                    _lastBackupInfo.value = result.fileInfo
                    addRecentBackup(result.fileInfo)
                    _backupOperationState.value = BackupOpState.Success(
                        title = "Backup created successfully",
                        detail = "File: ${result.fileInfo.fileName}\nDate: ${result.fileInfo.createdAtFormatted}\nSize: ${result.fileInfo.sizeFormatted}\nContents: ${result.fileInfo.recordSummary}"
                    )
                }
                is BackupResult.Error -> {
                    _backupOperationState.value = BackupOpState.Error(result.message)
                }
            }
        }
    }

    fun validateAndPrepareRestore(uri: Uri) {
        viewModelScope.launch {
            _backupOperationState.value = BackupOpState.Progress("Validating backup file...")
            kotlinx.coroutines.delay(300)

            val result = backupManager.validateBackup(uri)
            when (result) {
                is ValidationResult.Success -> {
                    _pendingRestoreData.value = result.parsedData
                    _backupOperationState.value = BackupOpState.Idle
                }
                is ValidationResult.Error -> {
                    _pendingRestoreData.value = null
                    _backupOperationState.value = BackupOpState.Error(result.message)
                }
            }
        }
    }

    fun confirmRestore() {
        val parsed = _pendingRestoreData.value ?: return
        viewModelScope.launch {
            _backupOperationState.value = BackupOpState.Progress("Restoring data...")
            kotlinx.coroutines.delay(300)
            _backupOperationState.value = BackupOpState.Progress("Verifying restored data...")

            val result = backupManager.restoreBackup(parsed)
            _pendingRestoreData.value = null
            when (result) {
                is RestoreResult.Success -> {
                    _backupOperationState.value = BackupOpState.Success(
                        title = "Backup restored successfully",
                        detail = result.summary
                    )
                }
                is RestoreResult.Error -> {
                    _backupOperationState.value = BackupOpState.Error(result.message)
                }
            }
        }
    }

    fun cancelPendingRestore() {
        _pendingRestoreData.value = null
    }

    fun clearBackupOpState() {
        _backupOperationState.value = BackupOpState.Idle
    }

    fun resetAllAppDataAdmin(onComplete: (Boolean, String) -> Unit) {
        val user = currentUser.value
        val role = user?.role?.lowercase() ?: ""
        val isAdmin = role.contains("admin") || role.contains("manager") || role.contains("owner")
        if (!isAdmin) {
            onComplete(false, "Unauthorized: Only Administrator can reset application data.")
            return
        }

        viewModelScope.launch {
            val result = backupManager.resetAllApplicationData()
            if (result.isSuccess) {
                onComplete(true, result.getOrDefault("All application data has been successfully reset."))
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to reset data.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }
}
