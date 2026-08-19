package com.restaurant.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.R
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OrdersScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onViewOrderDetails: (OrderWithItems) -> Unit,
    onBack: () -> Unit
) {
    val allOrders by viewModel.allOrders.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }

    fun isToday(timestamp: Long): Boolean {
        val orderCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()
        return orderCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                orderCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }

    val todayOrders = remember(allOrders) {
        allOrders.filter { isToday(it.order.timestamp) }
    }

    val filteredOrders = remember(selectedFilter, todayOrders) {
        when (selectedFilter) {
            "Pending" -> todayOrders.filter { it.order.status == "Pending" || it.order.status == "Preparing" || it.order.status == "Ready" }
            "Paid" -> todayOrders.filter { it.order.isPaid || it.order.status == "Paid" || it.order.status == "Completed" }
            else -> todayOrders
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("orders_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_orders_list),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "order_list", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("orders_screen")
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
            // Filter Tabs: All, Pending, Paid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderOutline, RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                val filters = listOf(
                    "All" to R.string.filter_all,
                    "Pending" to R.string.status_pending,
                    "Paid" to R.string.status_paid
                )
                filters.forEach { (filterKey, labelRes) ->
                    val isSel = selectedFilter == filterKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) BrandPrimary else Color.Transparent)
                            .clickable { selectedFilter = filterKey }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            color = if (isSel) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredOrders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.msg_no_orders_found), color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredOrders,
                        key = { it.order.id },
                        contentType = { "order_row" }
                    ) { orderWithItems ->
                        OrderRowCard(
                            orderWithItems = orderWithItems,
                            onClick = {
                                viewModel.setSelectedOrderDetails(orderWithItems)
                                onViewOrderDetails(orderWithItems)
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
fun OrderRowCard(
    orderWithItems: OrderWithItems,
    onClick: () -> Unit
) {
    val order = orderWithItems.order
    val items = orderWithItems.items
    val timeStr = remember(order.timestamp) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
    }
    val isPaid = order.isPaid || order.status == "Paid" || order.status == "Completed"

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("order_row_${order.orderNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Order Number, Order Type, Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = order.orderNumber,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${order.orderType}${if (order.tableNumber.isNotBlank()) " • " + stringResource(R.string.lbl_table_number, order.tableNumber) else ""}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeStr,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Customer Name & Items Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (order.customerName.isNotBlank()) stringResource(R.string.lbl_customer_name, order.customerName) else stringResource(R.string.lbl_walkin_customer),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lbl_items_count, items.size),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)

            // Footer Row: Total Amount, Status Badge & Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.lbl_total_amount), color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "৳ ${String.format(Locale.getDefault(), "%.0f", order.total)}",
                        color = CurrencyGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isPaid -> StatusReady.copy(alpha = 0.2f)
                                    order.status == "Pending" -> StatusPending.copy(alpha = 0.2f)
                                    order.status == "Preparing" -> StatusPreparing.copy(alpha = 0.2f)
                                    order.status == "Ready" -> StatusReady.copy(alpha = 0.2f)
                                    else -> StatusCancelled.copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val statusLabel = when {
                            isPaid -> stringResource(R.string.status_paid)
                            order.status == "Pending" -> stringResource(R.string.status_pending)
                            order.status == "Preparing" -> stringResource(R.string.status_preparing)
                            order.status == "Ready" -> stringResource(R.string.status_ready)
                            else -> order.status
                        }
                        Text(
                            text = statusLabel,
                            color = when {
                                isPaid -> StatusReady
                                order.status == "Pending" -> StatusPending
                                order.status == "Preparing" -> StatusPreparing
                                order.status == "Ready" -> StatusReady
                                else -> StatusCancelled
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
