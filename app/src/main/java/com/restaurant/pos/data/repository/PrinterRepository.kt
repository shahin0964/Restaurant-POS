package com.restaurant.pos.data.repository

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.data.db.PrinterSettingDao
import com.restaurant.pos.data.db.PrinterSettingEntity
import com.restaurant.pos.data.db.ReceiptSettingDao
import com.restaurant.pos.data.db.ReceiptSettingEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
     * Converts a rendered bitmap to standard ESC/POS raster image bytes (GS v 0).
     */
    private fun bitmapToEscPosBytes(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val stream = ByteArrayOutputStream()

        // Initialize printer
        stream.write(byteArrayOf(0x1B, 0x40))
        // Center alignment
        stream.write(byteArrayOf(0x1B, 0x61, 0x01))

        // GS v 0 m xL xH yL yH
        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
        stream.write(header)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rasterData = ByteArray(widthBytes * height)
        var byteIdx = 0

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var currentByte = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val luminance = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
                        if (luminance < 180) {
                            currentByte = currentByte or (0x80 shr bit)
                        }
                    }
                }
                rasterData[byteIdx++] = currentByte.toByte()
            }
        }
        stream.write(rasterData)

        // Line feeds & full paper cut
        stream.write(byteArrayOf(0x1B, 0x64, 0x05))
        stream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00))

        return stream.toByteArray()
    }

    /**
     * Builds Test Receipt directly into a bitmap ensuring Unicode Bangla & all characters render accurately.
     */
    fun buildTestReceiptBytes(
        setting: PrinterSettingEntity,
        receiptSetting: ReceiptSettingEntity
    ): ByteArray {
        val targetWidth = if (setting.paperSize == "80mm") 576 else 384
        val tempBitmap = Bitmap.createBitmap(targetWidth, 3000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        val normalPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val boldPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val headerPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 20f

        fun drawCentered(text: String, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, targetWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(0f, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 8f
        }

        fun drawDivider(char: String = "-") {
            val count = if (targetWidth == 576) 48 else 32
            val line = char.repeat(count)
            drawCentered(line, normalPaint)
        }

        fun drawLeftRight(left: String, right: String, isBold: Boolean = false) {
            val p = if (isBold) boldPaint else normalPaint
            val rightWidth = p.measureText(right)
            val maxLeftWidth = (targetWidth - rightWidth - 8f).coerceAtLeast(40f)

            val leftLayout = StaticLayout.Builder.obtain(left, 0, left.length, p, maxLeftWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(0f, y)
            leftLayout.draw(canvas)
            canvas.restore()

            val baseline = y + leftLayout.getLineBaseline(0)
            canvas.drawText(right, targetWidth - rightWidth, baseline, p)

            y += maxOf(leftLayout.height.toFloat(), p.textSize) + 8f
        }

        val shopName = if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) receiptSetting.shopName else "RESTAURANT POS"
        drawCentered(shopName, headerPaint)
        drawCentered("PRINTER TEST", boldPaint)
        drawDivider("=")

        val printerLabel = when (setting.connectionType) {
            "BLUETOOTH" -> if (setting.printerName.isNotBlank()) "${setting.printerName} (${setting.macAddress})" else setting.macAddress
            "WIFI_LAN" -> "${setting.ipAddress}:${setting.port}"
            "USB" -> if (setting.printerName.isNotBlank()) setting.printerName else "Attached USB Printer"
            else -> "SUNMI / POS Built-in Thermal"
        }

        drawLeftRight("Connection", setting.connectionType)
        drawLeftRight("Printer", printerLabel)
        drawLeftRight("Paper Size", setting.paperSize)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        drawLeftRight("Date/Time", dateStr)
        drawDivider("=")

        drawCentered("TEST PRINT SUCCESS", boldPaint)

        if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) {
            drawDivider("-")
            drawCentered(receiptSetting.footerText, normalPaint)
        }

        y += 20f
        val finalHeight = y.toInt().coerceAtLeast(10)
        val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, targetWidth, finalHeight)
        return bitmapToEscPosBytes(finalBitmap)
    }

    /**
     * Renders physical receipt with full Unicode Bangla font rendering onto an Android Canvas & exports as raster ESC/POS bytes.
     */
    fun buildReceiptBytes(
        orderWithItems: OrderWithItems,
        receiptSetting: ReceiptSettingEntity,
        paperSize: String = "58mm"
    ): ByteArray {
        val is80mm = paperSize == "80mm"
        val targetWidth = if (is80mm) 576 else 384

        val tempBitmap = Bitmap.createBitmap(targetWidth, 5000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        val normalPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 21f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val boldPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 21f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val headerPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 16f

        fun drawCentered(text: String, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, targetWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(0f, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 6f
        }

        fun drawDivider(char: String = "-") {
            val count = if (is80mm) 48 else 32
            val line = char.repeat(count)
            drawCentered(line, normalPaint)
        }

        fun drawLeftRight(left: String, right: String, isBold: Boolean = false) {
            val p = if (isBold) boldPaint else normalPaint
            val rightWidth = p.measureText(right)
            val maxLeftWidth = (targetWidth - rightWidth - 8f).coerceAtLeast(40f)

            val leftLayout = StaticLayout.Builder.obtain(left, 0, left.length, p, maxLeftWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(0f, y)
            leftLayout.draw(canvas)
            canvas.restore()

            val baseline = y + leftLayout.getLineBaseline(0)
            canvas.drawText(right, targetWidth - rightWidth, baseline, p)

            y += maxOf(leftLayout.height.toFloat(), p.textSize) + 6f
        }

        val currSymbol = if (receiptSetting.currencySymbol.isNotBlank()) receiptSetting.currencySymbol else (receiptSetting.currencyCode.ifBlank { "BDT" })

        fun formatAmount(amount: Double): String {
            return if (amount % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", amount)
            } else {
                String.format(Locale.US, "%.2f", amount)
            }
        }

        fun formatMoney(amount: Double): String {
            val amt = formatAmount(amount)
            return if (currSymbol.isNotBlank()) "$currSymbol $amt" else amt
        }

        val order = orderWithItems.order
        val items = orderWithItems.items

        // Header Section
        if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) {
            drawCentered(receiptSetting.shopName, headerPaint)
        }
        if (receiptSetting.showAddress && receiptSetting.address.isNotBlank()) {
            drawCentered(receiptSetting.address, normalPaint)
        }
        if (receiptSetting.showPhone && receiptSetting.phone.isNotBlank()) {
            drawCentered("Tel: ${receiptSetting.phone}", normalPaint)
        }
        if (receiptSetting.email.isNotBlank()) {
            drawCentered("Email: ${receiptSetting.email}", normalPaint)
        }
        if (receiptSetting.website.isNotBlank()) {
            drawCentered(receiptSetting.website, normalPaint)
        }

        drawDivider("=")

        // Order Details
        if (receiptSetting.showOrderNumber && order.orderNumber.isNotBlank()) {
            val formattedOrderNo = if (order.orderNumber.startsWith("#")) order.orderNumber else "#${order.orderNumber}"
            drawLeftRight("Order No:", formattedOrderNo, isBold = true)
        }
        if (receiptSetting.showOrderType && order.orderType.isNotBlank()) {
            val typeWithTable = if (order.tableNumber.isNotBlank()) {
                val tbl = if (order.tableNumber.lowercase().startsWith("table")) order.tableNumber else "Table ${order.tableNumber}"
                "${order.orderType} ($tbl)"
            } else {
                order.orderType
            }
            drawLeftRight("Type:", typeWithTable)
        }
        if (receiptSetting.showCustomerName && order.customerName.isNotBlank()) {
            drawLeftRight("Customer:", order.customerName)
        }
        if (receiptSetting.showDateTime && order.timestamp > 0) {
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(order.timestamp))
            drawLeftRight("Time:", dateStr)
        }
        if (order.note.isNotBlank()) {
            drawLeftRight("Note:", order.note)
        }

        drawDivider("-")

        // Items Table
        if (receiptSetting.showItems && items.isNotEmpty()) {
            drawLeftRight("Item", "Qty   Price", isBold = true)
            drawDivider("-")

            for (item in items) {
                val priceVal = item.pricePerUnit * item.quantity
                val priceStr = formatMoney(priceVal)
                val qtyStr = "x${item.quantity}"
                val rightSideText = "$qtyStr  $priceStr"

                val rightWidth = normalPaint.measureText(rightSideText)
                val maxItemWidth = (targetWidth - rightWidth - 8f).coerceAtLeast(80f)

                val itemLayout = StaticLayout.Builder.obtain(
                    item.menuItemName,
                    0,
                    item.menuItemName.length,
                    normalPaint,
                    maxItemWidth.toInt()
                )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .setIncludePad(false)
                    .build()

                canvas.save()
                canvas.translate(0f, y)
                itemLayout.draw(canvas)
                canvas.restore()

                val baseline = y + itemLayout.getLineBaseline(0)
                canvas.drawText(rightSideText, targetWidth - rightWidth, baseline, normalPaint)

                y += maxOf(itemLayout.height.toFloat(), normalPaint.textSize) + 6f

                if (item.note.isNotBlank()) {
                    val noteText = "  (${item.note})"
                    val noteLayout = StaticLayout.Builder.obtain(
                        noteText,
                        0,
                        noteText.length,
                        normalPaint,
                        (targetWidth - 16f).toInt()
                    )
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.1f)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate(0f, y)
                    noteLayout.draw(canvas)
                    canvas.restore()

                    y += noteLayout.height.toFloat() + 4f
                }
            }
            drawDivider("-")
        }

        // Totals Section
        if (receiptSetting.showSubtotal) {
            drawLeftRight("Subtotal:", formatMoney(order.subtotal))
        }
        if (receiptSetting.showDiscount && order.discount > 0) {
            drawLeftRight("Discount:", "-${formatMoney(order.discount)}")
        }
        if (receiptSetting.showTax) {
            drawLeftRight("Tax:", formatMoney(order.tax))
        }
        if (receiptSetting.showTotal) {
            drawLeftRight("TOTAL:", formatMoney(order.total), isBold = true)
        }

        // Payment Details
        y += 8f
        val paidStatus = if (order.isPaid || order.status.equals("Completed", ignoreCase = true)) "Paid" else "Unpaid"
        val payMethod = if (order.paymentMethod.isNotBlank()) order.paymentMethod else "Cash"
        drawCentered("Payment: $payMethod ($paidStatus)", boldPaint)

        drawDivider("=")

        // Footer
        if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) {
            drawCentered(receiptSetting.footerText, normalPaint)
        }

        y += 24f
        val finalHeight = y.toInt().coerceAtLeast(10)
        val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, targetWidth, finalHeight)
        return bitmapToEscPosBytes(finalBitmap)
    }

    suspend fun printTestReceipt(setting: PrinterSettingEntity): PrintResult = withContext(Dispatchers.IO) {
        val rSetting = receiptSettingDao?.getReceiptSettingSync() ?: ReceiptSettingEntity()
        val bytes = buildTestReceiptBytes(setting, rSetting)
        return@withContext sendBytesToPrinterHardware(bytes, setting)
    }

    suspend fun printOrderReceipt(orderWithItems: OrderWithItems): PrintResult = withContext(Dispatchers.IO) {
        val pSetting = getPrinterSettingSync()
        val rSetting = receiptSettingDao?.getReceiptSettingSync() ?: ReceiptSettingEntity()
        val bytes = buildReceiptBytes(orderWithItems, rSetting, pSetting.paperSize)
        return@withContext sendBytesToPrinterHardware(bytes, pSetting)
    }

    private suspend fun sendBytesToPrinterHardware(bytes: ByteArray, setting: PrinterSettingEntity): PrintResult {
        return when (setting.connectionType) {
            "BUILT_IN" -> printToBuiltInPrinter(bytes)
            "BLUETOOTH" -> printToBluetoothPrinter(bytes, setting.macAddress)
            "WIFI_LAN" -> printToWifiPrinter(bytes, setting.ipAddress, setting.port)
            "USB" -> printToUsbPrinter(bytes, setting.printerName)
            else -> PrintResult(false, "Unknown connection type: ${setting.connectionType}")
        }
    }

    private suspend fun printToBuiltInPrinter(bytes: ByteArray): PrintResult {
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

        val aidlResult = printViaSystemPrinterService(bytes)
        if (aidlResult.success) {
            return aidlResult
        }

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
            } catch (_: Exception) {}
        }

        return PrintResult(false, "Built-in thermal printer not detected or service unavailable on this device.")
    }

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
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service == null) {
                        deferred.complete(PrintResult(false, "Printer service binder is null"))
                        return
                    }
                    try {
                        val descriptor = service.interfaceDescriptor ?: ""
                        var executed = false
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

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            try {
                val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                if (bound) {
                    val result = withTimeoutOrNull(2500) { deferred.await() }
                    try { context.unbindService(conn) } catch (_: Exception) {}
                    if (result != null && result.success) return result
                }
            } catch (e: Exception) {
                Log.w("PrinterRepository", "Cannot bind to service ${intent.`package`}", e)
                try { context.unbindService(conn) } catch (_: Exception) {}
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
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
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
