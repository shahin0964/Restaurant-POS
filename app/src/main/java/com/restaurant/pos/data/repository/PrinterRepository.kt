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
            val shopName = if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) receiptSetting.shopName else "RESTAURANT POS"
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
     * Exactly matches layout from IMG_20260819_024407.jpg
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

            val rawSymbol = if (receiptSetting.currencySymbol.isNotBlank()) {
                receiptSetting.currencySymbol
            } else if (receiptSetting.currencyCode.isNotBlank()) {
                receiptSetting.currencyCode
            } else {
                "BDT"
            }

            // ESC/POS Thermal printers in standard single-byte code page mode corrupt non-ASCII characters like "৳" into Chinese characters like "唰".
            // Converting non-ASCII currency symbols to clean printer-safe ASCII string (e.g. "Tk") ensures 100% clean formatting and crisp printing.
            val currSymbol = when {
                rawSymbol == "৳" || rawSymbol.contains("\u09F3") -> "Tk"
                rawSymbol.all { it.code in 32..126 } -> rawSymbol
                else -> "Tk"
            }

            fun formatAmount(amount: Double): String {
                return if (amount % 1.0 == 0.0) {
                    String.format(Locale.US, "%.0f", amount)
                } else {
                    String.format(Locale.US, "%.2f", amount)
                }
            }

            fun formatMoney(amount: Double): String {
                val amtStr = formatAmount(amount)
                return if (currSymbol.isNotBlank()) "$currSymbol$amtStr" else amtStr
            }

            // ESC/POS Init
            stream.write(byteArrayOf(0x1B, 0x40))

            // Center Align for Shop Header
            stream.write(byteArrayOf(0x1B, 0x61, 0x01))

            // Shop Header
            if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) {
                stream.write(byteArrayOf(0x1D, 0x21, 0x11)) // Double height & width
                stream.write("${receiptSetting.shopName}\n".toByteArray(Charsets.UTF_8))
                stream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Normal size
            }

            if (receiptSetting.showAddress && receiptSetting.address.isNotBlank()) {
                stream.write("${receiptSetting.address}\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.showPhone && receiptSetting.phone.isNotBlank()) {
                stream.write("Tel: ${receiptSetting.phone}\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.email.isNotBlank()) {
                stream.write("Email: ${receiptSetting.email}\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.website.isNotBlank()) {
                stream.write("${receiptSetting.website}\n".toByteArray(Charsets.UTF_8))
            }

            val topDivider = "=".repeat(lineLength) + "\n"
            val subDivider = "-".repeat(lineLength) + "\n"

            stream.write(topDivider.toByteArray(Charsets.UTF_8))

            // Left Align for Order Details
            stream.write(byteArrayOf(0x1B, 0x61, 0x00))
            
            if (receiptSetting.showOrderNumber && order.orderNumber.isNotBlank()) {
                val formattedOrderNo = if (order.orderNumber.startsWith("#")) order.orderNumber else "#${order.orderNumber}"
                stream.write("Order No: $formattedOrderNo\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.showOrderType && order.orderType.isNotBlank()) {
                val typeWithTable = if (order.tableNumber.isNotBlank()) {
                    val tbl = if (order.tableNumber.lowercase().startsWith("table")) order.tableNumber else "Table ${order.tableNumber}"
                    "${order.orderType} ($tbl)"
                } else {
                    order.orderType
                }
                stream.write("Type    : $typeWithTable\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.showCustomerName && order.customerName.isNotBlank()) {
                stream.write("Customer: ${order.customerName}\n".toByteArray(Charsets.UTF_8))
            }

            if (receiptSetting.showDateTime && order.timestamp > 0) {
                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(order.timestamp))
                stream.write("Time    : $dateStr\n".toByteArray(Charsets.UTF_8))
            }

            if (order.note.isNotBlank()) {
                stream.write("Note    : ${order.note}\n".toByteArray(Charsets.UTF_8))
            }

            stream.write(subDivider.toByteArray(Charsets.UTF_8))

            // Items List
            if (receiptSetting.showItems && items.isNotEmpty()) {
                val itemColLen = if (is80mm) 26 else 18
                val qtyColLen = if (is80mm) 8 else 5
                val priceColLen = lineLength - itemColLen - qtyColLen

                val headerStr = String.format(
                    Locale.US,
                    "%-${itemColLen}s %${qtyColLen}s %${priceColLen}s\n",
                    "Item",
                    "Qty",
                    "Price"
                )
                stream.write(headerStr.toByteArray(Charsets.UTF_8))
                stream.write(subDivider.toByteArray(Charsets.UTF_8))

                for (item in items) {
                    val priceVal = item.pricePerUnit * item.quantity
                    val priceStr = formatMoney(priceVal)
                    val qtyStr = "x${item.quantity}"
                    val itemName = item.menuItemName

                    if (itemName.length <= itemColLen) {
                        val rowStr = String.format(
                            Locale.US,
                            "%-${itemColLen}s %${qtyColLen}s %${priceColLen}s\n",
                            itemName,
                            qtyStr,
                            priceStr
                        )
                        stream.write(rowStr.toByteArray(Charsets.UTF_8))
                    } else {
                        stream.write("$itemName\n".toByteArray(Charsets.UTF_8))
                        val rowStr = String.format(
                            Locale.US,
                            "%-${itemColLen}s %${qtyColLen}s %${priceColLen}s\n",
                            "",
                            qtyStr,
                            priceStr
                        )
                        stream.write(rowStr.toByteArray(Charsets.UTF_8))
                    }

                    if (item.note.isNotBlank()) {
                        stream.write("  Note: ${item.note}\n".toByteArray(Charsets.UTF_8))
                    }
                }
                stream.write(subDivider.toByteArray(Charsets.UTF_8))
            }

            // Totals Section (Right Aligned)
            fun writeRightAlignedRow(text: String, isBold: Boolean = false) {
                if (isBold) {
                    stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold ON
                }
                val rowStr = String.format(Locale.US, "%${lineLength}s\n", text)
                stream.write(rowStr.toByteArray(Charsets.UTF_8))
                if (isBold) {
                    stream.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold OFF
                }
            }

            if (receiptSetting.showSubtotal) {
                writeRightAlignedRow("Subtotal: ${formatMoney(order.subtotal)}")
            }

            if (receiptSetting.showDiscount && order.discount > 0) {
                writeRightAlignedRow("Discount: -${formatMoney(order.discount)}")
            }

            if (receiptSetting.showTax) {
                writeRightAlignedRow("Tax: ${formatMoney(order.tax)}")
            }

            if (receiptSetting.showTotal) {
                writeRightAlignedRow("TOTAL: ${formatMoney(order.total)}", isBold = true)
            }

            // Payment Status Section (Centered)
            stream.write("\n".toByteArray(Charsets.UTF_8))
            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center Align

            val paidStatus = if (order.isPaid || order.status.equals("Completed", ignoreCase = true)) "Paid" else "Unpaid"
            val payMethod = if (order.paymentMethod.isNotBlank()) order.paymentMethod else "Cash"
            stream.write("Payment : $payMethod ($paidStatus)\n".toByteArray(Charsets.UTF_8))

            stream.write(topDivider.toByteArray(Charsets.UTF_8))

            // Footer
            if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) {
                stream.write("${receiptSetting.footerText}\n".toByteArray(Charsets.UTF_8))
            }

            // Line feeds so cut occurs cleanly after footer
            stream.write("\n\n\n\n\n\n".toByteArray(Charsets.UTF_8))

            // Cut Paper Command
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

                val chunkSize = 128
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(chunkSize, bytes.size - offset)
                    os.write(bytes, offset, count)
                    os.flush()
                    offset += count
                    Thread.sleep(30)
                }

                Thread.sleep(400)
                try { os.close() } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
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

            val chunkSize = 512
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(chunkSize, bytes.size - offset)
                os.write(bytes, offset, count)
                os.flush()
                offset += count
                Thread.sleep(20)
            }

            Thread.sleep(300)
            try { os.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
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

            val maxPacket = endpoint.maxPacketSize.coerceAtLeast(64)
            val chunkSize = minOf(maxPacket, 512)
            var offset = 0
            var totalSent = 0
            var failed = false

            while (offset < bytes.size) {
                val length = minOf(chunkSize, bytes.size - offset)
                val chunk = bytes.copyOfRange(offset, offset + length)
                val transferred = connection.bulkTransfer(endpoint, chunk, length, 3000)
                if (transferred < 0) {
                    failed = true
                    break
                }
                totalSent += transferred
                offset += length
            }

            try { connection.releaseInterface(usbInterface) } catch (_: Exception) {}
            try { connection.close() } catch (_: Exception) {}

            if (!failed && totalSent > 0) {
                PrintResult(true, "Printed successfully via USB (${targetDevice.productName ?: targetDevice.deviceName})")
            } else {
                PrintResult(false, "USB printer rejected data transfer")
            }
        } catch (e: Exception) {
            try { connection.close() } catch (_: Exception) {}
            PrintResult(false, "USB print failed: ${e.message ?: "Transfer error"}")
        }
    }
}
