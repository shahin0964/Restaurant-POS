package com.restaurant.pos.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
fun KitchenViewScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onViewOrderDetails: (OrderWithItems) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pendingList by viewModel.pendingOrders.collectAsState()
    val preparingList by viewModel.preparingOrders.collectAsState()
    val readyList by viewModel.readyOrders.collectAsState()

    var selectedTab by remember { mutableStateOf("Pending") }
    var orderToConfirmPayment by remember { mutableStateOf<OrderWithItems?>(null) }

    val currentOrders = when (selectedTab) {
        "Pending" -> pendingList
        "Preparing" -> preparingList
        else -> readyList
    }

    // Payment Confirmation Dialog
    orderToConfirmPayment?.let { orderWithItems ->
        val order = orderWithItems.order
        AlertDialog(
            onDismissRequest = { orderToConfirmPayment = null },
            title = {
                Text(
                    text = stringResource(R.string.title_confirm_payment),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.msg_confirm_payment, order.orderNumber, String.format(Locale.getDefault(), "%.0f", order.total)),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentConfirmOrder = orderToConfirmPayment
                        orderToConfirmPayment = null
                        if (currentConfirmOrder != null) {
                            viewModel.confirmOrderPayment(
                                orderId = currentConfirmOrder.order.id,
                                onSuccess = {
                                    Toast.makeText(context, "Order ${currentConfirmOrder.order.orderNumber} marked as Paid", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.btn_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { orderToConfirmPayment = null }
                ) {
                    Text(stringResource(R.string.btn_cancel), color = TextMuted)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("kitchen_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_kitchen_view),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedTab == "Pending" || selectedTab == "Preparing") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { viewModel.markAllAsReady() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("mark_all_ready_btn")
                        ) {
                            Text(stringResource(R.string.btn_mark_all_ready), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                BottomNavBar(currentRoute = "kitchen", onNavigate = onNavigate)
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("kitchen_view_screen")
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
            // Status Tabs [Pending (5)] [Preparing (2)] [Ready (1)]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderOutline, RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                val tabs = listOf(
                    Triple("Pending", R.string.status_pending, pendingList.size),
                    Triple("Preparing", R.string.status_preparing, preparingList.size),
                    Triple("Ready", R.string.status_ready, readyList.size)
                )
                tabs.forEach { (tabKey, labelRes, count) ->
                    val isSel = selectedTab == tabKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) BrandPrimary else Color.Transparent)
                            .clickable { selectedTab = tabKey }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stringResource(labelRes)} ($count)",
                            color = if (isSel) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Order Tickets List
            if (currentOrders.isEmpty()) {
                val currentTabLabel = when (selectedTab) {
                    "Pending" -> stringResource(R.string.status_pending)
                    "Preparing" -> stringResource(R.string.status_preparing)
                    else -> stringResource(R.string.status_ready)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.msg_no_orders_status, currentTabLabel), color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = currentOrders,
                        key = { it.order.id },
                        contentType = { "kitchen_ticket" }
                    ) { orderWithItems ->
                        KitchenTicketCard(
                            orderWithItems = orderWithItems,
                            onStatusChange = { newStatus ->
                                viewModel.updateOrderStatus(orderWithItems.order.id, newStatus)
                            },
                            onPayClick = {
                                orderToConfirmPayment = orderWithItems
                            },
                            onClick = { onViewOrderDetails(orderWithItems) }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun KitchenTicketCard(
    orderWithItems: OrderWithItems,
    onStatusChange: (String) -> Unit,
    onPayClick: () -> Unit,
    onClick: () -> Unit
) {
    val order = orderWithItems.order
    val items = orderWithItems.items
    val timeStr = remember(order.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: #1058, Type, Time
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
                        text = "${order.orderType} • ${if (order.tableNumber.isNotBlank()) order.tableNumber else order.customerName}",
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)

            // Items List
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.quantity} x ",
                        color = CurrencyGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.menuItemName,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Total Amount & Status Action
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                when (order.status) {
                    "Pending" -> {
                        Button(
                            onClick = { onStatusChange("Preparing") },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusPreparing),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(stringResource(R.string.btn_start_preparing), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "Preparing" -> {
                        Button(
                            onClick = { onStatusChange("Ready") },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusReady),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(stringResource(R.string.btn_mark_as_ready), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "Ready" -> {
                        Button(
                            onClick = { onPayClick() },
                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(stringResource(R.string.btn_paid), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StatusReady.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.btn_paid), color = StatusReady, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
