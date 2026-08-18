package com.restaurant.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class AnalyticsDateFilter(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

data class TopSellingItemData(
    val menuItemId: Long,
    val menuItemName: String,
    val categoryName: String,
    val quantitySold: Int,
    val grossSales: Double,
    val discountsApplied: Double,
    val netSales: Double,
    val orderCount: Int
)

data class SalesByDayData(
    val dateLabel: String,
    val orderCount: Int,
    val totalSales: Double
)

data class SalesByHourData(
    val hourLabel: String,
    val orderCount: Int,
    val totalSales: Double
)

data class PaymentMethodAnalyticsData(
    val method: String,
    val orderCount: Int,
    val totalAmount: Double
)

data class CategoryAnalyticsData(
    val categoryName: String,
    val quantitySold: Int,
    val totalSales: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesAnalyticsScreen(
    viewModel: RestaurantViewModel,
    onBack: () -> Unit
) {
    val allOrders by viewModel.allOrders.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var selectedFilter by remember { mutableStateOf(AnalyticsDateFilter.TODAY) }

    var customStartDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var customEndDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    var showPickerForStart by remember { mutableStateOf(false) }
    var showPickerForEnd by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var selectedItemForDetail by remember { mutableStateOf<TopSellingItemData?>(null) }

    // Map MenuItems to Category Names
    val menuItemCategoryMap = remember(menuItems) {
        menuItems.associate { it.id to it.categoryName }
    }

    // Calculate Date Range Bounds (StartMs, EndMs)
    val (startMs, endMs) = remember(selectedFilter, customStartDateMillis, customEndDateMillis) {
        val cal = Calendar.getInstance()
        when (selectedFilter) {
            AnalyticsDateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val e = cal.timeInMillis
                Pair(s, e)
            }
            AnalyticsDateFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val e = cal.timeInMillis
                Pair(s, e)
            }
            AnalyticsDateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val e = cal.timeInMillis
                Pair(s, e)
            }
            AnalyticsDateFilter.THIS_YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val s = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val e = cal.timeInMillis
                Pair(s, e)
            }
            AnalyticsDateFilter.CUSTOM -> {
                val sCal = Calendar.getInstance().apply { timeInMillis = customStartDateMillis }
                sCal.set(Calendar.HOUR_OF_DAY, 0)
                sCal.set(Calendar.MINUTE, 0)
                sCal.set(Calendar.SECOND, 0)
                sCal.set(Calendar.MILLISECOND, 0)
                val s = sCal.timeInMillis

                val eCal = Calendar.getInstance().apply { timeInMillis = customEndDateMillis }
                eCal.set(Calendar.HOUR_OF_DAY, 23)
                eCal.set(Calendar.MINUTE, 59)
                eCal.set(Calendar.SECOND, 59)
                eCal.set(Calendar.MILLISECOND, 999)
                val e = eCal.timeInMillis
                Pair(s, e)
            }
        }
    }

    // Filter Valid Orders based on date range and Shop Report rule (status != "Cancelled")
    val validOrders = remember(allOrders, startMs, endMs) {
        allOrders.filter {
            it.order.status != "Cancelled" && it.order.timestamp in startMs..endMs
        }
    }

    // Summary Metrics
    val totalSales = remember(validOrders) { validOrders.sumOf { it.order.total } }
    val totalOrdersCount = remember(validOrders) { validOrders.size }
    val totalItemsSold = remember(validOrders) { validOrders.flatMap { it.items }.sumOf { it.quantity } }
    val avgOrderValue = remember(totalSales, totalOrdersCount) {
        if (totalOrdersCount > 0) totalSales / totalOrdersCount else 0.0
    }

    // Discount Impact Metrics
    val totalDiscountGiven = remember(validOrders) { validOrders.sumOf { it.order.discount } }
    val discountedOrdersCount = remember(validOrders) { validOrders.count { it.order.discount > 0 } }

    // Top Selling Items Aggregation
    val allTopSellingItems = remember(validOrders, menuItemCategoryMap) {
        val itemMap = mutableMapOf<String, MutableList<Pair<OrderWithItems, com.restaurant.pos.data.db.OrderItemEntity>>>()
        validOrders.forEach { orderWithItems ->
            orderWithItems.items.forEach { item ->
                itemMap.getOrPut(item.menuItemName) { mutableListOf() }.add(Pair(orderWithItems, item))
            }
        }

        itemMap.map { (itemName, pairList) ->
            val firstPair = pairList.first()
            val menuItemId = firstPair.second.menuItemId
            val catName = menuItemCategoryMap[menuItemId] ?: "Uncategorized"
            val qty = pairList.sumOf { it.second.quantity }
            val gross = pairList.sumOf { it.second.quantity * it.second.pricePerUnit }
            val discountShare = pairList.sumOf { (orderWithItems, item) ->
                val order = orderWithItems.order
                val itemGross = item.quantity * item.pricePerUnit
                if (order.subtotal > 0) (itemGross / order.subtotal) * order.discount else 0.0
            }
            val net = gross - discountShare
            val distinctOrders = pairList.map { it.first.order.id }.distinct().size

            TopSellingItemData(
                menuItemId = menuItemId,
                menuItemName = itemName,
                categoryName = catName,
                quantitySold = qty,
                grossSales = gross,
                discountsApplied = discountShare,
                netSales = net,
                orderCount = distinctOrders
            )
        }.sortedByDescending { it.quantitySold }
    }

    // Filtered Top Selling Items
    val filteredTopSellingItems = remember(allTopSellingItems, searchQuery, selectedCategoryFilter) {
        allTopSellingItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.menuItemName.contains(searchQuery, ignoreCase = true) ||
                    item.categoryName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "All" ||
                    item.categoryName.equals(selectedCategoryFilter, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    // Best Sales Day Calculation
    val bestSalesDay = remember(validOrders) {
        if (validOrders.isEmpty()) null
        else {
            val dayMap = validOrders.groupBy {
                SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(it.order.timestamp))
            }
            val bestEntry = dayMap.maxByOrNull { entry -> entry.value.sumOf { it.order.total } }
            bestEntry?.let { entry ->
                Triple(entry.key, entry.value.sumOf { it.order.total }, entry.value.size)
            }
        }
    }

    // Busiest Sales Time Calculation (1-Hour Intervals)
    val busiestSalesTime = remember(validOrders) {
        if (validOrders.isEmpty()) null
        else {
            val hourMap = validOrders.groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.order.timestamp }
                cal.get(Calendar.HOUR_OF_DAY)
            }
            val busiestEntry = hourMap.maxByOrNull { entry -> entry.value.size }
            busiestEntry?.let { entry ->
                val hour = entry.key
                val startLabel = String.format(Locale.US, "%02d:00 %s", if (hour % 12 == 0) 12 else hour % 12, if (hour >= 12) "PM" else "AM")
                val endHour = (hour + 1) % 24
                val endLabel = String.format(Locale.US, "%02d:00 %s", if (endHour % 12 == 0) 12 else endHour % 12, if (endHour >= 12) "PM" else "AM")
                val label = "$startLabel – $endLabel"
                Triple(label, entry.value.size, entry.value.sumOf { it.order.total })
            }
        }
    }

    // Sales By Day List
    val salesByDayList = remember(validOrders) {
        if (validOrders.isEmpty()) emptyList()
        else {
            validOrders.groupBy {
                SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(it.order.timestamp))
            }.map { (dateStr, orders) ->
                SalesByDayData(
                    dateLabel = dateStr,
                    orderCount = orders.size,
                    totalSales = orders.sumOf { it.order.total }
                )
            }.sortedByDescending { it.totalSales }
        }
    }

    // Sales By Hour List (Active Hours)
    val salesByHourList = remember(validOrders) {
        if (validOrders.isEmpty()) emptyList()
        else {
            validOrders.groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.order.timestamp }
                cal.get(Calendar.HOUR_OF_DAY)
            }.map { (hour, orders) ->
                val startLabel = String.format(Locale.US, "%02d:00 %s", if (hour % 12 == 0) 12 else hour % 12, if (hour >= 12) "PM" else "AM")
                val endHour = (hour + 1) % 24
                val endLabel = String.format(Locale.US, "%02d:00 %s", if (endHour % 12 == 0) 12 else endHour % 12, if (endHour >= 12) "PM" else "AM")
                SalesByHourData(
                    hourLabel = "$startLabel – $endLabel",
                    orderCount = orders.size,
                    totalSales = orders.sumOf { it.order.total }
                )
            }.sortedBy { it.hourLabel }
        }
    }

    // Payment Method Analytics
    val paymentMethodList = remember(validOrders) {
        if (validOrders.isEmpty()) emptyList()
        else {
            validOrders.groupBy { it.order.paymentMethod }
                .map { (method, orders) ->
                    PaymentMethodAnalyticsData(
                        method = method.ifBlank { "Unspecified" },
                        orderCount = orders.size,
                        totalAmount = orders.sumOf { it.order.total }
                    )
                }.sortedByDescending { it.totalAmount }
        }
    }

    // Product Category Analytics
    val categoryAnalyticsList = remember(validOrders, menuItemCategoryMap) {
        if (validOrders.isEmpty()) emptyList()
        else {
            val catMap = mutableMapOf<String, Pair<Int, Double>>()
            validOrders.flatMap { it.items }.forEach { item ->
                val catName = menuItemCategoryMap[item.menuItemId] ?: "Uncategorized"
                val current = catMap.getOrDefault(catName, Pair(0, 0.0))
                val itemTotal = item.quantity * item.pricePerUnit
                catMap[catName] = Pair(current.first + item.quantity, current.second + itemTotal)
            }
            catMap.map { (cat, pair) ->
                CategoryAnalyticsData(categoryName = cat, quantitySold = pair.first, totalSales = pair.second)
            }.sortedByDescending { it.totalSales }
        }
    }

    val availableCategoriesList = remember(categories, allTopSellingItems) {
        listOf("All") + categories.map { it.name }.union(allTopSellingItems.map { it.categoryName }).distinct().filter { it.isNotBlank() }
    }

    val dateRangeDisplayString = remember(selectedFilter, startMs, endMs) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        when (selectedFilter) {
            AnalyticsDateFilter.TODAY -> "Today • ${sdf.format(Date(startMs))}"
            AnalyticsDateFilter.THIS_WEEK -> "This Week (${sdf.format(Date(startMs))} - ${sdf.format(Date(endMs))})"
            AnalyticsDateFilter.THIS_MONTH -> "This Month (${SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(startMs))})"
            AnalyticsDateFilter.THIS_YEAR -> "This Year (${SimpleDateFormat("yyyy", Locale.US).format(Date(startMs))})"
            AnalyticsDateFilter.CUSTOM -> "${sdf.format(Date(startMs))} – ${sdf.format(Date(endMs))}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SALES ANALYTICS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = dateRangeDisplayString,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("sales_analytics_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("sales_analytics_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. DATE RANGE SELECTOR BAR
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DATE RANGE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = AnalyticsDateFilter.entries,
                            key = { it.name },
                            contentType = { "date_filter" }
                        ) { filter ->
                            val isSel = selectedFilter == filter
                            Surface(
                                onClick = {
                                    if (filter == AnalyticsDateFilter.CUSTOM) {
                                        showCustomDateDialog = true
                                    } else {
                                        selectedFilter = filter
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSel) CurrencyGold else DarkSurface,
                                border = if (isSel) null else androidx.compose.foundation.BorderStroke(1.dp, BorderOutline),
                                modifier = Modifier.testTag("date_filter_${filter.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (filter == AnalyticsDateFilter.CUSTOM) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = if (isSel) Color.Black else CurrencyGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = filter.label,
                                        color = if (isSel) Color.Black else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. SEARCH & CATEGORY FILTER BAR
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter items or categories...", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_analytics_input")
                    )

                    if (availableCategoriesList.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = availableCategoriesList,
                                key = { it },
                                contentType = { "category_filter" }
                            ) { cat ->
                                val isCatSel = selectedCategoryFilter == cat
                                FilterChip(
                                    selected = isCatSel,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CurrencyGold,
                                        selectedLabelColor = Color.Black,
                                        containerColor = DarkSurface,
                                        labelColor = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. SALES SUMMARY CARDS (4 Grid Cards)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetricCard(
                            title = "TOTAL SALES",
                            value = "৳ ${String.format(Locale.US, "%,.2f", totalSales)}",
                            icon = Icons.Default.PointOfSale,
                            accentColor = StatusReady,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "TOTAL ORDERS",
                            value = "$totalOrdersCount",
                            icon = Icons.Default.ReceiptLong,
                            accentColor = StatusPreparing,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetricCard(
                            title = "ITEMS SOLD",
                            value = "$totalItemsSold",
                            icon = Icons.Default.Fastfood,
                            accentColor = CurrencyGold,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "AVERAGE ORDER",
                            value = "৳ ${String.format(Locale.US, "%,.2f", avgOrderValue)}",
                            icon = Icons.Default.Analytics,
                            accentColor = StatusPending,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // IF NO DATA FOR SELECTED PERIOD
            if (validOrders.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("empty_analytics_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No sales data available for this period.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {

                // 4. TOP SELLING ITEMS
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🏆 ", fontSize = 16.sp)
                                    Text(
                                        text = "TOP SELLING ITEMS",
                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${filteredTopSellingItems.size} items",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (filteredTopSellingItems.isEmpty()) {
                                Text(
                                    text = "No items match search filter.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredTopSellingItems.forEachIndexed { index, item ->
                                        Surface(
                                            onClick = { selectedItemForDetail = item },
                                            shape = RoundedCornerShape(8.dp),
                                            color = DarkSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Surface(
                                                        color = if (index == 0) CurrencyGold.copy(alpha = 0.2f) else DarkBackground,
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "${index + 1}",
                                                                color = if (index == 0) CurrencyGold else TextSecondary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column {
                                                        Text(
                            text = item.menuItemName,
                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${item.quantitySold} sold • ${item.categoryName}",
                                                            color = TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "৳ ${String.format(Locale.US, "%,.2f", item.netSales)}",
                                                        color = CurrencyGold,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "${item.orderCount} orders",
                                                        color = TextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. BEST SALES DAY & BUSIEST SALES TIME
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Best Sales Day Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📅 ", fontSize = 14.sp)
                                    Text(
                                        text = "BEST SALES DAY",
                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (bestSalesDay != null) {
                                    Text(
                                        text = bestSalesDay.first,
                                        color = CurrencyGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "৳ ${String.format(Locale.US, "%,.2f", bestSalesDay.second)}",
                                        color = TextPrimary,
                            fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${bestSalesDay.third} orders",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = "No sales data available.",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Busiest Sales Time Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⏰ ", fontSize = 14.sp)
                                    Text(
                                        text = "BUSIEST TIME",
                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (busiestSalesTime != null) {
                                    Text(
                                        text = busiestSalesTime.first,
                                        color = StatusPreparing,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${busiestSalesTime.second} orders",
                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.US, "%,.2f", busiestSalesTime.third)}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = "No sales data available.",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. SALES BY DAY
                if (salesByDayList.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📊 ", fontSize = 16.sp)
                                    Text(
                                        text = "SALES BY DAY",
                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val maxDaySales = salesByDayList.maxOf { it.totalSales }.coerceAtLeast(1.0)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    salesByDayList.forEach { dayData ->
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                            text = dayData.dateLabel,
                            color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${dayData.orderCount} orders • ",
                                                        color = TextMuted,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "৳ ${String.format(Locale.US, "%,.2f", dayData.totalSales)}",
                                                        color = StatusReady,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { (dayData.totalSales / maxDaySales).toFloat() },
                                                color = StatusReady,
                                                trackColor = DarkSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. SALES BY HOUR
                if (salesByHourList.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⏰ ", fontSize = 16.sp)
                                    Text(
                                        text = "SALES BY HOUR",
                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val maxHourSales = salesByHourList.maxOf { it.totalSales }.coerceAtLeast(1.0)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    salesByHourList.forEach { hourData ->
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                            text = hourData.hourLabel,
                            color = TextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${hourData.orderCount} orders • ",
                                                        color = TextMuted,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "৳ ${String.format(Locale.US, "%,.2f", hourData.totalSales)}",
                                                        color = StatusPreparing,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { (hourData.totalSales / maxHourSales).toFloat() },
                                                color = StatusPreparing,
                                                trackColor = DarkSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. PAYMENT METHOD ANALYTICS & PRODUCT CATEGORY ANALYTICS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Payment Method Analytics Card
                        if (paymentMethodList.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💳 ", fontSize = 14.sp)
                                        Text(
                                            text = "PAYMENT METHODS",
                        color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        paymentMethodList.forEach { pm ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                            text = pm.method,
                            color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${pm.orderCount} orders",
                                                        color = TextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Text(
                                                    text = "৳ ${String.format(Locale.US, "%,.0f", pm.totalAmount)}",
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

                        // Product Category Analytics Card
                        if (categoryAnalyticsList.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏷️ ", fontSize = 14.sp)
                                        Text(
                                            text = "CATEGORIES",
                        color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        categoryAnalyticsList.forEach { cat ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                            text = cat.categoryName,
                            color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${cat.quantitySold} sold",
                                                        color = TextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Text(
                                                    text = "৳ ${String.format(Locale.US, "%,.0f", cat.totalSales)}",
                                                    color = StatusReady,
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
                }

                // 9. DISCOUNT IMPACT CARD
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(StatusPending.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎁", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "TOTAL DISCOUNTS GIVEN",
                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$discountedOrdersCount discounted orders in period",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Text(
                                text = "৳ ${String.format(Locale.US, "%,.2f", totalDiscountGiven)}",
                                color = StatusPending,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ITEM SALES DETAILS DIALOG
    selectedItemForDetail?.let { itemDetail ->
        AlertDialog(
            onDismissRequest = { selectedItemForDetail = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CurrencyGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📦", fontSize = 22.sp)
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                            text = itemDetail.menuItemName,
                            color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = itemDetail.categoryName,
                        color = CurrencyGold,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DetailRow("Quantity Sold", "${itemDetail.quantitySold} units")
                    DetailRow("Number of Orders", "${itemDetail.orderCount} orders")
                    HorizontalDivider(color = BorderOutline)
                    DetailRow("Gross Sales", "৳ ${String.format(Locale.US, "%,.2f", itemDetail.grossSales)}")
                    DetailRow("Discounts Applied", "- ৳ ${String.format(Locale.US, "%,.2f", itemDetail.discountsApplied)}")
                    HorizontalDivider(color = BorderOutline)
                    DetailRow("Net Sales", "৳ ${String.format(Locale.US, "%,.2f", itemDetail.netSales)}", isHighlighted = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForDetail = null }) {
                    Text("CLOSE", color = CurrencyGold, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }

    // CUSTOM DATE RANGE SELECTION DIALOG
    if (showCustomDateDialog) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = { Text(
                    text = "SELECT CUSTOM DATE RANGE",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Start Date Button
                    OutlinedButton(
                        onClick = { showPickerForStart = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start Date:", color = TextSecondary, fontSize = 12.sp)
                            Text(sdf.format(Date(customStartDateMillis)), color = CurrencyGold, fontWeight = FontWeight.Bold)
                        }
                    }

                    // End Date Button
                    OutlinedButton(
                        onClick = { showPickerForEnd = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("End Date:", color = TextSecondary, fontSize = 12.sp)
                            Text(sdf.format(Date(customEndDateMillis)), color = CurrencyGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFilter = AnalyticsDateFilter.CUSTOM
                        showCustomDateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold)
                ) {
                    Text("APPLY FILTER", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // DATE PICKER FOR START DATE
    if (showPickerForStart) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customStartDateMillis)
        DatePickerDialog(
            onDismissRequest = { showPickerForStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            customStartDateMillis = it
                        }
                        showPickerForStart = false
                    }
                ) {
                    Text("OK", color = CurrencyGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPickerForStart = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    headlineContentColor = CurrencyGold,
                    dayContentColor = TextPrimary,
                    selectedDayContainerColor = CurrencyGold,
                    selectedDayContentColor = Color.Black
                )
            )
        }
    }

    // DATE PICKER FOR END DATE
    if (showPickerForEnd) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = customEndDateMillis)
        DatePickerDialog(
            onDismissRequest = { showPickerForEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            customEndDateMillis = it
                        }
                        showPickerForEnd = false
                    }
                ) {
                    Text("OK", color = CurrencyGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPickerForEnd = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    headlineContentColor = CurrencyGold,
                    dayContentColor = TextPrimary,
                    selectedDayContainerColor = CurrencyGold,
                    selectedDayContentColor = Color.Black
                )
            )
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                            text = value,
                            color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isHighlighted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isHighlighted) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            color = if (isHighlighted) CurrencyGold else TextPrimary,
            fontSize = if (isHighlighted) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
