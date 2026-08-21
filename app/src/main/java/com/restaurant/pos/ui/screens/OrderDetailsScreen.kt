package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.restaurant.pos.R
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.ui.navigation.Routes
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.utils.PosFeedbackHelper
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OrderDetailsScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
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
    var showAddItemsBottomSheet by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isAddItemPressed by remember { mutableStateOf(false) }
    var isCancelPressed by remember { mutableStateOf(false) }
    var isPayPressed by remember { mutableStateOf(false) }
    var isPrintPressed by remember { mutableStateOf(false) }

    val currentOrderWithItems = selectedOrder

    if (currentOrderWithItems == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.msg_order_not_found), color = TextMuted)
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
                    text = stringResource(R.string.title_confirm_payment),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.msg_confirm_payment_order),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PosFeedbackHelper.triggerVibration(context, 80)
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
                    Text(stringResource(R.string.btn_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPaymentConfirmationDialog = false }
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("order_details_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.title_order_details),
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
                    val statusLabel = when {
                        isAlreadyPaid -> stringResource(R.string.status_paid)
                        order.status == "Pending" -> stringResource(R.string.status_pending)
                        order.status == "Preparing" -> stringResource(R.string.status_preparing)
                        order.status == "Ready" -> stringResource(R.string.status_ready)
                        else -> order.status
                    }
                    Text(
                        text = statusLabel,
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
                    SummaryRow(stringResource(R.string.lbl_customer_name), if (order.customerName.isNotBlank()) order.customerName else stringResource(R.string.lbl_walkin_customer))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow(stringResource(R.string.lbl_order_type), order.orderType)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow(stringResource(R.string.lbl_table_number), if (order.tableNumber.isNotBlank()) order.tableNumber else "N/A")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderOutline)
                    SummaryRow(stringResource(R.string.lbl_time), timeStr)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Items List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.lbl_ordered_items), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                
                if (!isAlreadyPaid && order.status != "Cancelled") {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isAddItemPressed = true
                                PosFeedbackHelper.triggerVibration(context, 70)
                                delay(180)
                                isAddItemPressed = false
                                viewModel.setIsAddingToOrder(true)
                                showAddItemsBottomSheet = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAddItemPressed) Color.White else CurrencyGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("order_details_add_item_btn")
                    ) {
                        Text(
                            "+ ADD",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
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
                                        text = "৳ ${formatAmount(item.pricePerUnit)} / unit",
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
                                val itemLineTotal = item.pricePerUnit * item.quantity
                                Text(
                                    text = "৳ ${formatAmount(itemLineTotal)}",
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
                    SummaryRow(stringResource(R.string.lbl_subtotal), "৳ ${formatAmount(order.subtotal)}")
                    Spacer(modifier = Modifier.height(6.dp))
                    val discountPercentage = if (order.subtotal > 0.0) (order.discount / order.subtotal) * 100.0 else 0.0
                    val pctFormatted = if (discountPercentage % 1.0 == 0.0) {
                        String.format(Locale.US, "%.0f%%", discountPercentage)
                    } else {
                        String.format(Locale.US, "%.1f%%", discountPercentage)
                    }
                    val discountLabel = if (order.discount > 0.0 && discountPercentage > 0.0) {
                        "${stringResource(R.string.lbl_discount)} ($pctFormatted)"
                    } else {
                        stringResource(R.string.lbl_discount)
                    }
                    val discountValueText = if (order.discount > 0.0) {
                        "-৳${formatAmount(order.discount)}"
                    } else {
                        "৳0"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_details_discount_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = discountLabel, color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = discountValueText,
                            color = if (order.discount > 0.0) CurrencyGold else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val netOld = (order.subtotal - order.discount).coerceAtLeast(1.0)
                    val taxPercentage = if (order.tax > 0.0) (order.tax / netOld) * 100.0 else 0.0
                    val taxPctFormatted = if (taxPercentage % 1.0 == 0.0) {
                        String.format(Locale.US, "%.0f%%", taxPercentage)
                    } else {
                        String.format(Locale.US, "%.1f%%", taxPercentage)
                    }
                    val vatLabel = if (order.tax > 0.0 && taxPercentage > 0.0) {
                        "${stringResource(R.string.lbl_vat)} ($taxPctFormatted)"
                    } else {
                        stringResource(R.string.lbl_vat)
                    }
                    val vatValueText = if (order.tax > 0.0) {
                        "+৳${formatAmount(order.tax)}"
                    } else {
                        "৳0"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_details_vat_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = vatLabel, color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = vatValueText,
                            color = if (order.tax > 0.0) CurrencyGold else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.lbl_total), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "৳ ${formatAmount(order.total)}",
                            color = CurrencyGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryRow(stringResource(R.string.lbl_payment_method), order.paymentMethod)
                    Spacer(modifier = Modifier.height(6.dp))
                    SummaryRow(stringResource(R.string.lbl_payment_status), if (isAlreadyPaid) stringResource(R.string.status_paid) else stringResource(R.string.status_unpaid))
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
                                coroutineScope.launch {
                                    isCancelPressed = true
                                    PosFeedbackHelper.triggerVibration(context, 70)
                                    delay(200)
                                    isCancelPressed = false
                                    viewModel.updateOrderStatus(order.id, "Cancelled")
                                    Toast.makeText(context, "Order Cancelled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCancelPressed) Color.White else StatusCancelled,
                                contentColor = if (isCancelPressed) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("order_details_cancel_btn")
                        ) {
                            Text(
                                stringResource(R.string.btn_cancel_order),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCancelPressed) Color.Black else Color.White
                            )
                        }
                    }

                    if (canReceivePayment) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isPayPressed = true
                                    PosFeedbackHelper.triggerVibration(context, 70)
                                    delay(200)
                                    isPayPressed = false
                                    showPaymentConfirmationDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPayPressed) Color.White else CurrencyGold,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("order_details_pay_btn")
                        ) {
                            Text(
                                stringResource(R.string.btn_pay),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
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
                    Text(stringResource(R.string.msg_order_paid_completed), color = StatusReady, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        isPrintPressed = true
                        PosFeedbackHelper.triggerVibration(context, 80)
                        PosFeedbackHelper.playReceiptPrinterSound()
                        delay(200)
                        isPrintPressed = false
                        viewModel.printCurrentOrder(order.id) { res ->
                            Toast.makeText(context, res.message, if (res.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPrintPressed) Color.White else DarkSurfaceVariant,
                    contentColor = if (isPrintPressed) Color.Black else CurrencyGold
                ),
                border = if (isPrintPressed) null else ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CurrencyGold)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("order_details_print_btn")
            ) {
                Icon(
                    Icons.Default.Print,
                    contentDescription = "Print",
                    tint = if (isPrintPressed) Color.Black else CurrencyGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.btn_print_receipt),
                    color = if (isPrintPressed) Color.Black else CurrencyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }


            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }

    if (showAddItemsBottomSheet) {
        AddItemsToOrderBottomSheet(
            orderNumber = order.orderNumber,
            viewModel = viewModel,
            onDismiss = {
                viewModel.setIsAddingToOrder(false)
                showAddItemsBottomSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemsToOrderBottomSheet(
    orderNumber: String,
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val menuItems by viewModel.menuItems.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var pressedCategoryChip by remember { mutableStateOf<String?>(null) }
    val quantities = remember { mutableStateMapOf<Long, Int>() }
    val notes = remember { mutableStateMapOf<Long, String>() }
    val pressedAddItems = remember { mutableStateMapOf<Long, Boolean>() }
    var isDonePressed by remember { mutableStateOf(false) }

    val filteredItems = remember(searchQuery, selectedCategoryFilter, menuItems) {
        menuItems.filter { item ->
            val matchesCategory = (selectedCategoryFilter == "All") || item.categoryName.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextMuted.copy(alpha = 0.4f))
            )
        },
        modifier = Modifier.testTag("add_items_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Add Items to Order",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Order #$orderNumber",
                        color = CurrencyGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search dishes / items...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
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
                    .testTag("order_items_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "cat_all") {
                    val isSelected = selectedCategoryFilter == "All"
                    val isPressed = pressedCategoryChip == "All"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isPressed) Color.White 
                                else if (isSelected) CurrencyGold 
                                else DarkSurface
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pressedCategoryChip = "All"
                                    PosFeedbackHelper.triggerVibration(context, 50)
                                    delay(180)
                                    pressedCategoryChip = null
                                    selectedCategoryFilter = "All"
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "All",
                            color = if (isPressed) Color.Black else if (isSelected) Color.Black else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isPressed) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                items(categories, key = { it.id }) { cat ->
                    val isSelected = selectedCategoryFilter == cat.name
                    val isPressed = pressedCategoryChip == cat.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isPressed) Color.White 
                                else if (isSelected) CurrencyGold 
                                else DarkSurface
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pressedCategoryChip = cat.name
                                    PosFeedbackHelper.triggerVibration(context, 50)
                                    delay(180)
                                    pressedCategoryChip = null
                                    selectedCategoryFilter = cat.name
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.name,
                            color = if (isPressed) Color.Black else if (isSelected) Color.Black else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isPressed) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Menu Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No menu items found", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val qty = quantities[item.id] ?: 1
                        val note = notes[item.id] ?: ""
                        val isItemAddPressed = pressedAddItems[item.id] == true

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_item_${item.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Food Image or Emoji
                                    val imageModel = remember(item.imageUrl) {
                                        item.imageUrl.takeIf { it.isNotBlank() }?.let { url ->
                                            if (url.startsWith("/")) File(url) else url
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
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
                                                    item.name.contains("Pizza", true) -> "🍕"
                                                    item.name.contains("Fries", true) -> "🍟"
                                                    item.name.contains("Chicken", true) -> "🍗"
                                                    item.name.contains("Ice Cream", true) -> "🍨"
                                                    else -> "🥤"
                                                },
                                                fontSize = 20.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "৳ ${formatAmount(item.price)}",
                                            color = CurrencyGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Quantity Stepper
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (qty > 1) quantities[item.id] = qty - 1
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextPrimary, modifier = Modifier.size(14.dp))
                                        }
                                        Text(
                                            text = "$qty",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                quantities[item.id] = qty + 1
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Add to Order Button
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                pressedAddItems[item.id] = true
                                                PosFeedbackHelper.triggerVibration(context, 70)
                                                delay(200)
                                                pressedAddItems[item.id] = false
                                                viewModel.addItemToExistingOrder(item, qty, note) { success ->
                                                    if (success) {
                                                        Toast.makeText(context, "Added $qty x ${item.name} to order", Toast.LENGTH_SHORT).show()
                                                        quantities[item.id] = 1
                                                    } else {
                                                        Toast.makeText(context, "Failed to add item", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isItemAddPressed) Color.White else CurrencyGold,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("add_item_direct_${item.id}")
                                    ) {
                                        Text(
                                            "Add",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Done Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        isDonePressed = true
                        PosFeedbackHelper.triggerVibration(context, 70)
                        delay(200)
                        isDonePressed = false
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDonePressed) Color.White else DarkSurfaceVariant,
                    contentColor = if (isDonePressed) Color.Black else TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("done_adding_items_btn")
            ) {
                Text(
                    "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isDonePressed) Color.Black else TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
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
