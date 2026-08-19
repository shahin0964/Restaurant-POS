package com.restaurant.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
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

    var selectedStaff by remember { mutableStateOf<com.restaurant.pos.data.db.UserEntity?>(null) }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableIntStateOf(1) }
    var expandedStaff by remember { mutableStateOf(false) }

    val selectedProduct = allProducts.find { it.id == selectedProductId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Staff Food", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            
            // Staff Selection
            Card(
                onClick = { expandedStaff = !expandedStaff },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedStaff?.name ?: "Select Staff", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(if (expandedStaff) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            if (expandedStaff) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    allStaff.forEach { staff ->
                        DropdownMenuItem(
                            text = { Text(staff.name) },
                            onClick = {
                                selectedStaff = staff
                                expandedStaff = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Add Food Area
            Text("Add Food Consumed", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            allProducts.forEach { product ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { selectedProductId = product.id },
                    colors = CardDefaults.cardColors(containerColor = if (selectedProductId == product.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(product.name, style = MaterialTheme.typography.bodyLarge)
                        Text("৳${product.price}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Quantity Control
            if (selectedProduct != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { if (quantity > 1) quantity-- }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text("$quantity", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { quantity++ }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Summary Card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("STAFF FOOD SUMMARY", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${selectedProduct.name} × $quantity")
                            Text("৳${selectedProduct.price * quantity}")
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Total", style = MaterialTheme.typography.titleMedium)
                            Text("৳${selectedProduct.price * quantity}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (selectedStaff != null && selectedProduct != null) {
                            viewModel.addStaffFood(selectedStaff!!.name, selectedProduct.name, quantity, selectedProduct.price)
                            quantity = 1
                            selectedProductId = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ADD TO RECORDS")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Today's Staff Food", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (staffFoodList.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍽️", fontSize = 32.sp)
                        Text("No staff food records yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                staffFoodList.forEach { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(entry.staffName, style = MaterialTheme.typography.titleSmall)
                                Text("${entry.productName} × ${entry.quantity}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("৳${entry.totalPrice}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
