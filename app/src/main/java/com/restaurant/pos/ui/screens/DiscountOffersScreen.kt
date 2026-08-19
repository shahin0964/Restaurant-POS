package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.OfferEntity
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.util.Locale

@Composable
fun DiscountOffersScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val offers by viewModel.allOffers.collectAsState()
    val receiptSetting by viewModel.receiptSetting.collectAsState()
    val currencySymbol = receiptSetting?.currencySymbol?.ifBlank { "৳" } ?: "৳"

    var searchQuery by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingOffer by remember { mutableStateOf<OfferEntity?>(null) }
    var offerToDelete by remember { mutableStateOf<OfferEntity?>(null) }

    val filteredOffers = remember(offers, searchQuery) {
        if (searchQuery.isBlank()) offers
        else offers.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("discount_offers_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Discounts & Offers",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            editingOffer = null
                            showAddEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CurrencyGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_new_offer_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("NEW OFFER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("discount_offers_screen")
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search discounts or promo offers...", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discount_search_input")
                )

                if (filteredOffers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(CurrencyGold.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Discount,
                                    contentDescription = null,
                                    tint = CurrencyGold,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = if (searchQuery.isBlank()) "No Discount Offers Available" else "No matching offers found",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Create promotional rules to apply percentage or fixed discounts on orders.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    editingOffer = null
                                    showAddEditDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CREATE FIRST OFFER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredOffers, key = { it.id }) { offer ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = if (offer.isActive) CurrencyGold.copy(alpha = 0.3f) else BorderOutline,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("offer_card_${offer.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (offer.isActive) CurrencyGold.copy(alpha = 0.15f) else DarkSurfaceVariant
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (offer.discountType == "PERCENTAGE") Icons.Default.Percent else Icons.Default.Discount,
                                                    contentDescription = null,
                                                    tint = if (offer.isActive) CurrencyGold else TextMuted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = offer.name,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val valueStr = if (offer.discountType == "PERCENTAGE") {
                                                    "${String.format(Locale.US, "%.0f", offer.discountValue)}% OFF"
                                                } else {
                                                    "$currencySymbol ${String.format(Locale.US, "%.2f", offer.discountValue)} OFF"
                                                }
                                                Text(
                                                    text = valueStr,
                                                    color = CurrencyGold,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = offer.isActive,
                                                onCheckedChange = {
                                                    viewModel.toggleOfferStatus(offer)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.Black,
                                                    checkedTrackColor = CurrencyGold,
                                                    uncheckedThumbColor = TextMuted,
                                                    uncheckedTrackColor = DarkSurfaceVariant
                                                ),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = BorderOutline.copy(alpha = 0.5f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            if (offer.minOrderAmount > 0.0) {
                                                Text(
                                                    text = "Min Order: $currencySymbol ${String.format(Locale.US, "%.0f", offer.minOrderAmount)}",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (offer.discountType == "PERCENTAGE" && offer.maxDiscountAmount > 0.0) {
                                                Text(
                                                    text = "Max Discount: $currencySymbol ${String.format(Locale.US, "%.0f", offer.maxDiscountAmount)}",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (offer.minOrderAmount <= 0.0 && offer.maxDiscountAmount <= 0.0) {
                                                Text(
                                                    text = "No minimum order requirement",
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = {
                                                    editingOffer = offer
                                                    showAddEditDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { offerToDelete = offer },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusCancelled, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Offer Dialog
    if (showAddEditDialog) {
        var offerName by remember { mutableStateOf(editingOffer?.name ?: "") }
        var discountType by remember { mutableStateOf(editingOffer?.discountType ?: "PERCENTAGE") }
        var discountValueText by remember { mutableStateOf(editingOffer?.let { String.format(Locale.US, "%.2f", it.discountValue) } ?: "") }
        var minOrderText by remember { mutableStateOf(editingOffer?.let { if (it.minOrderAmount > 0.0) String.format(Locale.US, "%.2f", it.minOrderAmount) else "" } ?: "") }
        var maxDiscountText by remember { mutableStateOf(editingOffer?.let { if (it.maxDiscountAmount > 0.0) String.format(Locale.US, "%.2f", it.maxDiscountAmount) else "" } ?: "") }
        var isActive by remember { mutableStateOf(editingOffer?.isActive ?: true) }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = {
                Text(
                    text = if (editingOffer == null) "Create Discount Offer" else "Edit Discount Offer",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = offerName,
                        onValueChange = { offerName = it },
                        label = { Text("Offer / Promo Name", color = TextMuted) },
                        placeholder = { Text("e.g. Happy Hour 10% Off", color = TextMuted) },
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

                    // Discount Type Selector (Percentage or Fixed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { discountType = "PERCENTAGE" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (discountType == "PERCENTAGE") CurrencyGold else DarkSurfaceVariant,
                                contentColor = if (discountType == "PERCENTAGE") Color.Black else TextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Percentage (%)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { discountType = "FIXED" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (discountType == "FIXED") CurrencyGold else DarkSurfaceVariant,
                                contentColor = if (discountType == "FIXED") Color.Black else TextSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fixed ($currencySymbol)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = discountValueText,
                        onValueChange = { discountValueText = it },
                        label = { Text(if (discountType == "PERCENTAGE") "Discount Rate (%)" else "Discount Amount ($currencySymbol)", color = TextMuted) },
                        placeholder = { Text("e.g. 10", color = TextMuted) },
                        suffix = { Text(if (discountType == "PERCENTAGE") "%" else currencySymbol, color = CurrencyGold, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        value = minOrderText,
                        onValueChange = { minOrderText = it },
                        label = { Text("Minimum Order Amount (Optional)", color = TextMuted) },
                        placeholder = { Text("0.00", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                    if (discountType == "PERCENTAGE") {
                        OutlinedTextField(
                            value = maxDiscountText,
                            onValueChange = { maxDiscountText = it },
                            label = { Text("Max Discount Cap ($currencySymbol) (Optional)", color = TextMuted) },
                            placeholder = { Text("0.00", color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Immediately", color = TextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = CurrencyGold
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameClean = offerName.trim()
                        val valueNum = discountValueText.toDoubleOrNull()
                        if (nameClean.isBlank()) {
                            Toast.makeText(context, "Please enter an offer name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (valueNum == null || valueNum <= 0.0) {
                            Toast.makeText(context, "Please enter a valid positive discount value", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (discountType == "PERCENTAGE" && valueNum > 100.0) {
                            Toast.makeText(context, "Percentage discount cannot exceed 100%", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val minOrderNum = minOrderText.toDoubleOrNull() ?: 0.0
                        val maxDiscNum = maxDiscountText.toDoubleOrNull() ?: 0.0

                        val entityToSave = OfferEntity(
                            id = editingOffer?.id ?: 0L,
                            name = nameClean,
                            discountType = discountType,
                            discountValue = valueNum,
                            startDate = System.currentTimeMillis(),
                            endDate = System.currentTimeMillis() + (365L * 24 * 3600 * 1000),
                            minOrderAmount = minOrderNum,
                            maxDiscountAmount = maxDiscNum,
                            isActive = isActive
                        )

                        viewModel.saveOffer(entityToSave) {
                            Toast.makeText(context, "Offer saved successfully", Toast.LENGTH_SHORT).show()
                        }
                        showAddEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                ) {
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Delete Confirmation Dialog
    if (offerToDelete != null) {
        val target = offerToDelete!!
        AlertDialog(
            onDismissRequest = { offerToDelete = null },
            title = {
                Text("Delete Offer?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete the offer \"${target.name}\"? This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOffer(target) {
                            Toast.makeText(context, "Offer deleted", Toast.LENGTH_SHORT).show()
                        }
                        offerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { offerToDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
