package com.restaurant.pos.data.repository

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.data.db.PrinterSettingDao
import com.restaurant.pos.data.db.PrinterSettingEntity
import com.restaurant.pos.data.db.ReceiptSettingDao
import com.restaurant.pos.data.db.ReceiptSettingEntity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PrintResult(
    val success: Boolean,
    val message: String
)

data class DiscoveredPrinterDevice(
    val name: String,
    val address: String,
    val connectionType: String
)

class PrinterRepository(
    private val context: Context,
    private val printerSettingDao: PrinterSettingDao,
    private val receiptSettingDao: ReceiptSettingDao? = null
) {
    val printerSetting: Flow<PrinterSettingEntity?> = printerSettingDao.getPrinterSetting()
    val receiptSetting: Flow<ReceiptSettingEntity?> = receiptSettingDao?.getReceiptSetting() ?: flowOf(null)

    suspend fun getPrinterSettingSync(): PrinterSettingEntity {
        return printerSettingDao.getPrinterSettingSync() ?: PrinterSettingEntity()
    }

    suspend fun savePrinterSetting(setting: PrinterSettingEntity) {
        printerSettingDao.savePrinterSetting(setting)
    }

    suspend fun saveReceiptSetting(setting: ReceiptSettingEntity) {
        receiptSettingDao?.saveReceiptSetting(setting)
    }

    /**
     * Discovers paired Bluetooth devices (REAL devices only)
     */
    fun getPairedBluetoothDevices(): List<DiscoveredPrinterDevice> {
        val list = mutableListOf<DiscoveredPrinterDevice>()
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val bonded = bluetoothAdapter.bondedDevices
                bonded?.forEach { dev ->
                    val name = try { dev.name } catch (e: SecurityException) { null } ?: dev.address
                    list.add(DiscoveredPrinterDevice(name = name, address = dev.address, connectionType = "BLUETOOTH"))
                }
            }
        } catch (e: SecurityException) {
            Log.e("PrinterRepository", "Bluetooth permission denied when querying paired devices", e)
        } catch (e: Exception) {
            Log.e("PrinterRepository", "Error getting Bluetooth devices", e)
        }
        return list
    }

    /**
     * Discovers connected USB devices (REAL devices only)
     */
    fun getConnectedUsbDevices(): List<DiscoveredPrinterDevice> {
        val list = mutableListOf<DiscoveredPrinterDevice>()
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            usbManager?.deviceList?.values?.forEach { dev ->
                val name = dev.productName ?: dev.deviceName ?: "USB Printer (${dev.vendorId}:${dev.productId})"
                list.add(DiscoveredPrinterDevice(name = name, address = dev.deviceName, connectionType = "USB"))
            }
        } catch (e: Exception) {
            Log.e("PrinterRepository", "Error getting USB devices", e)
        }
        return list
    }

    /**
     * Checks if current hardware is SUNMI or has built-in thermal printer
     */
    fun isSunmiDevice(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER ?: ""
        val model = android.os.Build.MODEL ?: ""
        val brand = android.os.Build.BRAND ?: ""
        if (manufacturer.contains("SUNMI", ignoreCase = true) ||
            model.contains("SUNMI", ignoreCase = true) ||
            brand.contains("SUNMI", ignoreCase = true)
        ) {
            return true
        }
        return try {
            Class.forName("com.sunmi.peripheral.printer.InnerPrinter")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Generates ESC/POS byte sequence for dynamic test receipt
     */
    fun buildTestReceiptBytes(
        setting: PrinterSettingEntity,
        receiptSetting: ReceiptSettingEntity
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        try {
            // ESC/POS Init
            stream.write(byteArrayOf(0x1B, 0x40))

            // Center Align
            stream.write(byteArrayOf(0x1B, 0x61, 0x01))

            // Double height & width for header
            stream.write(byteArrayOf(0x1D, 0x21, 0x11))
            val shopName = if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) receiptSetting.shopName else "DYNAMIC RESTAURANT"
            stream.write("$shopName\n".toByteArray(Charsets.UTF_8))
            
            // Normal text
            stream.write(byteArrayOf(0x1D, 0x21, 0x00))
            stream.write("PRINTER TEST\n".toByteArray(Charsets.UTF_8))

            val divider = if (setting.paperSize == "80mm") {
                "================================================\n"
            } else {
                "================================\n"
            }
            stream.write(divider.toByteArray(Charsets.UTF_8))

            // Left Align
            stream.write(byteArrayOf(0x1B, 0x61, 0x00))
            stream.write("Connection: ${setting.connectionType}\n".toByteArray(Charsets.UTF_8))
            
            val printerLabel = when (setting.connectionType) {
                "BLUETOOTH" -> if (setting.printerName.isNotBlank()) "${setting.printerName} (${setting.macAddress})" else setting.macAddress
                "WIFI_LAN" -> "${setting.ipAddress}:${setting.port}"
                "USB" -> if (setting.printerName.isNotBlank()) setting.printerName else "Attached USB Printer"
                else -> "SUNMI / POS Built-in Thermal Printer"
            }
            stream.write("Printer   : $printerLabel\n".toByteArray(Charsets.UTF_8))
            stream.write("Paper Size: ${setting.paperSize}\n".toByteArray(Charsets.UTF_8))

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            stream.write("Date/Time : $dateStr\n".toByteArray(Charsets.UTF_8))

            stream.write(divider.toByteArray(Charsets.UTF_8))

            // Center align for status
            stream.write(byteArrayOf(0x1B, 0x61, 0x01))
            stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold
            stream.write("TEST PRINT\nSUCCESS\n".toByteArray(Charsets.UTF_8))
            stream.write(byteArrayOf(0x1B, 0x45, 0x00))

            if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) {
                stream.write("\n${receiptSetting.footerText}\n".toByteArray(Charsets.UTF_8))
            }

            stream.write("\n\n\n".toByteArray(Charsets.UTF_8))
            // Paper Cut Command
            stream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00))
        } catch (e: Exception) {
            Log.e("PrinterRepository", "Error formatting test receipt bytes", e)
        }
        return stream.toByteArray()
    }

    /**
     * Generates ESC/POS byte sequence for physical receipt printing based on OrderWithItems and ReceiptSettingEntity
     */
    fun buildReceiptBytes(
        orderWithItems: OrderWithItems,
        receiptSetting: ReceiptSettingEntity,
        paperSize: String = "58mm"
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        try {
            val order = orderWithItems.order
            val items = orderWithItems.items
            val is80mm = paperSize == "80mm"
            val lineLength = if (is80mm) 48 else 32
            val currSymbol = if (receiptSetting.currencySymbol.isNotBlank()) receiptSetting.currencySymbol else if (receiptSetting.currencyCode.isNotBlank()) receiptSetting.currencyCode else "BDT"

            // ESC/POS Init
            stream.write(byteArrayOf(0x1B, 0x40))

            // Center Align
            stream.write(byteArrayOf(0x1B, 0x61, 0x01))

            // Shop Header
            if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) {
                stream.write(byteArrayOf(0x1D, 0x21, 0x11))
                stream.write("${receiptSetting.shopName}\n".toByteArray(Charsets.UTF_8))
                stream.write(byteArrayOf(0x1D, 0x21, 0x00))
            }

            if (receiptSetting.showPhone && receiptSetting.phone.isNotBlank()) {
                stream.write("Tel: ${receiptSetting.phone}\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.showAddress && receiptSetting.address.isNotBlank()) {
                stream.write("${receiptSetting.address}\n".toByteArray(Charsets.UTF_8))
            }

            val topDivider = "=".repeat(lineLength) + "\n"
            val subDivider = "-".repeat(lineLength) + "\n"

            stream.write(topDivider.toByteArray(Charsets.UTF_8))

            // Left Align for Order Details
            stream.write(byteArrayOf(0x1B, 0x61, 0x00))
            
            stream.write("Order No: ${order.orderNumber}\n".toByteArray(Charsets.UTF_8))
            
            if (receiptSetting.showOrderType) {
                stream.write("Type    : ${order.orderType}\n".toByteArray(Charsets.UTF_8))
                if (order.tableNumber != null && order.tableNumber.isNotBlank()) {
                    stream.write("Table   : ${order.tableNumber}\n".toByteArray(Charsets.UTF_8))
                }
            }
            if (receiptSetting.showCustomerName && order.customerName.isNotBlank()) {
                stream.write("Customer: ${order.customerName}\n".toByteArray(Charsets.UTF_8))
            }
            if (receiptSetting.showDateTime) {
                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
                stream.write("Time    : $dateStr\n".toByteArray(Charsets.UTF_8))
            }
            stream.write(subDivider.toByteArray(Charsets.UTF_8))

            // Items List
            
            if (is80mm) {
                stream.write(String.format("%-26s %4s %14s\n", "Item", "Qty", "Price").toByteArray(Charsets.UTF_8))
            } else {
                stream.write(String.format("%-16s %3s %9s\n", "Item", "Qty", "Price").toByteArray(Charsets.UTF_8))
            }
            stream.write(subDivider.toByteArray(Charsets.UTF_8))

            for (item in items) {
                val maxNameLen = if (is80mm) 26 else 16
                val nameTrunc = if (item.menuItemName.length > maxNameLen) item.menuItemName.substring(0, maxNameLen) else item.menuItemName
                val qtyStr = "${item.quantity}"
                val priceVal = item.pricePerUnit * item.quantity

                val line = if (is80mm) {
                    String.format(Locale.US, "%-26s %4s %s%10.2f\n", nameTrunc, qtyStr, currSymbol, priceVal)
                } else {
                    String.format(Locale.US, "%-16s %3s %s%7.2f\n", nameTrunc, qtyStr, currSymbol, priceVal)
                }
                stream.write(line.toByteArray(Charsets.UTF_8))
            }
            stream.write(subDivider.toByteArray(Charsets.UTF_8))
            

            // Totals
            val labelCol = if (is80mm) 32 else 18
            val fmtStr = "%-${labelCol}s %s%8.2f\n"
            
            stream.write(String.format(Locale.US, fmtStr, "Subtotal:", currSymbol, order.subtotal).toByteArray(Charsets.UTF_8))
            
            if (order.discount > 0) {
                stream.write(String.format(Locale.US, fmtStr, "Discount:", currSymbol, order.discount).toByteArray(Charsets.UTF_8))
            }
            if (order.tax > 0) {
                stream.write(String.format(Locale.US, fmtStr, "Tax:", currSymbol, order.tax).toByteArray(Charsets.UTF_8))
            }
            
            // Bold Total
            stream.write(byteArrayOf(0x1B, 0x45, 0x01))
            stream.write(String.format(Locale.US, fmtStr, "TOTAL:", currSymbol, order.total).toByteArray(Charsets.UTF_8))
            stream.write(byteArrayOf(0x1B, 0x45, 0x00))
            
            
            stream.write("Payment : ${order.paymentMethod}\n".toByteArray(Charsets.UTF_8))
            
            stream.write(topDivider.toByteArray(Charsets.UTF_8))

            // Footer
            if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) {
                stream.write(byteArrayOf(0x1B, 0x61, 0x01))
                stream.write("${receiptSetting.footerText}\n\n\n\n".toByteArray(Charsets.UTF_8))
            } else {
                stream.write("\n\n\n\n".toByteArray(Charsets.UTF_8))
            }

            // Cut Paper
            stream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00))
        } catch (e: Exception) {
            Log.e("PrinterRepository", "Error formatting receipt bytes", e)
        }
        return stream.toByteArray()
    }

    /**
     * Performs a REAL Test Print over the selected printer configuration
     */
    suspend fun printTestReceipt(setting: PrinterSettingEntity): PrintResult = withContext(Dispatchers.IO) {
        val rSetting = receiptSettingDao?.getReceiptSettingSync() ?: ReceiptSettingEntity()
        val bytes = buildTestReceiptBytes(setting, rSetting)
        
        android.util.Log.d("DiagnosticAudit", "PRINT TYPE = TEST_RECEIPT")
        android.util.Log.d("DiagnosticAudit", "BYTE LENGTH = ${bytes.size}")
        android.util.Log.d("DiagnosticAudit", "TEST BUILDER CALLED = true")
        
        return@withContext sendBytesToPrinterHardware(bytes, setting)
    }

    /**
     * Performs a REAL Order Print using saved printer settings
     */
    suspend fun printOrderReceipt(orderWithItems: OrderWithItems): PrintResult = withContext(Dispatchers.IO) {
        val pSetting = getPrinterSettingSync()
        val rSetting = receiptSettingDao?.getReceiptSettingSync() ?: ReceiptSettingEntity()
        val bytes = buildReceiptBytes(orderWithItems, rSetting, pSetting.paperSize)
        
        android.util.Log.d("DiagnosticAudit", "PRINT TYPE = REAL_RECEIPT")
        android.util.Log.d("DiagnosticAudit", "ORDER ID = ${orderWithItems.order.id}")
        android.util.Log.d("DiagnosticAudit", "BYTE LENGTH = ${bytes.size}")
        android.util.Log.d("DiagnosticAudit", "RECEIPT BUILDER = buildReceiptBytes")
        android.util.Log.d("DiagnosticAudit", "TEST BUILDER CALLED = false")
        
        return@withContext sendBytesToPrinterHardware(bytes, pSetting)
    }

    /**
     * Route raw ESC/POS byte sequence to the physical printer hardware socket/endpoint based on connectionType
     */
    private suspend fun sendBytesToPrinterHardware(bytes: ByteArray, setting: PrinterSettingEntity): PrintResult {
        android.util.Log.d("DiagnosticAudit", "PRINTER TYPE = ${setting.connectionType}")
        return when (setting.connectionType) {
            "BUILT_IN" -> printToBuiltInPrinter(bytes)
            "BLUETOOTH" -> printToBluetoothPrinter(bytes, setting.macAddress)
            "WIFI_LAN" -> printToWifiPrinter(bytes, setting.ipAddress, setting.port)
            "USB" -> printToUsbPrinter(bytes, setting.printerName)
            else -> PrintResult(false, "Unknown connection type: ${setting.connectionType}")
        }
    }

    private suspend fun printToBuiltInPrinter(bytes: ByteArray): PrintResult {
        // 1. Try Sunmi SDK InnerPrinter via Reflection if available in classpath
        try {
            val innerPrinterClass = try {
                Class.forName("com.sunmi.peripheral.printer.InnerPrinter")
            } catch (e: ClassNotFoundException) {
                null
            }
            if (innerPrinterClass != null) {
                val getInstanceMethod = innerPrinterClass.getMethod("getInstance")
                val printerInstance = getInstanceMethod.invoke(null)
                if (printerInstance != null) {
                    val cbClass = try {
                        Class.forName("com.sunmi.peripheral.printer.InnerResultCallback")
                    } catch (_: Exception) { null }

                    val sendRawMethod = if (cbClass != null) {
                        try { printerInstance.javaClass.getMethod("sendRAWData", ByteArray::class.java, cbClass) } catch (_: Exception) { null }
                    } else null

                    if (sendRawMethod != null && cbClass != null) {
                        val dummyCallback = java.lang.reflect.Proxy.newProxyInstance(
                            cbClass.classLoader,
                            arrayOf(cbClass)
                        ) { _, _, _ -> null }
                        sendRawMethod.invoke(printerInstance, bytes, dummyCallback)
                        return PrintResult(true, "Printed successfully via SUNMI InnerPrinter SDK")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w("PrinterRepository", "Sunmi InnerPrinter SDK reflection call not available", e)
        }

        // 2. Try binding to Android POS system printer AIDL services (Sunmi / WOYOU / iMin / iPOS)
        val aidlResult = printViaSystemPrinterService(bytes)
        if (aidlResult.success) {
            return aidlResult
        }

        // 3. Try Linux character device nodes common in Android POS terminals (/dev/ttyHSL1, /dev/ttyMT0, /dev/ttyMT1, /dev/usb/lp0)
        val devNodes = listOf(
            "/dev/ttyHSL1",
            "/dev/ttyMT0",
            "/dev/ttyMT1",
            "/dev/ttyS1",
            "/dev/ttyS3",
            "/dev/usb/lp0",
            "/dev/usb/lp1",
            "/dev/printer"
        )
        for (nodePath in devNodes) {
            val file = File(nodePath)
            if (file.exists() && file.canWrite()) {
                try {
                    FileOutputStream(file).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }
                    return PrintResult(true, "Printed successfully to POS thermal hardware port ($nodePath)")
                } catch (e: Exception) {
                    Log.w("PrinterRepository", "Failed writing to POS device node $nodePath", e)
                }
            }
        }

        // 4. Try internal loopback POS print service sockets (port 9100 / 9101 on 127.0.0.1 used by local thermal daemons)
        for (port in listOf(9100, 9101, 8888)) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 800)
                val os = socket.outputStream
                os.write(bytes)
                os.flush()
                Thread.sleep(200)
                os.close()
                socket.close()
                return PrintResult(true, "Printed successfully to built-in thermal daemon (127.0.0.1:$port)")
            } catch (_: Exception) {
                // Not listening on this loopback port
            }
        }

        // 5. If none of the actual physical built-in interfaces succeeded
        return PrintResult(false, "Built-in thermal printer not detected or service unavailable on this device. Please check hardware connection or choose Bluetooth / Wi-Fi / USB.")
    }

    /**
     * Binds to POS vendor print services (e.g., Sunmi WOYOU service) via AIDL IPC and sends raw ESC/POS byte array
     */
    private suspend fun printViaSystemPrinterService(bytes: ByteArray): PrintResult {
        val serviceIntents = listOf(
            Intent().apply {
                setPackage("woyou.aidlservice.jiuiv5")
                action = "woyou.aidlservice.jiuiv5.IWoyouService"
            },
            Intent().apply {
                setPackage("com.sunmi.peripheral.printer")
                action = "com.sunmi.peripheral.printer.SunmiPrinterService"
            },
            Intent().apply {
                setPackage("com.pos.printer.service")
                action = "com.pos.printer.service.PrinterService"
            },
            Intent().apply {
                setPackage("com.iposprinter.iposprinterservice")
                action = "com.iposprinter.iposprinterservice.IPosPrinterService"
            }
        )

        for (intent in serviceIntents) {
            val deferred = CompletableDeferred<PrintResult>()
            var serviceConnected = false

            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service == null) {
                        deferred.complete(PrintResult(false, "Printer service binder is null"))
                        return
                    }
                    serviceConnected = true
                    try {
                        // Look for sendRAWData or sendRawData method on the AIDL Stub/Proxy
                        val descriptor = service.interfaceDescriptor ?: ""
                        var executed = false

                        // Try calling sendRAWData via reflection on the Stub.asInterface
                        val stubClass = try {
                            Class.forName("${descriptor}\$Stub")
                        } catch (e: ClassNotFoundException) {
                            try {
                                Class.forName("woyou.aidlservice.jiuiv5.IWoyouService\$Stub")
                            } catch (_: ClassNotFoundException) {
                                try {
                                    Class.forName("com.sunmi.peripheral.printer.SunmiPrinterService\$Stub")
                                } catch (_: ClassNotFoundException) {
                                    null
                                }
                            }
                        }

                        if (stubClass != null) {
                            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                            val proxy = asInterface.invoke(null, service)
                            if (proxy != null) {
                                val methods = proxy.javaClass.methods
                                val sendRaw = methods.firstOrNull { it.name.equals("sendRAWData", ignoreCase = true) }
                                if (sendRaw != null) {
                                    val paramTypes = sendRaw.parameterTypes
                                    if (paramTypes.size == 1 && paramTypes[0] == ByteArray::class.java) {
                                        sendRaw.invoke(proxy, bytes)
                                        executed = true
                                    } else if (paramTypes.size == 2 && paramTypes[0] == ByteArray::class.java) {
                                        val cbClass = paramTypes[1]
                                        val dummyCb = try {
                                            java.lang.reflect.Proxy.newProxyInstance(
                                                cbClass.classLoader,
                                                arrayOf(cbClass)
                                            ) { _, _, _ -> null }
                                        } catch (_: Exception) { null }

                                        try {
                                            if (dummyCb != null) {
                                                sendRaw.invoke(proxy, bytes, dummyCb)
                                            } else {
                                                sendRaw.invoke(proxy, bytes, null)
                                            }
                                            executed = true
                                        } catch (e: Exception) {
                                            try {
                                                sendRaw.invoke(proxy, bytes, null)
                                                executed = true
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                        }

                        if (executed) {
                            deferred.complete(PrintResult(true, "Printed successfully via ${name?.packageName ?: "Built-in POS Service"}"))
                        } else {
                            deferred.complete(PrintResult(false, "Built-in printer service rejected raw data command"))
                        }
                    } catch (e: Throwable) {
                        Log.e("PrinterRepository", "Error printing via service ${name?.packageName}", e)
                        deferred.complete(PrintResult(false, "Printer service error: ${e.message}"))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    serviceConnected = false
                }
            }

            try {
                val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                if (bound) {
                    val result = withTimeoutOrNull(2500) {
                        deferred.await()
                    }
                    try {
                        context.unbindService(conn)
                    } catch (_: Exception) {}

                    if (result != null && result.success) {
                        return result
                    }
                }
            } catch (e: Exception) {
                Log.w("PrinterRepository", "Cannot bind to service ${intent.`package`}", e)
                try {
                    context.unbindService(conn)
                } catch (_: Exception) {}
            }
        }

        return PrintResult(false, "Could not bind to built-in printer service")
    }

    private fun printToBluetoothPrinter(bytes: ByteArray, macAddress: String): PrintResult {
        if (macAddress.isBlank()) {
            return PrintResult(false, "No Bluetooth printer selected. Please search and select a printer.")
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return PrintResult(false, "Bluetooth is not supported on this device")

            if (!bluetoothAdapter.isEnabled) {
                return PrintResult(false, "Bluetooth is turned off on this device")
            }

            val device: BluetoothDevice = try {
                bluetoothAdapter.getRemoteDevice(macAddress)
            } catch (e: IllegalArgumentException) {
                return PrintResult(false, "Invalid Bluetooth MAC address: $macAddress")
            }

            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID
            bluetoothAdapter.cancelDiscovery()

            val socket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: SecurityException) {
                return PrintResult(false, "Bluetooth permission denied")
            }

            return try {
                socket.connect()
                val os: OutputStream = socket.outputStream
                os.write(bytes)
                os.flush()
                Thread.sleep(500)
                os.close()
                socket.close()
                PrintResult(true, "Printed successfully via Bluetooth (${device.name ?: macAddress})")
            } catch (e: SecurityException) {
                PrintResult(false, "Bluetooth permission denied during connection")
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) {}
                PrintResult(false, "Bluetooth printer unavailable: ${e.message ?: "Connection failed"}")
            }
        } catch (e: SecurityException) {
            return PrintResult(false, "Bluetooth permission required for Bluetooth printer connection")
        } catch (e: Exception) {
            return PrintResult(false, "Bluetooth error: ${e.message ?: "Connection error"}")
        }
    }

    private fun printToWifiPrinter(bytes: ByteArray, ipAddress: String, port: Int): PrintResult {
        if (ipAddress.isBlank()) {
            return PrintResult(false, "Invalid IP address: IP cannot be blank")
        }

        val targetPort = if (port in 1..65535) port else 9100

        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, targetPort), 5000)
            val os: OutputStream = socket.outputStream
            os.write(bytes)
            os.flush()
            Thread.sleep(300)
            os.close()
            socket.close()
            PrintResult(true, "Printed successfully via Wi-Fi ($ipAddress:$targetPort)")
        } catch (e: SocketTimeoutException) {
            PrintResult(false, "Connection timeout to Wi-Fi printer at $ipAddress:$targetPort")
        } catch (e: UnknownHostException) {
            PrintResult(false, "Invalid IP address or host unreachable: $ipAddress")
        } catch (e: Exception) {
            PrintResult(false, "Network connection failed to $ipAddress:$targetPort - ${e.message ?: "Printer unavailable"}")
        }
    }

    private fun printToUsbPrinter(bytes: ByteArray, printerName: String): PrintResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return PrintResult(false, "USB Service unavailable")

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            return PrintResult(false, "USB printing is not supported on this device.")
        }

        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            return PrintResult(false, "No USB printer devices attached to this device")
        }

        val targetDevice: UsbDevice? = deviceList.values.firstOrNull { dev ->
            (printerName.isNotBlank() && (dev.deviceName == printerName || dev.productName == printerName)) ||
            dev.deviceClass == UsbConstants.USB_CLASS_PRINTER ||
            (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
        } ?: deviceList.values.firstOrNull()

        if (targetDevice == null) {
            return PrintResult(false, "Target USB printer not found")
        }

        if (!usbManager.hasPermission(targetDevice)) {
            return PrintResult(false, "USB permission denied for device ${targetDevice.productName ?: targetDevice.deviceName}")
        }

        val usbInterface = (0 until targetDevice.interfaceCount)
            .map { targetDevice.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            ?: targetDevice.getInterface(0)

        val endpoint = (0 until usbInterface.endpointCount)
            .map { usbInterface.getEndpoint(it) }
            .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }

        if (endpoint == null) {
            return PrintResult(false, "No USB output endpoint found on target printer")
        }

        val connection = usbManager.openDevice(targetDevice)
            ?: return PrintResult(false, "Could not open USB device connection")

        return try {
            connection.claimInterface(usbInterface, true)
            val bytesTransferred = connection.bulkTransfer(endpoint, bytes, bytes.size, 5000)
            connection.releaseInterface(usbInterface)
            connection.close()

            if (bytesTransferred >= 0) {
                PrintResult(true, "Printed successfully via USB (${targetDevice.productName ?: targetDevice.deviceName})")
            } else {
                PrintResult(false, "USB printer rejected data transfer")
            }
        } catch (e: Exception) {
            connection.close()
            PrintResult(false, "USB print failed: ${e.message ?: "Transfer error"}")
        }
    }
}
