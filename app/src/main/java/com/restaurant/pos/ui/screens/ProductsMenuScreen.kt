package com.restaurant.pos.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.restaurant.pos.data.db.CategoryEntity
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.data.model.AppPermission
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ProductsMenuScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val menuItems by viewModel.menuItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val isUserAdmin = currentUser?.isAdmin() == true
    val canViewProducts = isUserAdmin || (currentUser?.hasPermission(AppPermission.PRODUCTS_VIEW) == true)
    val canAddProducts = isUserAdmin || (currentUser?.hasPermission(AppPermission.PRODUCTS_ADD) == true)
    val canEditProducts = isUserAdmin || (currentUser?.hasPermission(AppPermission.PRODUCTS_EDIT) == true)
    val canDeleteProducts = isUserAdmin || (currentUser?.hasPermission(AppPermission.PRODUCTS_DELETE) == true)

    val canViewCategories = isUserAdmin || (currentUser?.hasPermission(AppPermission.CATEGORIES_VIEW) == true)
    val canAddCategories = isUserAdmin || (currentUser?.hasPermission(AppPermission.CATEGORIES_ADD) == true)
    val canEditCategories = isUserAdmin || (currentUser?.hasPermission(AppPermission.CATEGORIES_EDIT) == true)
    val canDeleteCategories = isUserAdmin || (currentUser?.hasPermission(AppPermission.CATEGORIES_DELETE) == true)

    var activeTab by remember { mutableStateOf(if (canViewProducts) "PRODUCTS" else "CATEGORY") } // "PRODUCTS" or "CATEGORY"
    var searchQuery by remember { mutableStateOf("") }

    // Item Dialog state
    var showAddEditDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<MenuItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<MenuItemEntity?>(null) }

    // Category Dialog state
    var showAddEditCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    val filteredItems = remember(menuItems, searchQuery, activeTab) {
        menuItems.filter { item ->
            searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.categoryName.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredCategories = remember(categories, searchQuery) {
        categories.filter { cat ->
            searchQuery.isBlank() || cat.name.contains(searchQuery, ignoreCase = true)
        }
    }

    val canShowFab = if (activeTab == "PRODUCTS") canAddProducts else canAddCategories

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("products_menu_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Products & Category",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        floatingActionButton = {
            if (canShowFab) {
                FloatingActionButton(
                    onClick = {
                        if (activeTab == "PRODUCTS") {
                            itemToEdit = null
                            showAddEditDialog = true
                        } else {
                            categoryToEdit = null
                            showAddEditCategoryDialog = true
                        }
                    },
                    containerColor = BrandPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_product_menu_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("products_menu_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Tabs Header: PRODUCTS | CATEGORY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderOutline, RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                val tabs = listOf("PRODUCTS", "CATEGORY")
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) BrandPrimary else Color.Transparent)
                            .clickable { activeTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = if (activeTab == "PRODUCTS") "Search Product..." else "Search Category...",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = BorderOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Item/Category List
            if (activeTab == "PRODUCTS") {
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No products found",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredItems,
                            key = { it.id },
                            contentType = { "product_item" }
                        ) { item ->
                            ItemCardRow(
                                item = item,
                                canEdit = canEditProducts,
                                canDelete = canDeleteProducts,
                                onEdit = {
                                    itemToEdit = item
                                    showAddEditDialog = true
                                },
                                onDelete = {
                                    itemToDelete = item
                                }
                            )
                        }
                    }
                }
            } else {
                if (filteredCategories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No categories found",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val categoryItemsMap = remember(categories, menuItems) {
                        categories.associate { cat ->
                            cat.id to menuItems.filter { it.categoryId == cat.id || it.categoryName.equals(cat.name, ignoreCase = true) }
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredCategories,
                            key = { it.id },
                            contentType = { "category_item" }
                        ) { cat ->
                            val itemsInCat = categoryItemsMap[cat.id] ?: emptyList()
                            CategoryCardRow(
                                category = cat,
                                items = itemsInCat,
                                canEdit = canEditCategories,
                                canDelete = canDeleteCategories,
                                onEdit = {
                                    categoryToEdit = cat
                                    showAddEditCategoryDialog = true
                                },
                                onDelete = {
                                    categoryToDelete = cat
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    // Add / Edit Product Dialog
    if (showAddEditDialog) {
        var isUploadingProductImage by remember { mutableStateOf(false) }
        var productImageUploadError by remember { mutableStateOf<String?>(null) }

        AddEditItemDialog(
            item = itemToEdit,
            activeTab = activeTab,
            categoriesList = categories.map { it.name },
            isUploading = isUploadingProductImage,
            uploadError = productImageUploadError,
            onDismiss = {
                if (!isUploadingProductImage) {
                    showAddEditDialog = false
                    productImageUploadError = null
                }
            },
            onSave = { name, category, price, costPrice, description, newImageUri, existingImageUrl, isAvailable ->
                productImageUploadError = null
                viewModel.saveMenuItemWithImageUpload(
                    id = itemToEdit?.id ?: 0L,
                    name = name,
                    categoryName = category,
                    price = price,
                    costPrice = costPrice,
                    description = description,
                    selectedImageUri = newImageUri,
                    existingImageUrl = existingImageUrl,
                    isAvailable = isAvailable,
                    onProgress = { uploading ->
                        isUploadingProductImage = uploading
                    },
                    onSuccess = {
                        isUploadingProductImage = false
                        showAddEditDialog = false
                        productImageUploadError = null
                    },
                    onError = { error ->
                        isUploadingProductImage = false
                        productImageUploadError = error
                    }
                )
            }
        )
    }

    // Add / Edit Category Dialog
    if (showAddEditCategoryDialog) {
        AddEditCategoryDialog(
            category = categoryToEdit,
            onDismiss = { showAddEditCategoryDialog = false },
            onSave = { name, iconName, imageUrl ->
                viewModel.saveCategory(
                    CategoryEntity(
                        id = categoryToEdit?.id ?: 0L,
                        name = name,
                        iconName = iconName,
                        imageUrl = imageUrl
                    )
                )
                showAddEditCategoryDialog = false
            },
            onPickImage = { uri ->
                viewModel.saveImageToInternalStorage(uri)
            }
        )
    }

    // Delete Product Confirmation Dialog
    if (itemToDelete != null) {
        val targetItem = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(
                    text = "Delete Item?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${targetItem.name}'? This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMenuItem(targetItem)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Delete Category Confirmation Dialog
    if (categoryToDelete != null) {
        val targetCat = categoryToDelete!!
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text(
                    text = "Delete Category?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${targetCat.name}'? All products in this category will be updated to 'General'.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(targetCat)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun ItemCardRow(
    item: MenuItemEntity,
    canEdit: Boolean = true,
    canDelete: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Image
            val context = LocalContext.current
            val imageModel = remember(item.imageUrl) {
                item.imageUrl.takeIf { it.isNotBlank() }?.let { url ->
                    if (url.startsWith("/")) File(url) else url
                }
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = when {
                            item.name.contains("Burger", true) -> "🍔"
                            item.name.contains("Pizza", true) -> "🍕"
                            item.name.contains("Fries", true) -> "🍟"
                            item.name.contains("Chicken", true) -> "🍗"
                            item.name.contains("Ice Cream", true) -> "🍨"
                            else -> "🥤"
                        },
                        fontSize = 30.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (item.isAvailable) StatusReady.copy(alpha = 0.2f) else StatusCancelled.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.isAvailable) "Available" else "Unavailable",
                            color = if (item.isAvailable) StatusReady else StatusCancelled,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.categoryName,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                val priceDisplay = if (item.price % 1.0 == 0.0) String.format(Locale.US, "%.0f", item.price) else String.format(Locale.US, "%.2f", item.price)
                Text(
                    text = "৳ $priceDisplay",
                    color = CurrencyGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Edit & Delete Action Buttons
            if (canEdit || canDelete) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (canEdit) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = CurrencyGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = StatusCancelled,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditItemDialog(
    item: MenuItemEntity?,
    activeTab: String,
    categoriesList: List<String>,
    isUploading: Boolean = false,
    uploadError: String? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, price: Double, costPrice: Double, description: String, selectedImageUri: Uri?, existingImageUrl: String, isAvailable: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var category by remember { mutableStateOf(item?.categoryName ?: if (categoriesList.isNotEmpty()) categoriesList.first() else "") }
    var priceStr by remember { mutableStateOf(item?.price?.let { if (it % 1.0 == 0.0) String.format(Locale.US, "%.0f", it) else String.format(Locale.US, "%.2f", it) } ?: "") }
    var costPriceStr by remember { mutableStateOf(item?.costPrice?.let { if (it > 0) (if (it % 1.0 == 0.0) String.format(Locale.US, "%.0f", it) else String.format(Locale.US, "%.2f", it)) else "" } ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var imageUrl by remember { mutableStateOf(item?.imageUrl ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isAvailable by remember { mutableStateOf(item?.isAvailable ?: true) }

    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val photoPickerLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
            }
        }
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = {
            if (!isUploading) onDismiss()
        },
        title = {
            Text(
                text = if (item == null) "Add ${if (activeTab == "PRODUCTS") "Product" else "Menu Item"}" else "Edit ${if (activeTab == "PRODUCTS") "Product" else "Menu Item"}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Image Picker Preview
                val imageModel: Any? = selectedImageUri ?: if (imageUrl.isNotBlank()) {
                    if (imageUrl.startsWith("/")) File(imageUrl) else imageUrl
                } else null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, BorderOutline, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Selected Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                            Text("No Image Selected", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    if (isUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CurrencyGold, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Uploading image to Cloud...", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (uploadError != null) {
                    Text(
                        text = "Upload failed: $uploadError",
                        color = StatusCancelled,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher?.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isUploading,
                        colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (imageModel == null) "ADD IMAGE" else "CHANGE IMAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (imageModel != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                selectedImageUri = null
                                imageUrl = ""
                            },
                            enabled = !isUploading
                        ) {
                            Text("REMOVE", color = StatusCancelled, fontSize = 11.sp)
                        }
                    }
                }

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name", color = TextMuted) },
                    enabled = !isUploading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Input
                var categoryExpanded by remember { mutableStateOf(false) }
                val displayCategory = if (categoriesList.isEmpty()) "No Category Available" else category
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = displayCategory,
                        onValueChange = {},
                        label = { Text("Category", color = TextMuted) },
                        enabled = !isUploading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = BorderOutline,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = if (categoriesList.isEmpty()) TextSecondary else TextPrimary
                        ),
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Invisible box to intercept clicks
                    if (!isUploading) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { 
                                    if (categoriesList.isNotEmpty()) {
                                        categoryExpanded = true 
                                    }
                                }
                        )
                    }
                    
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        categoriesList.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = TextPrimary) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Price Input
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price (৳)", color = TextMuted) },
                    enabled = !isUploading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Cost Price Input
                OutlinedTextField(
                    value = costPriceStr,
                    onValueChange = { costPriceStr = it },
                    label = { Text("Cost Price / Purchase Price (৳)", color = TextMuted) },
                    enabled = !isUploading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = TextMuted) },
                    enabled = !isUploading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Availability Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available for Orders", color = TextPrimary, fontSize = 13.sp)
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { isAvailable = it },
                        enabled = !isUploading,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CurrencyGold
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceStr.toDoubleOrNull() ?: 0.0
                    val cp = costPriceStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && !isUploading) {
                        onSave(name, category, p, cp, description, selectedImageUri, imageUrl, isAvailable)
                    }
                },
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVING...", fontWeight = FontWeight.Bold)
                } else {
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun CategoryCardRow(
    category: CategoryEntity,
    items: List<MenuItemEntity>,
    canEdit: Boolean = true,
    canDelete: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = remember(category.imageUrl) {
        category.imageUrl.takeIf { it.isNotBlank() }?.let { url ->
            if (url.startsWith("/")) File(url) else url
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_card_${category.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CurrencyGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = category.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = when (category.iconName.lowercase(Locale.US)) {
                                    "burger" -> "🍔"
                                    "pizza" -> "🍕"
                                    "drinks" -> "🥤"
                                    "fries" -> "🍟"
                                    "chicken" -> "🍗"
                                    "dessert" -> "🍨"
                                    else -> "📁"
                                },
                                fontSize = 20.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = category.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${items.size} ${if (items.size == 1) "product" else "products"}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (canEdit || canDelete) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canEdit) {
                            IconButton(onClick = onEdit) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Category",
                                    tint = CurrencyGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (canDelete) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Category",
                                    tint = StatusCancelled,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderOutline, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Products in this category:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${item.name}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "৳ ${String.format(Locale.US, "%.0f", item.price)}",
                                color = CurrencyGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditCategoryDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, iconName: String, imageUrl: String) -> Unit,
    onPickImage: suspend (Uri) -> String = { "" }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(category?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(category?.iconName ?: "burger") }
    var imageUrl by remember { mutableStateOf(category?.imageUrl ?: "") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = onPickImage(uri)
                if (savedPath.isNotBlank()) {
                    imageUrl = savedPath
                }
            }
        }
    }

    val iconsList = listOf(
        "burger" to "🍔",
        "pizza" to "🍕",
        "drinks" to "🥤",
        "fries" to "🍟",
        "chicken" to "🍗",
        "dessert" to "🍨",
        "folder" to "📁"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (category == null) "Add Category" else "Edit Category",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Image / Icon Section
                Text("Category Image / Icon", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Preview container matching exact standard category icon proportions
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, if (imageUrl.isNotBlank()) BrandPrimary else BorderOutline, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(if (imageUrl.startsWith("/")) File(imageUrl) else imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Category Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        } else {
                            Text(
                                text = when (selectedIcon.lowercase(Locale.US)) {
                                    "burger" -> "🍔"
                                    "pizza" -> "🍕"
                                    "drinks" -> "🥤"
                                    "fries" -> "🍟"
                                    "chicken" -> "🍗"
                                    "dessert" -> "🍨"
                                    else -> "📁"
                                },
                                fontSize = 24.sp
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPrimary),
                            border = BorderStroke(1.dp, BrandPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (imageUrl.isBlank()) "UPLOAD IMAGE" else "CHANGE IMAGE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (imageUrl.isNotBlank()) {
                            TextButton(
                                onClick = { imageUrl = "" },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Remove image (use icon)", color = StatusCancelled, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (imageUrl.isBlank()) {
                    Text("Or choose an emoji icon:", color = TextSecondary, fontSize = 12.sp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        iconsList.forEach { (iconKey, emoji) ->
                            val isSelected = selectedIcon == iconKey
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                    .clickable { selectedIcon = iconKey }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), selectedIcon, imageUrl)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
            ) {
                Text("SAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}
