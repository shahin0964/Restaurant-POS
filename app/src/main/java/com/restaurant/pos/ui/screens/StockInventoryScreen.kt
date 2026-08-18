package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.data.db.StockLogEntity
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockInventoryScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val allMenuItems by viewModel.restaurantRepo.menuItems.collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf("ALL") } // ALL, LOW STOCK, OUT OF STOCK
    var searchQuery by remember { mutableStateOf("") }
    var selectedItemForDetail by remember { mutableStateOf<MenuItemEntity?>(null) }
    var quickAddStockItem by remember { mutableStateOf<MenuItemEntity?>(null) }

    val filteredItems = remember(allMenuItems, selectedTab, searchQuery) {
        allMenuItems.filter { item ->
            val matchesTab = when (selectedTab) {
                "LOW STOCK" -> item.stockQuantity > 0 && item.stockQuantity <= item.lowStockThreshold
                "OUT OF STOCK" -> item.stockQuantity <= 0
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.categoryName.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    val totalCount = allMenuItems.size
    val lowStockCount = remember(allMenuItems) { allMenuItems.count { it.stockQuantity > 0 && it.stockQuantity <= it.lowStockThreshold } }
    val outOfStockCount = remember(allMenuItems) { allMenuItems.count { it.stockQuantity <= 0 } }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("stock_back_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Stock / Inventory",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("stock_inventory_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Real Count Summary Cards Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InventorySummaryCard(
                    title = "Total Items",
                    count = totalCount,
                    accentColor = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                InventorySummaryCard(
                    title = "Low Stock",
                    count = lowStockCount,
                    accentColor = CurrencyGold,
                    modifier = Modifier.weight(1f)
                )
                InventorySummaryCard(
                    title = "Out of Stock",
                    count = outOfStockCount,
                    accentColor = StatusCancelled,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Filter Tabs: [ ALL ] [ LOW STOCK ] [ OUT OF STOCK ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, shape = RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("ALL", "LOW STOCK", "OUT OF STOCK").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CurrencyGold else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search item name or category...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = CurrencyGold,
                    unfocusedBorderColor = BorderOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stock_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Inventory Item List / Empty States
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (selectedTab) {
                            "LOW STOCK" -> "No low stock items."
                            "OUT OF STOCK" -> "No out of stock items."
                            else -> "No inventory items."
                        },
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.id },
                        contentType = { "inventory_item" }
                    ) { item ->
                        InventoryItemCard(
                            item = item,
                            onItemClick = { selectedItemForDetail = item },
                            onQuickAddStock = { quickAddStockItem = item }
                        )
                    }
                }
            }
        }
    }

    // Quick Add Stock Dialog
    if (quickAddStockItem != null) {
        val target = quickAddStockItem!!
        QuickAddStockDialog(
            item = target,
            onDismiss = { quickAddStockItem = null },
            onConfirmAdd = { qtyToAdd ->
                viewModel.updateItemStock(
                    menuItemId = target.id,
                    newQuantity = target.stockQuantity + qtyToAdd,
                    reasonNote = "+$qtyToAdd Stock Added"
                )
                quickAddStockItem = null
            }
        )
    }

    // Full Item Details & Stock Adjustment / History Sheet
    if (selectedItemForDetail != null) {
        StockDetailDialog(
            item = selectedItemForDetail!!,
            viewModel = viewModel,
            onDismiss = { selectedItemForDetail = null }
        )
    }
}

@Composable
fun InventorySummaryCard(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count",
                color = accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InventoryItemCard(
    item: MenuItemEntity,
    onItemClick: () -> Unit,
    onQuickAddStock: () -> Unit
) {
    val status = when {
        item.stockQuantity <= 0 -> "OUT OF STOCK"
        item.stockQuantity <= item.lowStockThreshold -> "LOW STOCK"
        else -> "IN STOCK"
    }

    val badgeColor = when (status) {
        "OUT OF STOCK" -> StatusCancelled
        "LOW STOCK" -> CurrencyGold
        else -> StatusReady
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .testTag("inventory_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Image or Placeholder
            val imageModel = remember(item.imageUrl) {
                item.imageUrl.takeIf { it.isNotBlank() }?.let {
                    if (it.startsWith("/")) java.io.File(it) else it
                }
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = CurrencyGold,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                        fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.categoryName,
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Stock: ",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${item.stockQuantity} ${item.unit}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Add Stock Button
            OutlinedButton(
                onClick = onQuickAddStock,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CurrencyGold),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CurrencyGold),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("add_stock_btn_${item.id}")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("+ Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuickAddStockDialog(
    item: MenuItemEntity,
    onDismiss: () -> Unit,
    onConfirmAdd: (Int) -> Unit
) {
    var amountText by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(
                text = "Add Stock: ${item.name}",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Current Stock: ${item.stockQuantity} ${item.unit}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 20, 50).forEach { preset ->
                        OutlinedButton(
                            onClick = { amountText = preset.toString() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (amountText == preset.toString()) CurrencyGold else BorderOutline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (amountText == preset.toString()) CurrencyGold else TextPrimary
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("+$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                    label = { Text("Quantity to Add", color = TextMuted) },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = amountText.toIntOrNull() ?: 0
                    if (qty > 0) {
                        onConfirmAdd(qty)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
            ) {
                Text("ADD STOCK", fontWeight = FontWeight.Bold)
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

@Composable
fun StockDetailDialog(
    item: MenuItemEntity,
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    val logsFlow = remember(item.id) { viewModel.getStockLogsForMenuItem(item.id) }
    val logs by logsFlow.collectAsState(initial = emptyList())

    var stockQtyText by remember { mutableStateOf(item.stockQuantity.toString()) }
    var unitText by remember { mutableStateOf(item.unit) }
    var thresholdText by remember { mutableStateOf(item.lowStockThreshold.toString()) }
    var noteText by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Category: ${item.categoryName} • Unit Price: ৳${item.price}",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                // Adjust Quantity & Threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockQtyText,
                        onValueChange = { stockQtyText = it.filter { c -> c.isDigit() } },
                        label = { Text("Stock Qty", color = TextMuted) },
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
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unit (e.g. pcs)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it.filter { c -> c.isDigit() } },
                    label = { Text("Low Stock Alert Limit", color = TextMuted) },
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

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Adjustment Note (Optional)", color = TextMuted) },
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

                Button(
                    onClick = {
                        val newQty = stockQtyText.toIntOrNull() ?: item.stockQuantity
                        val newThreshold = thresholdText.toIntOrNull() ?: item.lowStockThreshold
                        viewModel.updateItemStock(
                            menuItemId = item.id,
                            newQuantity = newQty,
                            unit = unitText,
                            lowStockThreshold = newThreshold,
                            reasonNote = noteText.ifBlank { "Manual Stock Update" }
                        )
                        Toast.makeText(context, "Stock updated successfully", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE STOCK ADJUSTMENT", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = BorderOutline, thickness = 1.dp)

                // Stock Movement Logs Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = CurrencyGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOCK MOVEMENT HISTORY",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (logs.isEmpty()) {
                    Text(
                        text = "No stock transactions recorded yet.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                } else {
                    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceVariant, shape = RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = log.note,
                                        color = TextPrimary,
                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.timestamp)),
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = if (log.changeAmount > 0) "+${log.changeAmount}" else "${log.changeAmount}",
                                    color = if (log.changeAmount >= 0) StatusReady else StatusCancelled,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = DarkSurface
    )
}
