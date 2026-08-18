package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.util.Locale

@Composable
fun OrderSummaryScreen(
    viewModel: RestaurantViewModel,
    onOrderPlaced: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val orderType by viewModel.orderType.collectAsState()
    val tableNumber by viewModel.tableNumber.collectAsState()
    val customerName by viewModel.customerName.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val discount by viewModel.discount.collectAsState()
    val tax by viewModel.tax.collectAsState()

    var selectedPaymentMethod by remember { mutableStateOf("Cash") }
    var isPlacing by remember { mutableStateOf(false) }

    val subtotal = viewModel.calculateSubtotal()
    val total = viewModel.calculateTotal()

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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("summary_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Order Summary",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(1.dp, BorderOutline)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        isPlacing = true
                        viewModel.placeOrder(selectedPaymentMethod) { orderId ->
                            isPlacing = false
                            Toast.makeText(context, "Order Placed & Receipt Sent to Printer", Toast.LENGTH_SHORT).show()
                            onOrderPlaced()
                        }
                    },
                    enabled = !isPlacing && cartItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("place_order_btn")
                ) {
                    if (isPlacing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("PLACE ORDER", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("order_summary_screen")
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
            // Summary Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SummaryRow("Order Type", orderType)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow("Table Number", tableNumber)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow("Customer", customerName)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Items List
            Text("Items", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    cartItems.forEachIndexed { index, cartItem ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cartItem.menuItem.name,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "x${cartItem.quantity}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "৳ ${String.format(Locale.US, "%.0f", cartItem.menuItem.price * cartItem.quantity)}",
                                color = CurrencyGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (index < cartItems.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price Totals Breakdown
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SummaryRow("Subtotal", "৳ ${String.format(Locale.US, "%.0f", subtotal)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryRow("Discount", "৳ ${String.format(Locale.US, "%.0f", discount)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryRow("Tax", "৳ ${String.format(Locale.US, "%.0f", tax)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "৳ ${String.format(Locale.US, "%.0f", total)}",
                            color = CurrencyGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Method Selector
            Text("Payment Method", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Cash", "Card", "Mobile").forEach { method ->
                    val isSel = selectedPaymentMethod == method
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) BrandPrimary else DarkSurface)
                            .border(1.dp, if (isSel) BrandPrimary else BorderOutline, RoundedCornerShape(10.dp))
                            .clickable { selectedPaymentMethod = method }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = method,
                            color = if (isSel) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
