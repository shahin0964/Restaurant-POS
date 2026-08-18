package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import com.restaurant.pos.data.db.ReceiptSettingEntity
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.util.Locale

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String
)

val CURRENCY_OPTIONS = listOf(
    CurrencyOption("BDT", "৳", "BDT / ৳ (Bangladeshi Taka)"),
    CurrencyOption("USD", "$", "USD / $ (US Dollar)"),
    CurrencyOption("EUR", "€", "EUR / € (Euro)"),
    CurrencyOption("GBP", "£", "GBP / £ (British Pound)"),
    CurrencyOption("INR", "₹", "INR / ₹ (Indian Rupee)"),
    CurrencyOption("SAR", "﷼", "SAR / ﷼ (Saudi Riyal)"),
    CurrencyOption("AED", "د.إ", "AED / د.إ (UAE Dirham)")
)

@Composable
fun BusinessSettingsScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val savedSetting by viewModel.receiptSetting.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Authorization check
    val isAuthorized = currentUser == null || currentUser?.role in listOf("Administrator", "Manager")

    // State fields
    var shopName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("৳") }
    var currencyCode by remember { mutableStateOf("BDT") }
    var isTaxEnabled by remember { mutableStateOf(false) }
    var taxRateText by remember { mutableStateOf("0.00") }

    // Initial loaded state tracking to detect unsaved changes
    var initialSetting by remember { mutableStateOf<ReceiptSettingEntity?>(null) }

    LaunchedEffect(savedSetting) {
        val s = savedSetting ?: ReceiptSettingEntity()
        if (initialSetting == null) {
            initialSetting = s
            shopName = s.shopName
            phone = s.phone
            address = s.address
            email = s.email
            website = s.website
            currencySymbol = if (s.currencySymbol.isBlank()) "৳" else s.currencySymbol
            currencyCode = if (s.currencyCode.isBlank()) "BDT" else s.currencyCode
            isTaxEnabled = s.isTaxEnabled
            taxRateText = String.format(Locale.US, "%.2f", s.taxRate)
        }
    }

    // Dialog flags
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }

    // Tax Safety Validation
    val parsedTaxRate = taxRateText.toDoubleOrNull()
    val isTaxRateValid = parsedTaxRate != null &&
            !parsedTaxRate.isNaN() &&
            !parsedTaxRate.isInfinite() &&
            parsedTaxRate >= 0.0 &&
            parsedTaxRate <= 100.0

    val taxErrorMessage = when {
        parsedTaxRate == null -> "Please enter a valid numeric tax rate."
        parsedTaxRate < 0.0 -> "Tax rate cannot be negative."
        parsedTaxRate > 100.0 -> "Tax rate cannot exceed 100%."
        parsedTaxRate.isNaN() || parsedTaxRate.isInfinite() -> "Invalid tax rate value."
        else -> null
    }

    val hasUnsavedChanges = initialSetting?.let { init ->
        shopName != init.shopName ||
        phone != init.phone ||
        address != init.address ||
        email != init.email ||
        website != init.website ||
        currencySymbol != init.currencySymbol ||
        currencyCode != init.currencyCode ||
        isTaxEnabled != init.isTaxEnabled ||
        (parsedTaxRate != null && parsedTaxRate != init.taxRate)
    } ?: false

    fun handleBack() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    fun saveSettings(): Boolean {
        if (!isAuthorized) {
            Toast.makeText(context, "Unauthorized: Only Managers & Admins can change settings", Toast.LENGTH_SHORT).show()
            return false
        }

        if (isTaxEnabled && !isTaxRateValid) {
            Toast.makeText(context, taxErrorMessage ?: "Invalid tax rate. Please check input.", Toast.LENGTH_SHORT).show()
            return false
        }

        val currentEntity = savedSetting ?: ReceiptSettingEntity()
        val finalTaxRate = if (isTaxEnabled) (parsedTaxRate ?: 0.0) else (parsedTaxRate ?: 0.0)

        val updatedEntity = currentEntity.copy(
            shopName = shopName.trim(),
            phone = phone.trim(),
            address = address.trim(),
            email = email.trim(),
            website = website.trim(),
            currencySymbol = currencySymbol,
            currencyCode = currencyCode,
            isTaxEnabled = isTaxEnabled,
            taxRate = finalTaxRate
        )

        viewModel.saveReceiptSetting(updatedEntity)
        initialSetting = updatedEntity
        Toast.makeText(context, "Business settings saved successfully", Toast.LENGTH_SHORT).show()
        return true
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
                    IconButton(
                        onClick = { handleBack() },
                        modifier = Modifier.testTag("business_settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Business Settings",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showResetDialog = true },
                        enabled = isAuthorized,
                        modifier = Modifier.testTag("business_settings_reset_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Settings",
                            tint = if (isAuthorized) StatusCancelled else TextMuted
                        )
                    }

                    Button(
                        onClick = { saveSettings() },
                        enabled = isAuthorized,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CurrencyGold,
                            contentColor = Color.Black,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("business_settings_save_btn")
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
        modifier = Modifier.testTag("business_settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner if Unauthorized
            if (!isAuthorized) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = StatusCancelled)
                            Column {
                                Text(
                                    text = "READ-ONLY ACCESS",
                                    color = StatusCancelled,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Only Administrators & Managers can modify business settings.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // LOGO PREVIEW (MAPPED TO INVOICE / RECEIPT SETTINGS LOGO)
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
                        Text(
                            text = "BUSINESS LOGO",
                            color = CurrencyGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, BorderOutline, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val logoUri = savedSetting?.logoUri ?: ""
                                if (logoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = logoUri,
                                        contentDescription = "Configured Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = "No Logo",
                                        tint = TextMuted,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if ((savedSetting?.logoUri ?: "").isNotBlank()) "Logo Uploaded" else "No Logo Set",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Logo is managed centrally via Invoice / Receipt Settings",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 1: BUSINESS INFORMATION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "BUSINESS INFORMATION",
                            color = CurrencyGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Restaurant / Shop Name", color = TextMuted) },
                            placeholder = { Text("Enter restaurant name", color = TextMuted) },
                            singleLine = true,
                            enabled = isAuthorized,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("business_input_name")
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number", color = TextMuted) },
                            placeholder = { Text("Enter phone number", color = TextMuted) },
                            singleLine = true,
                            enabled = isAuthorized,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("business_input_phone")
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address", color = TextMuted) },
                            placeholder = { Text("Enter address", color = TextMuted) },
                            singleLine = true,
                            enabled = isAuthorized,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("business_input_address")
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address", color = TextMuted) },
                            placeholder = { Text("Enter email address", color = TextMuted) },
                            singleLine = true,
                            enabled = isAuthorized,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("business_input_email")
                        )

                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text("Website (Optional)", color = TextMuted) },
                            placeholder = { Text("Enter website URL (optional)", color = TextMuted) },
                            singleLine = true,
                            enabled = isAuthorized,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("business_input_website")
                        )
                    }
                }
            }

            // SECTION 2: CURRENCY
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = CurrencyGold)
                            Text(
                                text = "CURRENCY",
                                color = CurrencyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "Select currency used by the application for prices and totals:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = "$currencyCode / $currencySymbol",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Currency", color = TextMuted) },
                                enabled = isAuthorized,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { if (isAuthorized) showCurrencyDropdown = !showCurrencyDropdown }
                                    ) {
                                        Text("▼", color = CurrencyGold, fontSize = 12.sp)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceVariant,
                                    unfocusedContainerColor = DarkSurfaceVariant,
                                    disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                    focusedBorderColor = CurrencyGold,
                                    unfocusedBorderColor = BorderOutline,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isAuthorized) { showCurrencyDropdown = true }
                                    .testTag("business_currency_selector")
                            )

                            DropdownMenu(
                                expanded = showCurrencyDropdown,
                                onDismissRequest = { showCurrencyDropdown = false },
                                modifier = Modifier
                                    .background(DarkSurface)
                                    .border(1.dp, BorderOutline, RoundedCornerShape(8.dp))
                            ) {
                                CURRENCY_OPTIONS.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(opt.name, color = TextPrimary, fontSize = 13.sp)
                                                if (opt.code == currencyCode) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = CurrencyGold, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            currencyCode = opt.code
                                            currencySymbol = opt.symbol
                                            showCurrencyDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Historical transaction amounts will remain financially unchanged.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // SECTION 3: TAX SETTINGS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Percent, contentDescription = null, tint = CurrencyGold)
                            Text(
                                text = "TAX SETTINGS",
                                color = CurrencyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Enable Tax", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Apply tax rate on order calculations", color = TextMuted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isTaxEnabled,
                                onCheckedChange = { if (isAuthorized) isTaxEnabled = it },
                                enabled = isAuthorized,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = CurrencyGold,
                                    disabledCheckedTrackColor = CurrencyGold.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.testTag("business_tax_switch")
                            )
                        }

                        if (isTaxEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tax Type", color = TextSecondary, fontSize = 13.sp)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Percentage (%)",
                                        color = CurrencyGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = taxRateText,
                                onValueChange = { taxRateText = it },
                                label = { Text("Tax Rate (%)", color = TextMuted) },
                                placeholder = { Text("0.00", color = TextMuted) },
                                suffix = { Text("%", color = CurrencyGold, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                enabled = isAuthorized,
                                isError = !isTaxRateValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceVariant,
                                    unfocusedContainerColor = DarkSurfaceVariant,
                                    disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                                    focusedBorderColor = CurrencyGold,
                                    unfocusedBorderColor = BorderOutline,
                                    errorBorderColor = StatusCancelled,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("business_tax_rate_input")
                            )

                            if (!isTaxRateValid) {
                                Text(
                                    text = taxErrorMessage ?: "Invalid tax rate",
                                    color = StatusCancelled,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Save Button
            item {
                Button(
                    onClick = { saveSettings() },
                    enabled = isAuthorized,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CurrencyGold,
                        contentColor = Color.Black,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("business_bottom_save_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAVE BUSINESS SETTINGS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // UNSAVED CHANGES CONFIRMATION DIALOG (Requirement 11)
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = {
                Text("Save changes?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "You have unsaved changes in business settings. Would you like to save before leaving?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        if (saveSettings()) {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                ) {
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("CANCEL", color = TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            onBack()
                        }
                    ) {
                        Text("DISCARD", color = StatusCancelled, fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = DarkSurface
        )
    }

    // RESET CONFIRMATION DIALOG (Requirement 12)
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text("Reset business settings?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "This will reset restaurant name, contact information, currency, and tax options to defaults. Orders, products, inventory, staff, and reports will NOT be affected.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        val currentEntity = savedSetting ?: ReceiptSettingEntity()
                        val resetEntity = currentEntity.copy(
                            shopName = "",
                            phone = "",
                            address = "",
                            email = "",
                            website = "",
                            currencySymbol = "৳",
                            currencyCode = "BDT",
                            isTaxEnabled = false,
                            taxRate = 0.0
                        )
                        viewModel.saveReceiptSetting(resetEntity)
                        initialSetting = resetEntity
                        shopName = ""
                        phone = ""
                        address = ""
                        email = ""
                        website = ""
                        currencySymbol = "৳"
                        currencyCode = "BDT"
                        isTaxEnabled = false
                        taxRateText = "0.00"
                        Toast.makeText(context, "Business settings reset", Toast.LENGTH_SHORT).show()
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
}
