package com.restaurant.pos.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.restaurant.pos.data.db.PrinterSettingEntity
import com.restaurant.pos.data.repository.DiscoveredPrinterDevice
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    viewModel: RestaurantViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedSetting by viewModel.printerSetting.collectAsState()

    var connectionType by remember { mutableStateOf("BUILT_IN") }
    var printerName by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("192.168.1.100") }
    var portText by remember { mutableStateOf("9100") }
    var paperSize by remember { mutableStateOf("58mm") }
    var autoPrintOnOrder by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }

    var isTestingPrint by remember { mutableStateOf(false) }
    var testResultDialogMsg by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf(false) }

    var discoveredDevices by remember { mutableStateOf<List<DiscoveredPrinterDevice>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Not tested") }

    // Initialize local state when DB loads
    LaunchedEffect(savedSetting) {
        savedSetting?.let { s ->
            connectionType = s.connectionType
            printerName = s.printerName
            macAddress = s.macAddress
            ipAddress = if (s.ipAddress.isNotBlank()) s.ipAddress else "192.168.1.100"
            portText = s.port.toString()
            paperSize = s.paperSize
            autoPrintOnOrder = s.autoPrintOnOrder
            isConnected = s.isConnected
        }
    }

    // Bluetooth permission check
    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val btPermissionLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.values.all { it }
            hasBluetoothPermission = granted
            if (granted) {
                discoveredDevices = viewModel.printerRepo.getPairedBluetoothDevices()
                Toast.makeText(context, "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Bluetooth permission required for discovery", Toast.LENGTH_LONG).show()
            }
        }
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PRINTER SETTINGS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Universal Receipt Printer Configuration",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("printer_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("printer_settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 1. Connection Status Banner
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) StatusReady else StatusCancelled)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                                color = if (isConnected) StatusReady else StatusCancelled,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val targetDesc = when (connectionType) {
                                "BUILT_IN" -> if (viewModel.printerRepo.isSunmiDevice()) "SUNMI Built-In Printer" else "Built-In Printer"
                                "BLUETOOTH" -> if (printerName.isNotBlank()) "$printerName ($macAddress)" else "Bluetooth Printer"
                                "WIFI_LAN" -> "$ipAddress:${portText.toIntOrNull() ?: 9100}"
                                "USB" -> if (printerName.isNotBlank()) printerName else "USB Printer"
                                else -> "Unconfigured"
                            }
                            Text(
                                text = "$connectionType • $targetDesc",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        color = CurrencyGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = paperSize,
                            color = CurrencyGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. Connection Type Selector Tabs
            Text(
                text = "CONNECTION TYPE",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "BUILT_IN" to "BUILT-IN",
                    "BLUETOOTH" to "BLUETOOTH",
                    "WIFI_LAN" to "WI-FI / LAN",
                    "USB" to "USB"
                ).forEach { (typeKey, label) ->
                    val selected = connectionType == typeKey
                    Surface(
                        onClick = {
                            connectionType = typeKey
                            discoveredDevices = emptyList()
                            if (typeKey == "BUILT_IN") {
                                isConnected = viewModel.printerRepo.isSunmiDevice()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) StatusPreparing else DarkSurface,
                        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, BorderOutline),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_connection_$typeKey")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = TextPrimary,
                        fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 3. Connection Configuration Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when (connectionType) {
                        "BUILT_IN" -> {
                            val isSunmi = remember { viewModel.printerRepo.isSunmiDevice() }
                            Text(
                                text = "BUILT-IN THERMAL PRINTER",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isSunmi)
                                    "SUNMI V2 Pro / POS terminal inner printer detected. This app directly communicates with the built-in printer hardware service."
                                else
                                    "Generic built-in printer mode. Will send ESC/POS commands directly to the terminal printer engine.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSunmi) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (isSunmi) StatusReady else CurrencyGold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (isSunmi) "Hardware Ready: SUNMI InnerPrinter" else "Built-in printer hardware check pending",
                                        color = TextPrimary,
                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        "BLUETOOTH" -> {
                            Text(
                                text = "BLUETOOTH THERMAL PRINTER",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (!hasBluetoothPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Bluetooth permission is required to discover and connect to physical receipt printers.",
                        color = TextPrimary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                btPermissionLauncher?.launch(
                                                    arrayOf(
                                                        Manifest.permission.BLUETOOTH_CONNECT,
                                                        Manifest.permission.BLUETOOTH_SCAN
                                                    )
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusPreparing)
                                        ) {
                                            Text("GRANT PERMISSION")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isSearching = true
                                        discoveredDevices = viewModel.printerRepo.getPairedBluetoothDevices()
                                        isSearching = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusPreparing),
                                    modifier = Modifier.fillMaxWidth().testTag("btn_search_bt_printers")
                                ) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SEARCH PAIRED BLUETOOTH PRINTERS")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (discoveredDevices.isEmpty()) {
                                    Text(
                                        text = "No paired Bluetooth printers listed yet. Please pair your Bluetooth thermal printer in Android Bluetooth Settings first, then tap Search.",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        text = "PAIRED DEVICES (${discoveredDevices.size}):",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxHeight(0.7f)
                                    ) {
                                        items(discoveredDevices) { dev ->
                                            val isSelected = macAddress == dev.address
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isSelected) StatusPreparing.copy(alpha = 0.2f) else DarkSurfaceVariant),
                                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, StatusPreparing) else null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        printerName = dev.name
                                                        macAddress = dev.address
                                                        isConnected = true
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(text = dev.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(text = dev.address, color = TextSecondary, fontSize = 11.sp)
                                                    }
                                                    if (isSelected) {
                                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = StatusPreparing)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "WIFI_LAN" -> {
                            Text(
                                text = "WI-FI / LAN THERMAL PRINTER",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it },
                                label = { Text("IP Address (e.g. 192.168.1.100)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = StatusPreparing,
                                    unfocusedBorderColor = BorderOutline,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_printer_ip")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it },
                                label = { Text("Port (Default: 9100)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = StatusPreparing,
                                    unfocusedBorderColor = BorderOutline,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_printer_port")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val p = portText.toIntOrNull() ?: 9100
                                            val socket = Socket()
                                            socket.connect(InetSocketAddress(ipAddress, p), 3000)
                                            socket.close()
                                            withContext(Dispatchers.Main) {
                                                isConnected = true
                                                statusText = "Connected to $ipAddress:$p"
                                                Toast.makeText(context, "Connection successful to $ipAddress:$p", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isConnected = false
                                                statusText = "Failed: ${e.message}"
                                                Toast.makeText(context, "Wi-Fi printer unreachable: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusPreparing),
                                modifier = Modifier.fillMaxWidth().testTag("btn_connect_wifi_printer")
                            ) {
                                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONNECT / TEST SOCKET")
                            }

                            if (statusText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = statusText, color = if (isConnected) StatusReady else StatusCancelled, fontSize = 12.sp)
                            }
                        }

                        "USB" -> {
                            val hasUsbHost = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) }
                            Text(
                                text = "USB THERMAL PRINTER",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (!hasUsbHost) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "USB printing is not supported on this device.",
                                        color = StatusCancelled,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        discoveredDevices = viewModel.printerRepo.getConnectedUsbDevices()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusPreparing),
                                    modifier = Modifier.fillMaxWidth().testTag("btn_search_usb_printers")
                                ) {
                                    Icon(imageVector = Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("DISCOVER ATTACHED USB PRINTERS")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (discoveredDevices.isEmpty()) {
                                    Text(
                                        text = "No attached USB printers detected. Connect a USB thermal printer via OTG adapter and tap Discover.",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxHeight(0.7f)
                                    ) {
                                        items(discoveredDevices) { dev ->
                                            val isSelected = printerName == dev.name || printerName == dev.address
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isSelected) StatusPreparing.copy(alpha = 0.2f) else DarkSurfaceVariant),
                                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, StatusPreparing) else null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        printerName = dev.name
                                                        macAddress = dev.address
                                                        isConnected = true
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(text = dev.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(text = dev.address, color = TextSecondary, fontSize = 11.sp)
                                                    }
                                                    if (isSelected) {
                                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = StatusPreparing)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 4. Paper Size Selection
                    HorizontalDivider(color = BorderOutline, modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "PAPER ROLL SIZE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("58mm", "80mm").forEach { size ->
                            val sel = paperSize == size
                            Surface(
                                onClick = { paperSize = size },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) CurrencyGold.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                border = if (sel) androidx.compose.foundation.BorderStroke(1.5.dp, CurrencyGold) else null,
                                modifier = Modifier.weight(1f).testTag("paper_size_$size")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = size,
                                        color = if (sel) CurrencyGold else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // 5. Auto Print Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auto-print receipt on order placement",
                        color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Switch(
                            checked = autoPrintOnOrder,
                            onCheckedChange = { autoPrintOnOrder = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = StatusReady
                            ),
                            modifier = Modifier.testTag("switch_auto_print")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Action Row: [ SAVE ] and [ 🖨️ TEST PRINT ]
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val portInt = portText.toIntOrNull() ?: 9100
                        val entity = PrinterSettingEntity(
                            id = 1,
                            connectionType = connectionType,
                            printerName = printerName,
                            macAddress = macAddress,
                            ipAddress = ipAddress,
                            port = portInt,
                            paperSize = paperSize,
                            autoPrintOnOrder = autoPrintOnOrder,
                            isConnected = isConnected,
                            printerType = connectionType,
                            bluetoothAddress = macAddress
                        )
                        viewModel.savePrinterSetting(entity)
                        Toast.makeText(context, "Printer settings saved", Toast.LENGTH_SHORT).show()
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_save_printer_settings")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE")
                }

                Button(
                    onClick = {
                        isTestingPrint = true
                        val portInt = portText.toIntOrNull() ?: 9100
                        val currentEntity = PrinterSettingEntity(
                            id = 1,
                            connectionType = connectionType,
                            printerName = printerName,
                            macAddress = macAddress,
                            ipAddress = ipAddress,
                            port = portInt,
                            paperSize = paperSize,
                            autoPrintOnOrder = autoPrintOnOrder,
                            isConnected = isConnected
                        )
                        viewModel.printTestReceipt(currentEntity) { res ->
                            isTestingPrint = false
                            testResultSuccess = res.success
                            testResultDialogMsg = res.message
                            if (res.success) {
                                isConnected = true
                            }
                        }
                    },
                    enabled = !isTestingPrint,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusReady),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("btn_test_print")
                ) {
                    if (isTestingPrint) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("🖨️ TEST PRINT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Real Test Print Result Dialog
    if (testResultDialogMsg != null) {
        AlertDialog(
            onDismissRequest = { testResultDialogMsg = null },
            icon = {
                Icon(
                    imageVector = if (testResultSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (testResultSuccess) StatusReady else StatusCancelled,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (testResultSuccess) "Test Print Sent" else "Test Print Failed",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = testResultDialogMsg ?: "",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { testResultDialogMsg = null },
                    modifier = Modifier.testTag("btn_dismiss_test_dialog")
                ) {
                    Text("OK", color = StatusPreparing, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }
}
