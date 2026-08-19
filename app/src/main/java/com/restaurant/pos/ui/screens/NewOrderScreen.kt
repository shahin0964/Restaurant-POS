package com.restaurant.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import com.restaurant.pos.R
import com.restaurant.pos.data.db.CategoryEntity
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.data.repository.CartItem
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    viewModel: RestaurantViewModel,
    onProceedToSummary: () -> Unit,
    onAddMoreItems: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val orderType by viewModel.orderType.collectAsState()
    val tableNumber by viewModel.tableNumber.collectAsState()
    val selectedTableId by viewModel.selectedTableId.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tables by viewModel.allTables.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var isCartExpanded by remember { mutableStateOf(false) }

    // Dropdown States
    var orderTypeDropdownExpanded by remember { mutableStateOf(false) }
    var tableDropdownExpanded by remember { mutableStateOf(false) }

    // Auto-select first available table for Dine In if not set
    LaunchedEffect(tables, orderType, selectedTableId) {
        if (orderType == "Dine In" && tables.isNotEmpty()) {
            val currentSelected = tables.find { it.id == selectedTableId || it.name.equals(tableNumber, true) }
            val isCurrentOccupied = currentSelected != null && viewModel.getActiveOrderForTable(currentSelected, allOrders) != null
            if (currentSelected == null || isCurrentOccupied) {
                val availableTable = tables.firstOrNull { viewModel.getActiveOrderForTable(it, allOrders) == null }
                if (availableTable != null) {
                    viewModel.setTableNumber(availableTable.name)
                    viewModel.setSelectedTableId(availableTable.id)
                }
            }
        }
    }

    val total = viewModel.calculateTotal()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("new_order_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_new_order),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { viewModel.clearCart() }) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Cart",
                        tint = TextSecondary
                    )
                }
            }
        },
        bottomBar = {
            // Persistent Total & PROCEED TO PAYMENT Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(stringResource(R.string.lbl_total_amount), color = TextSecondary, fontSize = 11.sp)
                        val formattedTotal = if (total % 1.0 == 0.0) String.format(Locale.US, "%.0f", total) else String.format(Locale.US, "%.2f", total)
                        Text(
                            text = "৳ $formattedTotal",
                            color = CurrencyGold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onProceedToSummary,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("proceed_to_payment_btn")
                    ) {
                        Text(stringResource(R.string.btn_proceed_to_payment), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("new_order_screen")
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
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // 1. Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.hint_search_food), color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Order Info & Table Selection Side-By-Side Dropdowns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Order Info Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderOutline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clickable { orderTypeDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val typeLabel = when (orderType) {
                                    "Dine In" -> stringResource(R.string.type_dine_in)
                                    "Take Away" -> stringResource(R.string.type_takeaway)
                                    "Delivery" -> stringResource(R.string.type_delivery)
                                    else -> orderType
                                }
                                Text(
                                    text = stringResource(R.string.lbl_type_prefix, typeLabel),
                                    color = CurrencyGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = orderTypeDropdownExpanded,
                            onDismissRequest = { orderTypeDropdownExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            val types = listOf(
                                stringResource(R.string.type_dine_in) to "Dine In",
                                stringResource(R.string.type_takeaway) to "Take Away",
                                stringResource(R.string.type_delivery) to "Delivery"
                            )
                            types.forEach { (display, internalName) ->
                                DropdownMenuItem(
                                    text = { Text(display, color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.setOrderType(internalName)
                                        orderTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Table Selection Dropdown (Only relevant for Dine In)
                    Box(modifier = Modifier.weight(1f)) {
                        val isDineIn = orderType == "Dine In"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDineIn) DarkSurface else DarkSurface.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isDineIn) BorderOutline else BorderOutline.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clickable(enabled = isDineIn) { tableDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isDineIn) {
                                        if (tableNumber.isNotBlank()) stringResource(R.string.lbl_table_prefix, tableNumber) else stringResource(R.string.lbl_select_table)
                                    } else {
                                        stringResource(R.string.lbl_non_dine_in)
                                    },
                                    color = if (isDineIn) CurrencyGold else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isDineIn) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (isDineIn) {
                            val availableTables = tables.filter { table ->
                                viewModel.getActiveOrderForTable(table, allOrders) == null
                            }
                            DropdownMenu(
                                expanded = tableDropdownExpanded,
                                onDismissRequest = { tableDropdownExpanded = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                if (availableTables.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.msg_no_tables_available), color = TextMuted, fontSize = 12.sp) },
                                        onClick = { tableDropdownExpanded = false }
                                    )
                                } else {
                                    availableTables.forEach { table ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(table.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text("${table.capacity} Seats", color = TextMuted, fontSize = 10.sp)
                                                }
                                            },
                                            onClick = {
                                                viewModel.setTableNumber(table.name)
                                                viewModel.setSelectedTableId(table.id)
                                                tableDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Horizontal Product Category Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    // "All" Category
                    item(key = "all_categories", contentType = "category_badge") {
                        val isAllSelected = selectedCategory == null
                        CategoryItemBadge(
                            name = stringResource(R.string.lbl_all_items),
                            emoji = "🍽️",
                            isSelected = isAllSelected,
                            onClick = { selectedCategory = null }
                        )
                    }

                    items(
                        items = categories,
                        key = { it.id },
                        contentType = { "category_badge" }
                    ) { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        val emoji = when (cat.iconName.lowercase(Locale.US)) {
                            "burger" -> "🍔"
                            "pizza" -> "🍕"
                            "drinks" -> "🥤"
                            "fries" -> "🍟"
                            "chicken" -> "🍗"
                            "dessert" -> "🍨"
                            else -> "📁"
                        }
                        CategoryItemBadge(
                            name = cat.name,
                            emoji = emoji,
                            imageUrl = cat.imageUrl,
                            isSelected = isSelected,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Product List (Grid)
                val filteredItems = remember(menuItems, searchQuery, selectedCategory) {
                    menuItems.filter { item ->
                        val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
                        val matchesCategory = selectedCategory == null || 
                                              item.categoryId == selectedCategory?.id || 
                                              item.categoryName.equals(selectedCategory?.name, ignoreCase = true)
                        matchesSearch && matchesCategory
                    }
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.msg_no_products_found), color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    val cartQtyMap = remember(cartItems) {
                        cartItems.associate { it.menuItem.id to it.quantity }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp), // Space to avoid overlaying the bottom panel
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = filteredItems,
                            key = { it.id },
                            contentType = { "product_grid_card" }
                        ) { item ->
                            val cartQty = cartQtyMap[item.id] ?: 0
                            ProductGridCard(
                                item = item,
                                cartQty = cartQty,
                                onAdd = { viewModel.addToCart(item, 1) }
                            )
                        }
                    }
                }
            }

            // 5 & 6. Expandable Current Order Panel
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(bottom = 0.dp) // Sits neatly above the bottomBar scaffold area
            ) {
                CurrentOrderExpandablePanel(
                    cartItems = cartItems,
                    isExpanded = isCartExpanded,
                    onToggleExpand = { isCartExpanded = !isCartExpanded },
                    onIncrease = { item -> viewModel.updateCartQuantity(item.menuItem, item.quantity + 1) },
                    onDecrease = { item -> viewModel.updateCartQuantity(item.menuItem, item.quantity - 1) },
                    onRemove = { item -> viewModel.updateCartQuantity(item.menuItem, 0) }
                )
            }
        }
    }
}

@Composable
fun CategoryItemBadge(
    name: String,
    emoji: String,
    imageUrl: String = "",
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = remember(imageUrl) {
        imageUrl.takeIf { it.isNotBlank() }?.let { url ->
            if (url.startsWith("/")) File(url) else url
        }
    }

    Box(
        modifier = Modifier.padding(bottom = 4.dp) // offset space for 3D shadow
    ) {
        // 3D Bottom shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.5f))
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) BrandPrimary else DarkSurface)
                .border(
                    width = 1.dp,
                    color = if (isSelected) BrandPrimary else BorderOutline,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (imageModel != null) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Text(emoji, fontSize = 15.sp)
            }
            Text(
                text = name,
                color = if (isSelected) Color.White else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProductGridCard(
    item: MenuItemEntity,
    cartQty: Int,
    onAdd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
    ) {
        // 3D Shadow Layer (offset bottom and right to look 3D and rounded)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Main Card Surface
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    // Product visual representation
                    val context = LocalContext.current
                    val imageModel = remember(item.imageUrl) {
                        item.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            if (url.startsWith("/")) File(url) else url
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                                    item.name.contains("Fries", true) -> "🍟"
                                    item.name.contains("Pizza", true) -> "🍕"
                                    item.name.contains("Coke", true) || item.name.contains("Drink", true) -> "🥤"
                                    item.name.contains("Salad", true) -> "🥗"
                                    item.name.contains("Pasta", true) -> "🍝"
                                    else -> "🍽️"
                                },
                                fontSize = 36.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "৳ ${formatAmount(item.price)}",
                            color = CurrencyGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Rounded + Button in bottom corner
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary)
                                .clickable { onAdd() }
                                .testTag("add_item_btn_${item.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Item",
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Quantity badge if added to cart
                if (cartQty > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(CurrencyGold, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${cartQty}x",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentOrderExpandablePanel(
    cartItems: List<CartItem>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onIncrease: (CartItem) -> Unit,
    onDecrease: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, BorderOutline),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 300))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CurrencyGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${cartItems.sumOf { it.quantity }}",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_current_order_list),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isExpanded) stringResource(R.string.lbl_hide_details) else stringResource(R.string.lbl_show_details),
                        color = CurrencyGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = CurrencyGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Order List
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    if (cartItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.msg_no_items_in_cart),
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(
                                items = cartItems,
                                key = { it.menuItem.id },
                                contentType = { "cart_item" }
                            ) { item ->
                                ExpandedCartRow(
                                    cartItem = item,
                                    onIncrease = { onIncrease(item) },
                                    onDecrease = { onDecrease(item) },
                                    onRemove = { onRemove(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandedCartRow(
    cartItem: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = remember(cartItem.menuItem.imageUrl) {
        cartItem.menuItem.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            if (url.startsWith("/")) File(url) else url
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, BorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Image
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = cartItem.menuItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = when {
                            cartItem.menuItem.name.contains("Burger", true) -> "🍔"
                            cartItem.menuItem.name.contains("Fries", true) -> "🍟"
                            cartItem.menuItem.name.contains("Pizza", true) -> "🍕"
                            cartItem.menuItem.name.contains("Coke", true) || cartItem.menuItem.name.contains("Drink", true) -> "🥤"
                            cartItem.menuItem.name.contains("Salad", true) -> "🥗"
                            cartItem.menuItem.name.contains("Pasta", true) -> "🍝"
                            else -> "🍽️"
                        },
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Product Name and Price info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.menuItem.name,
                    color = TextPrimary,
                        fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "৳ ${formatAmount(cartItem.menuItem.price * cartItem.quantity)}",
                    color = CurrencyGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quantity controls [- Qty +]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { onDecrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Text(
                    text = "${cartItem.quantity}",
                        color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { onIncrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Remove/Delete option
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Item",
                    tint = StatusCancelled,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", amount)
    } else {
        String.format(Locale.US, "%.2f", amount)
    }
}
