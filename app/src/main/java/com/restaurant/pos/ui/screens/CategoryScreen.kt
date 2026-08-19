package com.restaurant.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.restaurant.pos.R
import com.restaurant.pos.data.db.CategoryEntity
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.io.File
import java.util.Locale

@Composable
fun CategoryScreen(
    viewModel: RestaurantViewModel,
    onSelectItem: (MenuItemEntity) -> Unit,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val filteredItems = remember(searchQuery, selectedCategoryFilter, menuItems) {
        menuItems.filter { item ->
            val matchesCategory = (selectedCategoryFilter == "All") || item.categoryName.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("category_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_category),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("category_screen")
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.hint_search_category_item), color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = CurrencyGold,
                    unfocusedBorderColor = BorderOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "cat_all", contentType = "category_chip") {
                    CategoryChip(
                        label = stringResource(R.string.filter_all),
                        isSelected = selectedCategoryFilter == "All",
                        onClick = { selectedCategoryFilter = "All" }
                    )
                }
                items(
                    items = categories,
                    key = { it.id },
                    contentType = { "category_chip" }
                ) { cat ->
                    CategoryChip(
                        label = cat.name,
                        imageUrl = cat.imageUrl,
                        iconName = cat.iconName,
                        isSelected = selectedCategoryFilter == cat.name,
                        onClick = { selectedCategoryFilter = cat.name }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid of Items / Categories
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.msg_no_items_found), color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredItems,
                        key = { it.id },
                        contentType = { "menu_item_grid" }
                    ) { item ->
                        MenuItemGridCard(
                            item = item,
                            onClick = {
                                viewModel.setSelectedMenuItem(item)
                                onSelectItem(item)
                            }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    imageUrl: String = "",
    iconName: String = "",
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
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) CurrencyGold else DarkSurface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (imageModel != null) {
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            } else if (iconName.isNotBlank()) {
                val emoji = when (iconName.lowercase(Locale.US)) {
                    "burger" -> "🍔"
                    "pizza" -> "🍕"
                    "drinks" -> "🥤"
                    "fries" -> "🍟"
                    "chicken" -> "🍗"
                    "dessert" -> "🍨"
                    else -> "📁"
                }
                Text(emoji, fontSize = 13.sp)
            }
            Text(
                text = label,
                color = if (isSelected) Color.Black else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun MenuItemGridCard(
    item: MenuItemEntity,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("menu_item_${item.id}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val imageModel = remember(item.imageUrl) {
                item.imageUrl.takeIf { it.isNotBlank() }?.let { url ->
                    if (url.startsWith("/")) java.io.File(url) else url
                }
            }
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
                        fontSize = 36.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${item.categoryName}",
                color = TextMuted,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "৳ ${String.format(Locale.getDefault(), "%.0f", item.price)}",
                color = CurrencyGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
