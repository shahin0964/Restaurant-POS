package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.R
import com.restaurant.pos.data.db.TableEntity
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(
    viewModel: RestaurantViewModel,
    onNavigateToOrderDetails: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tables by viewModel.allTables.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var tableToEdit by remember { mutableStateOf<TableEntity?>(null) }
    var tableToDelete by remember { mutableStateOf<TableEntity?>(null) }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("tables_back_btn")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Text(
                            text = stringResource(R.string.title_tables_dine_in),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_table_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Table",
                            tint = CurrencyGold
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CurrencyGold,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_table_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Table")
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("tables_screen")
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
            // Header Stats Banner
            val totalTables = tables.size
            val occupiedCount = tables.count { viewModel.getActiveOrderForTable(it, allOrders) != null }
            val availableCount = totalTables - occupiedCount

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.stat_total_tables), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("$totalTables", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(BorderOutline))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.stat_available), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("$availableCount", color = StatusReady, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(BorderOutline))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.stat_occupied), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("$occupiedCount", color = StatusPreparing, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (tables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.msg_no_tables_configured), color = TextMuted, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                        ) {
                            Text(stringResource(R.string.btn_add_table), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val activeOrderMap = remember(tables, allOrders) {
                    tables.associate { table ->
                        table.id to viewModel.getActiveOrderForTable(table, allOrders)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = tables,
                        key = { it.id },
                        contentType = { "table_card" }
                    ) { table ->
                        val activeOrder = activeOrderMap[table.id]
                        val isOccupied = activeOrder != null

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOccupied) DarkSurface else DarkSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isOccupied) androidx.compose.foundation.BorderStroke(1.dp, StatusPreparing.copy(alpha = 0.5f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("table_card_${table.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                // Table Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = table.name.uppercase(),
                                        color = TextPrimary,
                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Status Tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isOccupied) StatusPreparing.copy(alpha = 0.2f)
                                                else StatusReady.copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isOccupied) stringResource(R.string.lbl_occupied_tag) else stringResource(R.string.lbl_available_tag),
                                            color = if (isOccupied) StatusPreparing else StatusReady,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Capacity",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.lbl_seats_count, table.capacity),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderOutline)
                                Spacer(modifier = Modifier.height(10.dp))

                                if (isOccupied && activeOrder != null) {
                                    val order = activeOrder.order
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkBackground)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lbl_order_number_prefix, order.orderNumber),
                                            color = CurrencyGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${stringResource(R.string.lbl_total)}: ৳${String.format(Locale.getDefault(), "%.0f", order.total)}",
                                            color = TextPrimary,
                        fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${stringResource(R.string.lbl_status_prefix)} ${order.status}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                viewModel.setSelectedOrderDetails(activeOrder)
                                                onNavigateToOrderDetails()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.fillMaxWidth().height(32.dp).testTag("view_order_btn_${table.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = "View",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.btn_view_order), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.lbl_ready_for_order),
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Table Actions (Edit / Delete)
                                val cannotDeleteMsg = stringResource(R.string.msg_table_active_order_cannot_delete)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { tableToEdit = table },
                                        modifier = Modifier.size(32.dp).testTag("edit_table_btn_${table.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Table",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isOccupied) {
                                                Toast.makeText(context, cannotDeleteMsg, Toast.LENGTH_LONG).show()
                                            } else {
                                                tableToDelete = table
                                            }
                                        },
                                        modifier = Modifier.size(32.dp).testTag("delete_table_btn_${table.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Table",
                                            tint = if (isOccupied) TextMuted else StatusCancelled,
                                            modifier = Modifier.size(16.dp)
                                        )
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

    // ADD TABLE DIALOG
    val msgTableCreated = stringResource(R.string.msg_table_created)
    if (showAddDialog) {
        TableFormDialog(
            title = stringResource(R.string.title_add_new_table),
            initialName = "",
            initialCapacity = "4",
            onDismiss = { showAddDialog = false },
            onSave = { name, capacityStr ->
                val cap = capacityStr.toIntOrNull() ?: 4
                viewModel.addTable(
                    name = name,
                    capacity = cap,
                    onSuccess = {
                        showAddDialog = false
                        Toast.makeText(context, msgTableCreated, Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // EDIT TABLE DIALOG
    val msgTableUpdated = stringResource(R.string.msg_table_updated)
    tableToEdit?.let { table ->
        TableFormDialog(
            title = stringResource(R.string.title_edit_table),
            initialName = table.name,
            initialCapacity = table.capacity.toString(),
            onDismiss = { tableToEdit = null },
            onSave = { name, capacityStr ->
                val cap = capacityStr.toIntOrNull() ?: table.capacity
                viewModel.updateTable(
                    table = table.copy(name = name, capacity = cap),
                    onSuccess = {
                        tableToEdit = null
                        Toast.makeText(context, msgTableUpdated, Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    val msgTableDeleted = stringResource(R.string.msg_table_deleted)
    tableToDelete?.let { table ->
        AlertDialog(
            onDismissRequest = { tableToDelete = null },
            title = {
                Text(stringResource(R.string.title_delete_table), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(stringResource(R.string.msg_confirm_delete_table, table.name), color = TextSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = table
                        tableToDelete = null
                        viewModel.deleteTable(
                            table = t,
                            onSuccess = {
                                Toast.makeText(context, msgTableDeleted, Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.btn_delete_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tableToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel), color = TextMuted)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun TableFormDialog(
    title: String,
    initialName: String,
    initialCapacity: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var capacity by remember { mutableStateOf(initialCapacity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hint_table_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedLabelColor = CurrencyGold,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("table_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it },
                    label = { Text(stringResource(R.string.hint_table_capacity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedLabelColor = CurrencyGold,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("table_capacity_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, capacity) },
                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_table_btn")
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = TextMuted)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(14.dp)
    )
}
