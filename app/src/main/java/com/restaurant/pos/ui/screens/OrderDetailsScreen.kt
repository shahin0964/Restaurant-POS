package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OrderDetailsScreen(
    viewModel: RestaurantViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedOrder by viewModel.selectedOrderForDetails.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val isUserAdmin = currentUser?.isAdmin() == true
    val canCancelOrder = isUserAdmin || (currentUser?.hasPermission(com.restaurant.pos.data.model.AppPermission.ORDERS_CANCEL) == true)
    val canReceivePayment = isUserAdmin || (currentUser?.hasPermission(com.restaurant.pos.data.model.AppPermission.PAYMENT_RECEIVE) == true)

    var showPaymentConfirmationDialog by remember { mutableStateOf(false) }

    val currentOrderWithItems = selectedOrder

    if (currentOrderWithItems == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            Text("Order not found", color = TextMuted)
        }
        return
    }

    val order = currentOrderWithItems.order
    val items = currentOrderWithItems.items
    val timeStr = remember(order.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
    }
    val isAlreadyPaid = order.isPaid || order.status == "Paid" || order.status == "Completed"

    // Payment Confirmation Dialog
    if (showPaymentConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentConfirmationDialog = false },
            title = { Text(
                    text = "PAYMENT CONFIRMATION",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Confirm payment for this order?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPaymentConfirmationDialog = false
                        viewModel.confirmOrderPayment(
                            orderId = order.id,
                            onSuccess = {
                                Toast.makeText(context, "Order ${order.orderNumber} marked as Paid successfully", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CONFIRM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPaymentConfirmationDialog = false }
                ) {
                    Text("CANCEL", color = TextMuted)
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("order_details_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Order Details",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("order_details_screen")
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
            // Order Number & Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.orderNumber,
                    color = TextPrimary,
                        fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isAlreadyPaid -> StatusReady.copy(alpha = 0.2f)
                                order.status == "Pending" -> StatusPending.copy(alpha = 0.2f)
                                order.status == "Preparing" -> StatusPreparing.copy(alpha = 0.2f)
                                order.status == "Ready" -> StatusReady.copy(alpha = 0.2f)
                                else -> StatusCancelled.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAlreadyPaid) "Paid" else order.status,
                        color = when {
                            isAlreadyPaid -> StatusReady
                            order.status == "Pending" -> StatusPending
                            order.status == "Preparing" -> StatusPreparing
                            order.status == "Ready" -> StatusReady
                            else -> StatusCancelled
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Order Metadata Block
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SummaryRow("Customer Name", order.customerName)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow("Order Type", order.orderType)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow("Table Number", if (order.tableNumber.isNotBlank()) order.tableNumber else "N/A")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow("Time", timeStr)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Items List
            Text("Ordered Items", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    items.forEachIndexed { index, item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.quantity} x ${item.menuItemName}",
                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "৳ ${String.format(Locale.US, "%.0f", item.pricePerUnit)} / unit",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                    if (item.note.isNotBlank()) {
                                        Text(
                                            text = "Note: ${item.note}",
                                            color = CurrencyGold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "৳ ${String.format(Locale.US, "%.0f", item.pricePerUnit * item.quantity)}",
                                    color = CurrencyGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (index < items.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price Breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SummaryRow("Subtotal", "৳ ${String.format(Locale.US, "%.0f", order.subtotal)}")
                    Spacer(modifier = Modifier.height(6.dp))
                    SummaryRow("Discount", "৳ ${String.format(Locale.US, "%.0f", order.discount)}")
                    Spacer(modifier = Modifier.height(6.dp))
                    SummaryRow("Tax", "৳ ${String.format(Locale.US, "%.0f", order.tax)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "৳ ${String.format(Locale.US, "%.0f", order.total)}",
                            color = CurrencyGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryRow("Payment Method", order.paymentMethod)
                    Spacer(modifier = Modifier.height(6.dp))
                    SummaryRow("Payment Status", if (isAlreadyPaid) "Paid" else "Unpaid")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (!isAlreadyPaid && (canCancelOrder || canReceivePayment)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (canCancelOrder) {
                        Button(
                            onClick = {
                                viewModel.updateOrderStatus(order.id, "Cancelled")
                                Toast.makeText(context, "Order Cancelled", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("CANCEL ORDER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (canReceivePayment) {
                        Button(
                            onClick = {
                                showPaymentConfirmationDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("PAYMENT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (isAlreadyPaid) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusReady.copy(alpha = 0.2f))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ORDER PAID & COMPLETED", color = StatusReady, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    viewModel.printCurrentOrder(order.id) { res ->
                        Toast.makeText(context, res.message, if (res.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CurrencyGold)),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = "Print", tint = CurrencyGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("PRINT RECEIPT", color = CurrencyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }


            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}
