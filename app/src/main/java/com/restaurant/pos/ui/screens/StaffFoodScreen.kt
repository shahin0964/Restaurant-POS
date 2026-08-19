package com.restaurant.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.StaffFoodEntity
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.StaffFoodViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffFoodScreen(viewModel: StaffFoodViewModel) {
    val allProducts by viewModel.allProducts.collectAsState()
    val allStaff by viewModel.allStaff.collectAsState()
    val staffFoodList by viewModel.staffFoodList.collectAsState()

    var selectedStaffName by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf(1) }
    var expandedStaff by remember { mutableStateOf(false) }
    var expandedProduct by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("STAFF FOOD", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        // Staff Selection
        ExposedDropdownMenuBox(expanded = expandedStaff, onExpandedChange = { expandedStaff = it }) {
            TextField(
                value = selectedStaffName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Staff") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStaff) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expandedStaff, onDismissRequest = { expandedStaff = false }) {
                allStaff.forEach { staff ->
                    DropdownMenuItem(text = { Text(staff.name) }, onClick = {
                        selectedStaffName = staff.name
                        expandedStaff = false
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Product Selection
        val selectedProduct = allProducts.find { it.id == selectedProductId }
        ExposedDropdownMenuBox(expanded = expandedProduct, onExpandedChange = { expandedProduct = it }) {
            TextField(
                value = selectedProduct?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Product") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expandedProduct, onDismissRequest = { expandedProduct = false }) {
                allProducts.forEach { product ->
                    DropdownMenuItem(text = { Text(product.name) }, onClick = {
                        selectedProductId = product.id
                        expandedProduct = false
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantity: ")
            IconButton(onClick = { if (quantity > 1) quantity-- }) { Icon(Icons.Default.Delete, "Dec") } // Should be Remove
            Text("$quantity", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { quantity++ }) { Icon(Icons.Default.Add, "Inc") }
        }

        if (selectedProduct != null) {
            Text("Price: ৳${selectedProduct.price}", color = TextPrimary)
            Text("Total: ৳${selectedProduct.price * quantity}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CurrencyGold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (selectedStaffName.isNotEmpty() && selectedProduct != null) {
                    viewModel.addStaffFood(selectedStaffName, selectedProduct.name, quantity, selectedProduct.price)
                    quantity = 1
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ADD")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Today's Staff Food", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        LazyColumn {
            items(staffFoodList) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(entry.staffName, fontWeight = FontWeight.Bold)
                            Text("${entry.productName} × ${entry.quantity}")
                        }
                        Text("৳${entry.totalPrice}", color = CurrencyGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
