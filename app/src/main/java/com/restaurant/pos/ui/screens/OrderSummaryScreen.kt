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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.R
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
    val effectiveTaxRate by viewModel.effectiveTaxRate.collectAsState()

    var selectedPaymentMethod by remember { mutableStateOf("Cash") }
    var isPlacing by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var discountInputText by remember { mutableStateOf("") }
    var showTaxDialog by remember { mutableStateOf(false) }
    var taxInputText by remember { mutableStateOf("") }

    val subtotal = remember(cartItems) { viewModel.calculateSubtotal() }
    val total = remember(cartItems, discount, tax) { viewModel.calculateTotal() }

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
                        text = stringResource(R.string.title_order_summary),
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
                val toastMsg = stringResource(R.string.msg_order_placed_receipt)
                Button(
                    onClick = {
                        isPlacing = true
                        viewModel.placeOrder(selectedPaymentMethod) { orderId ->
                            isPlacing = false
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            onOrderPlaced()
                        }
                    },
                    enabled = !isPlacing && cartItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlacing) Color.White else BrandPrimary,
                        contentColor = if (isPlacing) Color.Black else Color.White,
                        disabledContainerColor = if (isPlacing) Color.White else DarkSurfaceVariant,
                        disabledContentColor = if (isPlacing) Color.Black else TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("place_order_btn")
                ) {
                    if (isPlacing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Placing Order...",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.btn_place_order),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
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
                    SummaryRow(stringResource(R.string.lbl_order_type), orderType)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow(stringResource(R.string.lbl_table_number), if (tableNumber.isNotBlank()) tableNumber else "N/A")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow(stringResource(R.string.lbl_customer_name), if (customerName.isNotBlank()) customerName else stringResource(R.string.lbl_walkin_customer))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Items List
            Text(stringResource(R.string.lbl_ordered_items), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                            val itemTotal = cartItem.menuItem.price * cartItem.quantity
                            Text(
                                text = "৳ ${formatAmount(itemTotal)}",
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
                    SummaryRow(stringResource(R.string.lbl_subtotal), "৳ ${formatAmount(subtotal)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                discountInputText = if (discount > 0.0) formatAmount(discount) else ""
                                showDiscountDialog = true
                            }
                            .padding(vertical = 2.dp)
                            .testTag("order_discount_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val discountPercentage = if (subtotal > 0.0) (discount / subtotal) * 100.0 else 0.0
                        val pctFormatted = if (discountPercentage % 1.0 == 0.0) {
                            String.format(Locale.US, "%.0f%%", discountPercentage)
                        } else {
                            String.format(Locale.US, "%.1f%%", discountPercentage)
                        }
                        val discountLabel = if (discount > 0.0 && discountPercentage > 0.0) {
                            "${stringResource(R.string.lbl_discount)} ($pctFormatted)"
                        } else {
                            stringResource(R.string.lbl_discount)
                        }
                        val discountValueText = if (discount > 0.0) {
                            "-৳${formatAmount(discount)}"
                        } else {
                            "৳0"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = discountLabel,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (discount > 0.0) "(Edit)" else "(Add manual)",
                                color = CurrencyGold.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = discountValueText,
                            color = if (discount > 0.0) CurrencyGold else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val taxPctFormatted = if (effectiveTaxRate % 1.0 == 0.0) {
                        String.format(Locale.US, "%.0f%%", effectiveTaxRate)
                    } else {
                        String.format(Locale.US, "%.1f%%", effectiveTaxRate)
                    }
                    val vatLabel = if (tax > 0.0 || effectiveTaxRate > 0.0) {
                        "${stringResource(R.string.lbl_vat)} ($taxPctFormatted)"
                    } else {
                        stringResource(R.string.lbl_vat)
                    }
                    val vatValueText = if (tax > 0.0) {
                        "+৳${formatAmount(tax)}"
                    } else {
                        "৳0"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                taxInputText = if (effectiveTaxRate > 0.0) {
                                    if (effectiveTaxRate % 1.0 == 0.0) String.format(Locale.US, "%.0f", effectiveTaxRate)
                                    else String.format(Locale.US, "%.2f", effectiveTaxRate)
                                } else ""
                                showTaxDialog = true
                            }
                            .padding(vertical = 2.dp)
                            .testTag("order_vat_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = vatLabel,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (effectiveTaxRate > 0.0 || tax > 0.0) "(Edit)" else "(Add VAT)",
                                color = CurrencyGold.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = vatValueText,
                            color = if (tax > 0.0) CurrencyGold else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.lbl_total), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "৳ ${formatAmount(total)}",
                            color = CurrencyGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Method Selector
            Text(stringResource(R.string.lbl_payment_method), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val methods = listOf(
                    "Cash" to stringResource(R.string.pay_cash),
                    "Card" to stringResource(R.string.pay_card),
                    "Mobile" to stringResource(R.string.pay_mobile)
                )
                methods.forEach { (methodKey, displayLabel) ->
                    val isSel = selectedPaymentMethod == methodKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) BrandPrimary else DarkSurface)
                            .border(1.dp, if (isSel) BrandPrimary else BorderOutline, RoundedCornerShape(10.dp))
                            .clickable { selectedPaymentMethod = methodKey }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayLabel,
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

    if (showDiscountDialog) {
        AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = {
                Text(
                    text = "Manual Discount",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter discount amount in ৳ (default is 0.00):",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = discountInputText,
                        onValueChange = { discountInputText = it },
                        placeholder = { Text("0.00", color = TextMuted) },
                        prefix = { Text("৳ ", color = CurrencyGold, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_discount_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = discountInputText.toDoubleOrNull() ?: 0.0
                        viewModel.setDiscount(parsed.coerceAtLeast(0.0))
                        showDiscountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CurrencyGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("apply_discount_btn")
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.setDiscount(0.0)
                            showDiscountDialog = false
                        },
                        modifier = Modifier.testTag("clear_discount_btn")
                    ) {
                        Text("Clear (0.00)", color = StatusCancelled)
                    }
                    TextButton(
                        onClick = { showDiscountDialog = false },
                        modifier = Modifier.testTag("cancel_discount_btn")
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showTaxDialog) {
        AlertDialog(
            onDismissRequest = { showTaxDialog = false },
            title = {
                Text(
                    text = "VAT / Tax Rate (%)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select preset or enter custom VAT percentage:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    // Quick Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.0, 5.0, 7.5, 10.0, 15.0).forEach { preset ->
                            val label = if (preset == 0.0) "0%" else if (preset % 1.0 == 0.0) "${preset.toInt()}%" else "$preset%"
                            val isSelected = taxInputText.toDoubleOrNull() == preset
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CurrencyGold else DarkSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        taxInputText = if (preset % 1.0 == 0.0) "${preset.toInt()}" else "$preset"
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = taxInputText,
                        onValueChange = { taxInputText = it },
                        placeholder = { Text("0.00", color = TextMuted) },
                        suffix = { Text("%", color = CurrencyGold, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_tax_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = taxInputText.toDoubleOrNull() ?: 0.0
                        viewModel.setCustomTaxRate(parsed.coerceIn(0.0, 100.0))
                        showTaxDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CurrencyGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("apply_tax_btn")
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.setCustomTaxRate(null)
                            showTaxDialog = false
                        },
                        modifier = Modifier.testTag("reset_tax_btn")
                    ) {
                        Text("Reset Default", color = StatusCancelled)
                    }
                    TextButton(
                        onClick = { showTaxDialog = false },
                        modifier = Modifier.testTag("cancel_tax_btn")
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
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

private fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", amount)
    } else {
        String.format(Locale.US, "%.2f", amount)
    }
}
