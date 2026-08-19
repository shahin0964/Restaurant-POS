package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDiscountSettingsScreen(
    viewModel: RestaurantViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allProducts by viewModel.menuItems.collectAsState(initial = emptyList())
    var products by remember { mutableStateOf<List<MenuItemEntity>>(emptyList()) }

    LaunchedEffect(allProducts) {
        products = allProducts
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = "Product Discount Settings",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 48.dp)
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(products, key = { it.id }) { product ->
                ProductDiscountItem(
                    product = product,
                    onUpdate = { updatedProduct ->
                        products = products.map { if (it.id == updatedProduct.id) updatedProduct else it }
                        viewModel.updateMenuItemDiscount(updatedProduct)
                    }
                )
            }
        }
    }
}

@Composable
fun ProductDiscountItem(
    product: MenuItemEntity,
    onUpdate: (MenuItemEntity) -> Unit
) {
    var discountEnabled by remember { mutableStateOf(product.discountEnabled) }
    var discountValue by remember { mutableStateOf(product.discountValue.toString()) }
    var discountType by remember { mutableStateOf(product.discountType) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(product.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                Switch(checked = discountEnabled, onCheckedChange = {
                    discountEnabled = it
                    onUpdate(product.copy(discountEnabled = it, discountValue = discountValue.toDoubleOrNull() ?: 0.0, discountType = discountType))
                })
            }
            Text("Price: ৳${product.price}", color = TextSecondary)
            if (discountEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = discountType == "PERCENTAGE", onClick = {
                        discountType = "PERCENTAGE"
                        onUpdate(product.copy(discountEnabled = true, discountValue = discountValue.toDoubleOrNull() ?: 0.0, discountType = "PERCENTAGE"))
                    }, label = { Text("%") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = discountType == "FIXED", onClick = {
                        discountType = "FIXED"
                        onUpdate(product.copy(discountEnabled = true, discountValue = discountValue.toDoubleOrNull() ?: 0.0, discountType = "FIXED"))
                    }, label = { Text("৳") })
                }
                OutlinedTextField(
                    value = discountValue,
                    onValueChange = {
                        discountValue = it
                        val value = it.toDoubleOrNull() ?: 0.0
                        if (discountType == "PERCENTAGE" && value <= 100.0 && value >= 0.0) {
                            onUpdate(product.copy(discountEnabled = true, discountValue = value, discountType = discountType))
                        } else if (discountType == "FIXED" && value <= product.price && value >= 0.0) {
                            onUpdate(product.copy(discountEnabled = true, discountValue = value, discountType = discountType))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Discount") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
