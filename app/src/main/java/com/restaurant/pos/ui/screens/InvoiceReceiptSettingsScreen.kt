package com.restaurant.pos.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.restaurant.pos.data.db.ReceiptSettingEntity
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel

@Composable
fun InvoiceReceiptSettingsScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val savedSetting by viewModel.receiptSetting.collectAsState()

    var shopName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var logoUri by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("") }

    var showShopName by remember { mutableStateOf(true) }
    var showLogo by remember { mutableStateOf(true) }
    var showPhone by remember { mutableStateOf(true) }
    var showAddress by remember { mutableStateOf(true) }
    var showOrderNumber by remember { mutableStateOf(true) }
    var showDateTime by remember { mutableStateOf(true) }
    var showCustomerName by remember { mutableStateOf(true) }
    var showOrderType by remember { mutableStateOf(true) }
    var showItems by remember { mutableStateOf(true) }
    var showQuantity by remember { mutableStateOf(true) }
    var showItemPrice by remember { mutableStateOf(true) }
    var showSubtotal by remember { mutableStateOf(true) }
    var showDiscount by remember { mutableStateOf(true) }
    var showTax by remember { mutableStateOf(true) }
    var showTotal by remember { mutableStateOf(true) }
    var showPaymentStatus by remember { mutableStateOf(true) }
    var showFooter by remember { mutableStateOf(true) }

    var showResetDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    // Load saved settings
    LaunchedEffect(savedSetting) {
        val s = savedSetting ?: ReceiptSettingEntity()
        shopName = s.shopName
        phone = s.phone
        address = s.address
        email = s.email
        website = s.website
        logoUri = s.logoUri
        footerText = s.footerText

        showShopName = s.showShopName
        showLogo = s.showLogo
        showPhone = s.showPhone
        showAddress = s.showAddress
        showOrderNumber = s.showOrderNumber
        showDateTime = s.showDateTime
        showCustomerName = s.showCustomerName
        showOrderType = s.showOrderType
        showItems = s.showItems
        showQuantity = s.showQuantity
        showItemPrice = s.showItemPrice
        showSubtotal = s.showSubtotal
        showDiscount = s.showDiscount
        showTax = s.showTax
        showTotal = s.showTotal
        showPaymentStatus = s.showPaymentStatus
        showFooter = s.showFooter
    }

    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val imagePickerLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                logoUri = uri.toString()
            }
        }
    } else {
        null
    }

    fun buildCurrentEntity(): ReceiptSettingEntity {
        val current = savedSetting
        return ReceiptSettingEntity(
            id = 1,
            shopName = shopName,
            phone = phone,
            address = address,
            email = email,
            website = website,
            logoUri = logoUri,
            footerText = footerText,
            currencySymbol = current?.currencySymbol ?: "৳",
            currencyCode = current?.currencyCode ?: "BDT",
            isTaxEnabled = current?.isTaxEnabled ?: false,
            taxRate = current?.taxRate ?: 0.0,
            showShopName = showShopName,
            showLogo = showLogo,
            showPhone = showPhone,
            showAddress = showAddress,
            showOrderNumber = showOrderNumber,
            showDateTime = showDateTime,
            showCustomerName = showCustomerName,
            showOrderType = showOrderType,
            showItems = showItems,
            showQuantity = showQuantity,
            showItemPrice = showItemPrice,
            showSubtotal = showSubtotal,
            showDiscount = showDiscount,
            showTax = showTax,
            showTotal = showTotal,
            showPaymentStatus = showPaymentStatus,
            showFooter = showFooter
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("receipt_settings_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Receipt Settings",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Settings", tint = StatusCancelled)
                    }
                    Button(
                        onClick = {
                            viewModel.saveReceiptSetting(buildCurrentEntity())
                            Toast.makeText(context, "Receipt settings saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("invoice_receipt_settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview & Actions Bar
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Thermal Receipt Formatting", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Configure content & branding printed on receipts", color = TextMuted, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showPreviewDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = CurrencyGold),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PREVIEW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // SHOP INFORMATION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("SHOP INFORMATION", color = CurrencyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Shop / Restaurant Name", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email (Opt)", color = TextMuted, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceVariant,
                                    unfocusedContainerColor = DarkSurfaceVariant,
                                    focusedBorderColor = CurrencyGold,
                                    unfocusedBorderColor = BorderOutline,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = website,
                                onValueChange = { website = it },
                                label = { Text("Website (Opt)", color = TextMuted, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceVariant,
                                    unfocusedContainerColor = DarkSurfaceVariant,
                                    focusedBorderColor = CurrencyGold,
                                    unfocusedBorderColor = BorderOutline,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // SHOP LOGO
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("SHOP LOGO", color = CurrencyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, BorderOutline, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (logoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = logoUri,
                                        contentDescription = "Shop Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextMuted)
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (logoUri.isBlank()) {
                                    Button(
                                        onClick = { imagePickerLauncher?.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("ADD LOGO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { imagePickerLauncher?.launch("image/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = CurrencyGold),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("CHANGE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { logoUri = "" },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusCancelled),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("REMOVE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RECEIPT CONTENT VISIBILITY OPTIONS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("RECEIPT CONTENT VISIBILITY", color = CurrencyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        VisibilitySwitchRow("Shop Name", showShopName) { showShopName = it }
                        VisibilitySwitchRow("Logo", showLogo) { showLogo = it }
                        VisibilitySwitchRow("Phone Number", showPhone) { showPhone = it }
                        VisibilitySwitchRow("Address", showAddress) { showAddress = it }
                        VisibilitySwitchRow("Order Number", showOrderNumber) { showOrderNumber = it }
                        VisibilitySwitchRow("Date & Time", showDateTime) { showDateTime = it }
                        VisibilitySwitchRow("Customer Name", showCustomerName) { showCustomerName = it }
                        VisibilitySwitchRow("Table / Order Type", showOrderType) { showOrderType = it }
                        VisibilitySwitchRow("Ordered Items", showItems) { showItems = it }
                        VisibilitySwitchRow("Item Quantity", showQuantity) { showQuantity = it }
                        VisibilitySwitchRow("Item Price", showItemPrice) { showItemPrice = it }
                        VisibilitySwitchRow("Subtotal", showSubtotal) { showSubtotal = it }
                        VisibilitySwitchRow("Discount", showDiscount) { showDiscount = it }
                        VisibilitySwitchRow("Tax", showTax) { showTax = it }
                        VisibilitySwitchRow("Total Amount", showTotal) { showTotal = it }
                        VisibilitySwitchRow("Payment Status", showPaymentStatus) { showPaymentStatus = it }
                        VisibilitySwitchRow("Receipt Footer Text", showFooter) { showFooter = it }
                    }
                }
            }

            // RECEIPT FOOTER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("RECEIPT FOOTER", color = CurrencyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        OutlinedTextField(
                            value = footerText,
                            onValueChange = { footerText = it },
                            placeholder = { Text("Enter receipt footer message...", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text("Reset receipt settings?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text("This will restore default receipt visibility and clear shop information fields. Orders, inventory, and reports will remain untouched.", color = TextSecondary, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val defaultSetting = ReceiptSettingEntity()
                        viewModel.saveReceiptSetting(defaultSetting)
                        Toast.makeText(context, "Receipt settings reset", Toast.LENGTH_SHORT).show()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("RESET", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Receipt Thermal Preview Dialog
    if (showPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = {
                Text("Receipt Thermal Preview", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showLogo && logoUri.isNotBlank()) {
                            AsyncImage(
                                model = logoUri,
                                contentDescription = "Logo Preview",
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (showShopName && shopName.isNotBlank()) {
                            Text(shopName, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (showPhone && phone.isNotBlank()) {
                            Text("Tel: $phone", color = Color.DarkGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (showAddress && address.isNotBlank()) {
                            Text(address, color = Color.DarkGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Text("=================================", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            if (showOrderNumber) Text("Order No: #1001", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (showOrderType) Text("Type    : Dine-in (Table 4)", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (showCustomerName) Text("Customer: John Doe", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (showDateTime) Text("Time    : 14 Aug 2026, 01:45 PM", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Text("---------------------------------", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                        if (showItems) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Item", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (showQuantity) Text("Qty", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (showItemPrice) Text("Price", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text("---------------------------------", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Chicken Burger", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (showQuantity) Text("x2", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (showItemPrice) Text("৳500", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cold Coffee", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (showQuantity) Text("x1", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (showItemPrice) Text("৳120", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text("---------------------------------", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            if (showSubtotal) Text("Subtotal: ৳620", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (showDiscount) Text("Discount: -৳20", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (showTax) Text("Tax: ৳0", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (showTotal) Text("TOTAL: ৳600", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }

                        if (showPaymentStatus) {
                            Text("Payment : Cash (Paid)", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Text("=================================", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

                        if (showFooter && footerText.isNotBlank()) {
                            Text(footerText, color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPreviewDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun VisibilitySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = CurrencyGold
            )
        )
    }
}
