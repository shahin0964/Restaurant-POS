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
import com.restaurant.pos.data.storage.ProductImageStorageManager
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
        started = SharingStarted.Eagerly,
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
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val allUsers: StateFlow<List<UserEntity>> = authRepo.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    suspend fun restoreSessionIfNeeded(): UserEntity? {
        return authRepo.restoreSessionIfNeeded()
    }

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

    val allOffers: StateFlow<List<OfferEntity>> = database.offerDao().getAllOffers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveOffer(offer: OfferEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (offer.id == 0L) {
                database.offerDao().insertOffer(offer)
            } else {
                database.offerDao().updateOffer(offer)
            }
            forceCloudSync()
            onComplete()
        }
    }

    fun toggleOfferStatus(offer: OfferEntity) {
        viewModelScope.launch {
            database.offerDao().updateOffer(offer.copy(isActive = !offer.isActive))
            forceCloudSync()
        }
    }

    fun deleteOffer(offer: OfferEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            database.offerDao().deleteOffer(offer)
            forceCloudSync()
            onComplete()
        }
    }

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

    fun saveMenuItemWithImageUpload(
        id: Long = 0,
        name: String,
        categoryName: String,
        price: Double,
        costPrice: Double = 0.0,
        description: String,
        selectedImageUri: Uri?,
        existingImageUrl: String,
        isAvailable: Boolean,
        onProgress: (Boolean) -> Unit = {},
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            var finalImageUrl = existingImageUrl

            if (selectedImageUri != null) {
                onProgress(true)
                // Use existing ID or random unique suffix for new product storage key
                val productIdKey = if (id != 0L) id.toString() else java.util.UUID.randomUUID().toString().take(8)
                val uploadResult = ProductImageStorageManager.uploadProductImage(
                    productId = productIdKey,
                    imageUri = selectedImageUri,
                    context = getApplication()
                )

                if (uploadResult.isSuccess) {
                    finalImageUrl = uploadResult.getOrThrow()
                } else {
                    onProgress(false)
                    val errorMsg = uploadResult.exceptionOrNull()?.localizedMessage ?: "Failed to upload image to Firebase Storage."
                    onError(errorMsg)
                    return@launch
                }
                onProgress(false)
            }

            restaurantRepo.saveMenuItem(
                id = id,
                name = name,
                categoryName = categoryName,
                price = price,
                costPrice = costPrice,
                description = description,
                imageUrl = finalImageUrl,
                isAvailable = isAvailable
            )
            forceCloudSync()
            onSuccess()
        }
    }

    fun deleteMenuItem(item: MenuItemEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (item.imageUrl.isNotBlank() && item.imageUrl.contains("firebasestorage.googleapis.com")) {
                ProductImageStorageManager.deleteProductImage(item.id.toString())
            }
            restaurantRepo.deleteMenuItem(item)
            forceCloudSync()
            onComplete()
        }
    }

    fun updateMenuItemDiscount(item: MenuItemEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            restaurantRepo.updateMenuItem(item)
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allTables: StateFlow<List<TableEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) {
            val accountId = user.firebaseUid ?: user.id.toString()
            restaurantRepo.getAllTables(accountId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.stateIn(
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
        val latestTableOrder = ordersList.firstOrNull { orderWithItems ->
            val o = orderWithItems.order
            val isDineIn = o.orderType.lowercase().contains("dine")
            if (!isDineIn) return@firstOrNull false

            val orderTableClean = o.tableNumber.trim().lowercase()
            val matchesId = o.tableId != null && o.tableId == table.id
            val matchesName = orderTableClean == tableNameClean ||
                    orderTableClean == "table $tableNameClean" ||
                    tableNameClean == "table $orderTableClean" ||
                    (tableNameClean.replace("table", "").trim().isNotBlank() &&
                     tableNameClean.replace("table", "").trim() == orderTableClean.replace("table", "").trim())

            matchesId || matchesName
        }

        if (latestTableOrder != null) {
            val o = latestTableOrder.order
            val isCompleted = o.status.equals("Completed", ignoreCase = true) || o.status.equals("Paid", ignoreCase = true) || o.isPaid
            val isCancelled = o.status.equals("Cancelled", ignoreCase = true)
            val isActive = !isCompleted && !isCancelled

            if (isActive) {
                return latestTableOrder
            }
        }
        return null
    }

    fun addTable(name: String, capacity: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (name.isBlank()) {
            onError("Table name cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                val accountId = currentUser.value?.let { it.firebaseUid ?: it.id.toString() } ?: ""
                restaurantRepo.addTable(name.trim(), capacity, accountId)
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

    private val _discount = MutableStateFlow(0.0)
    val discount: StateFlow<Double> = combine(_cartItems, _discount, allOffers) { cart, manualDisc, offers ->
        val gross = cart.sumOf { it.menuItem.price * it.quantity }
        val prodDisc = cart.sumOf { cartItem ->
            val item = cartItem.menuItem
            if (item.discountEnabled) {
                val disc = if (item.discountType == "PERCENTAGE") {
                    item.price * (item.discountValue / 100.0)
                } else {
                    item.discountValue
                }
                disc * cartItem.quantity
            } else {
                0.0
            }
        }
        val activeOffers = offers.filter { it.isActive }
        val offerDisc = activeOffers.sumOf { offer ->
            if (gross >= offer.minOrderAmount) {
                val disc = if (offer.discountType == "PERCENTAGE") {
                    gross * (offer.discountValue / 100.0)
                } else {
                    offer.discountValue
                }
                if (offer.maxDiscountAmount > 0.0) disc.coerceAtMost(offer.maxDiscountAmount) else disc
            } else 0.0
        }
        (prodDisc + manualDisc + offerDisc).coerceAtMost(gross)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    private val _customTaxRate = MutableStateFlow<Double?>(null)
    val customTaxRate: StateFlow<Double?> = _customTaxRate.asStateFlow()

    fun setCustomTaxRate(rate: Double?) {
        _customTaxRate.value = rate
    }

    val effectiveTaxRate: StateFlow<Double> = combine(_customTaxRate, receiptSetting) { custom, setting ->
        custom ?: if (setting?.isTaxEnabled == true) setting.taxRate else 0.0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0.0
    )

    val tax: StateFlow<Double> = combine(_cartItems, discount, effectiveTaxRate) { cart, totalDisc, rate ->
        if (rate <= 0.0) {
            0.0
        } else {
            val sub = cart.sumOf { it.menuItem.price * it.quantity }
            val net = (sub - totalDisc).coerceAtLeast(0.0)
            val calc = net * (rate / 100.0)
            if (calc.isNaN() || calc.isInfinite() || calc < 0.0) 0.0 else calc
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0.0
    )

    private val _selectedOrderForDetails = MutableStateFlow<OrderWithItems?>(null)
    val selectedOrderForDetails: StateFlow<OrderWithItems?> = _selectedOrderForDetails.asStateFlow()

    private val _isAddingToOrder = MutableStateFlow(false)
    val isAddingToOrder: StateFlow<Boolean> = _isAddingToOrder.asStateFlow()

    fun setIsAddingToOrder(isAdding: Boolean) {
        _isAddingToOrder.value = isAdding
    }

    fun addItemToExistingOrder(
        item: MenuItemEntity,
        quantity: Int = 1,
        note: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val orderId = selectedOrderForDetails.value?.order?.id ?: run {
            onComplete(false)
            return
        }
        if (quantity <= 0) {
            onComplete(false)
            return
        }

        val cartItem = CartItem(menuItem = item, quantity = quantity, note = note)
        val setting = receiptSetting.value
        val taxRate = if (setting?.isTaxEnabled == true) setting.taxRate else null

        viewModelScope.launch {
            restaurantRepo.addItemsToExistingOrder(orderId, listOf(cartItem), taxRate)
            clearCart()
            setIsAddingToOrder(false)
            val updatedOrder = restaurantRepo.getOrderById(orderId)
            _selectedOrderForDetails.value = updatedOrder
            forceCloudSync()
            onComplete(true)
        }
    }

    fun addItemsToExistingOrder(onComplete: (Boolean) -> Unit) {
        val orderId = selectedOrderForDetails.value?.order?.id ?: run {
            onComplete(false)
            return
        }
        val itemsToAdd = _cartItems.value
        if (itemsToAdd.isEmpty()) {
            // Check if selectedMenuItem exists as fallback
            val selected = _selectedMenuItem.value
            if (selected != null) {
                addItemToExistingOrder(selected, 1, "", onComplete)
                return
            }
            onComplete(false)
            return
        }

        val setting = receiptSetting.value
        val taxRate = if (setting?.isTaxEnabled == true) setting.taxRate else null

        viewModelScope.launch {
            restaurantRepo.addItemsToExistingOrder(orderId, itemsToAdd, taxRate)
            clearCart()
            setIsAddingToOrder(false)
            val updatedOrder = restaurantRepo.getOrderById(orderId)
            _selectedOrderForDetails.value = updatedOrder
            forceCloudSync()
            onComplete(true)
        }
    }

    private val _selectedMenuItem = MutableStateFlow<MenuItemEntity?>(null)
    val selectedMenuItem: StateFlow<MenuItemEntity?> = _selectedMenuItem.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.restoreSessionIfNeeded()
            authRepo.seedDefaultUserIfNeeded()
            val hasClearedLegacy = sharedPrefs.getBoolean("has_removed_existing_products_and_categories_v1", false)
            if (!hasClearedLegacy) {
                restaurantRepo.clearExistingProductsAndCategories()
                sharedPrefs.edit().putBoolean("has_removed_existing_products_and_categories_v1", true).apply()
            }
            val hasRemovedSampleTables = sharedPrefs.getBoolean("has_removed_sample_tables_v1", false)
            if (!hasRemovedSampleTables) {
                val accountId = currentUser.value?.let { it.firebaseUid ?: it.id.toString() } ?: ""
                restaurantRepo.removeSampleTables(accountId)
                sharedPrefs.edit().putBoolean("has_removed_sample_tables_v1", true).apply()
            }
            checkForGitHubUpdates()
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
        _discount.value = 0.0
        _customTaxRate.value = null
    }

    fun calculateSubtotal(): Double {
        return _cartItems.value.sumOf { it.menuItem.price * it.quantity }
    }

    fun calculateProductDiscount(): Double {
        return _cartItems.value.sumOf { cartItem ->
            val item = cartItem.menuItem
            if (item.discountEnabled) {
                val disc = if (item.discountType == "PERCENTAGE") {
                    item.price * (item.discountValue / 100.0)
                } else {
                    item.discountValue
                }
                disc * cartItem.quantity
            } else {
                0.0
            }
        }
    }

    fun calculateTotalDiscount(): Double {
        val gross = calculateSubtotal()
        val prodDisc = calculateProductDiscount()
        val manualDisc = _discount.value
        val activeOffers = allOffers.value.filter { it.isActive }
        val offerDisc = activeOffers.sumOf { offer ->
            if (gross >= offer.minOrderAmount) {
                val disc = if (offer.discountType == "PERCENTAGE") {
                    gross * (offer.discountValue / 100.0)
                } else {
                    offer.discountValue
                }
                if (offer.maxDiscountAmount > 0.0) disc.coerceAtMost(offer.maxDiscountAmount) else disc
            } else 0.0
        }
        return (prodDisc + manualDisc + offerDisc).coerceAtMost(gross)
    }

    fun calculateTax(): Double {
        val rate = effectiveTaxRate.value
        if (rate <= 0.0) return 0.0
        val gross = calculateSubtotal()
        val totalDisc = calculateTotalDiscount()
        val net = (gross - totalDisc).coerceAtLeast(0.0)
        val calc = net * (rate / 100.0)
        return if (calc.isNaN() || calc.isInfinite() || calc < 0.0) 0.0 else calc
    }

    fun calculateTotal(): Double {
        val gross = calculateSubtotal()
        val totalDisc = calculateTotalDiscount()
        val taxVal = calculateTax()
        val total = gross - totalDisc + taxVal
        return if (total < 0) 0.0 else total
    }

    fun placeOrder(paymentMethod: String, onOrderPlaced: (Long) -> Unit) {
        viewModelScope.launch {
            val subtotal = calculateSubtotal()
            val totalDisc = calculateTotalDiscount()
            val taxVal = calculateTax()
            val total = calculateTotal()
            val selectedTId = if (_orderType.value.lowercase().contains("dine")) _selectedTableId.value else null
            val orderId = restaurantRepo.createOrder(
                orderType = _orderType.value,
                tableNumber = _tableNumber.value,
                customerName = _customerName.value,
                note = _orderNote.value,
                subtotal = subtotal,
                discount = totalDisc,
                tax = taxVal,
                total = total,
                paymentMethod = paymentMethod,
                cartItems = _cartItems.value,
                tableId = selectedTId
            )
            val orderWithItems = restaurantRepo.getOrderById(orderId)
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

    // Auto Backup State
    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupFrequency = MutableStateFlow("Daily")
    val autoBackupFrequency: StateFlow<String> = _autoBackupFrequency.asStateFlow()

    private val _lastAutoBackupTime = MutableStateFlow(0L)
    val lastAutoBackupTime: StateFlow<Long> = _lastAutoBackupTime.asStateFlow()

    private val _lastAutoBackupStatus = MutableStateFlow<String?>(null)
    val lastAutoBackupStatus: StateFlow<String?> = _lastAutoBackupStatus.asStateFlow()

    private val _lastAutoBackupError = MutableStateFlow<String?>(null)
    val lastAutoBackupError: StateFlow<String?> = _lastAutoBackupError.asStateFlow()

    private val _lastAutoBackupFile = MutableStateFlow<String?>(null)
    val lastAutoBackupFile: StateFlow<String?> = _lastAutoBackupFile.asStateFlow()

    init {
        loadRecentBackupsFromPrefs()
        loadAutoBackupSettings()
    }

    fun refreshAutoBackupState() {
        loadAutoBackupSettings()
    }

    private fun loadAutoBackupSettings() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("auto_backup_enabled", false)
            val frequency = prefs.getString("auto_backup_frequency", "Daily") ?: "Daily"
            val lastTime = prefs.getLong("last_auto_backup_time", 0L)
            val status = prefs.getString("last_auto_backup_status", null)
            val error = prefs.getString("last_auto_backup_error", null)
            val file = prefs.getString("last_auto_backup_file", null)

            _autoBackupEnabled.value = isEnabled
            _autoBackupFrequency.value = frequency
            _lastAutoBackupTime.value = lastTime
            _lastAutoBackupStatus.value = status
            _lastAutoBackupError.value = error
            _lastAutoBackupFile.value = file

            if (isEnabled) {
                AutoBackupScheduler.scheduleOrUpdate(getApplication(), true, frequency)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        _autoBackupEnabled.value = enabled
        AutoBackupScheduler.scheduleOrUpdate(getApplication(), enabled, _autoBackupFrequency.value)
    }

    fun setAutoBackupFrequency(frequency: String) {
        val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("auto_backup_frequency", frequency).apply()
        _autoBackupFrequency.value = frequency
        if (_autoBackupEnabled.value) {
            AutoBackupScheduler.scheduleOrUpdate(getApplication(), true, frequency)
        }
    }

    fun runAutoBackupNow() {
        viewModelScope.launch {
            _backupOperationState.value = BackupOpState.Progress("Running automatic backup...")
            val result = backupManager.createAutoBackup()
            when (result) {
                is BackupResult.Success -> {
                    val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putLong("last_auto_backup_time", result.fileInfo.timestamp)
                        .putString("last_auto_backup_status", "SUCCESS")
                        .putString("last_auto_backup_error", null)
                        .putString("last_auto_backup_file", result.fileInfo.fileName)
                        .putString("last_auto_backup_summary", result.fileInfo.recordSummary)
                        .apply()

                    _lastAutoBackupTime.value = result.fileInfo.timestamp
                    _lastAutoBackupStatus.value = "SUCCESS"
                    _lastAutoBackupError.value = null
                    _lastAutoBackupFile.value = result.fileInfo.fileName

                    _backupOperationState.value = BackupOpState.Success(
                        title = "Auto Backup completed successfully",
                        detail = "Saved to: ${result.fileInfo.fileName}\nTime: ${result.fileInfo.createdAtFormatted}\nSize: ${result.fileInfo.sizeFormatted}\nSummary: ${result.fileInfo.recordSummary}"
                    )
                }
                is BackupResult.Error -> {
                    val prefs = getApplication<Application>().getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("last_auto_backup_status", "FAILED")
                        .putString("last_auto_backup_error", result.message)
                        .apply()

                    _lastAutoBackupStatus.value = "FAILED"
                    _lastAutoBackupError.value = result.message

                    _backupOperationState.value = BackupOpState.Error("Auto Backup failed: ${result.message}")
                }
            }
        }
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
