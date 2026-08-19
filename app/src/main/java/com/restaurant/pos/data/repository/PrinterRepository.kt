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
                bluetoothAdapter.bondedDevices?.forEach { dev ->
                    val name = try { dev.name } catch (_: SecurityException) { null } ?: dev.address
                    list.add(DiscoveredPrinterDevice(name = name, address = dev.address, connectionType = "BLUETOOTH"))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getConnectedUsbDevices(): List<DiscoveredPrinterDevice> {
        val list = mutableListOf<DiscoveredPrinterDevice>()
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            usbManager?.deviceList?.values?.forEach { dev ->
                val name = dev.productName ?: dev.deviceName ?: "USB Printer"
                list.add(DiscoveredPrinterDevice(name = name, address = dev.deviceName, connectionType = "USB"))
            }
        } catch (_: Exception) {}
        return list
    }

    fun isSunmiDevice(): Boolean {
        return true
    }

    fun buildTestReceiptBitmap(setting: PrinterSettingEntity, receiptSetting: ReceiptSettingEntity): Bitmap {
        val targetWidth = if (setting.paperSize == "80mm") 576 else 384
        val tempBitmap = Bitmap.createBitmap(targetWidth, 3000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        val normalPaint = TextPaint().apply { color = Color.BLACK; textSize = 22f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val boldPaint = TextPaint().apply { color = Color.BLACK; textSize = 22f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerPaint = TextPaint().apply { color = Color.BLACK; textSize = 28f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

        var y = 20f
        fun drawCentered(text: String, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, targetWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.1f).setIncludePad(false).build()
            canvas.save(); canvas.translate(0f, y); layout.draw(canvas); canvas.restore()
            y += layout.height + 8f
        }
        fun drawDivider(char: String = "-") { drawCentered(char.repeat(if (targetWidth == 576) 48 else 32), normalPaint) }
        fun drawLeftRight(left: String, right: String, isBold: Boolean = false) {
            val p = if (isBold) boldPaint else normalPaint
            val rightWidth = p.measureText(right)
            val maxLeftWidth = (targetWidth - rightWidth - 8f).coerceAtLeast(40f)
            val leftLayout = StaticLayout.Builder.obtain(left, 0, left.length, p, maxLeftWidth.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.1f).setIncludePad(false).build()
            canvas.save(); canvas.translate(0f, y); leftLayout.draw(canvas); canvas.restore()
            canvas.drawText(right, targetWidth - rightWidth, y + leftLayout.getLineBaseline(0), p)
            y += maxOf(leftLayout.height.toFloat(), p.textSize) + 8f
        }

        drawCentered(if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) receiptSetting.shopName else "RESTAURANT POS", headerPaint)
        drawCentered("PRINTER TEST", boldPaint)
        drawDivider("=")
        drawLeftRight("Connection", setting.connectionType)
        drawLeftRight("Printer", "SUNMI V2 PRO Built-in")
        drawLeftRight("Paper Size", setting.paperSize)
        drawLeftRight("Date/Time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        drawDivider("=")
        drawCentered("TEST PRINT SUCCESS", boldPaint)
        if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) {
            drawDivider("-")
            drawCentered(receiptSetting.footerText, normalPaint)
        }
        y += 20f
        return Bitmap.createBitmap(tempBitmap, 0, 0, targetWidth, y.toInt().coerceAtLeast(10))
    }

    fun buildReceiptBitmap(orderWithItems: OrderWithItems, receiptSetting: ReceiptSettingEntity, paperSize: String = "58mm"): Bitmap {
        val is80mm = paperSize == "80mm"
        val targetWidth = if (is80mm) 576 else 384
        val tempBitmap = Bitmap.createBitmap(targetWidth, 5000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        val normalPaint = TextPaint().apply { color = Color.BLACK; textSize = 21f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val boldPaint = TextPaint().apply { color = Color.BLACK; textSize = 21f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerPaint = TextPaint().apply { color = Color.BLACK; textSize = 28f; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

        var y = 16f
        fun drawCentered(text: String, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, targetWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.1f).setIncludePad(false).build()
            canvas.save(); canvas.translate(0f, y); layout.draw(canvas); canvas.restore()
            y += layout.height + 6f
        }
        fun drawDivider(char: String = "-") { drawCentered(char.repeat(if (is80mm) 48 else 32), normalPaint) }
        fun drawLeftRight(left: String, right: String, isBold: Boolean = false) {
            val p = if (isBold) boldPaint else normalPaint
            val rightWidth = p.measureText(right)
            val maxLeftWidth = (targetWidth - rightWidth - 8f).coerceAtLeast(40f)
            val leftLayout = StaticLayout.Builder.obtain(left, 0, left.length, p, maxLeftWidth.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.1f).setIncludePad(false).build()
            canvas.save(); canvas.translate(0f, y); leftLayout.draw(canvas); canvas.restore()
            canvas.drawText(right, targetWidth - rightWidth, y + leftLayout.getLineBaseline(0), p)
            y += maxOf(leftLayout.height.toFloat(), p.textSize) + 6f
        }

        val currSymbol = if (receiptSetting.currencySymbol.isNotBlank()) receiptSetting.currencySymbol else (receiptSetting.currencyCode.ifBlank { "BDT" })
        fun formatMoney(amount: Double): String {
            val amt = if (amount % 1.0 == 0.0) String.format(Locale.US, "%.0f", amount) else String.format(Locale.US, "%.2f", amount)
            return if (currSymbol.isNotBlank()) "$currSymbol $amt" else amt
        }

        val order = orderWithItems.order
        if (receiptSetting.showShopName && receiptSetting.shopName.isNotBlank()) drawCentered(receiptSetting.shopName, headerPaint)
        if (receiptSetting.showAddress && receiptSetting.address.isNotBlank()) drawCentered(receiptSetting.address, normalPaint)
        if (receiptSetting.showPhone && receiptSetting.phone.isNotBlank()) drawCentered("Tel: ${receiptSetting.phone}", normalPaint)
        drawDivider("=")

        if (receiptSetting.showOrderNumber) drawLeftRight("Order No:", order.orderNumber, isBold = true)
        if (receiptSetting.showOrderType) drawLeftRight("Type:", order.orderType)
        if (receiptSetting.showCustomerName) drawLeftRight("Customer:", order.customerName)
        if (receiptSetting.showDateTime) drawLeftRight("Time:", SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(order.timestamp)))
        drawDivider("-")

        orderWithItems.items.forEach { item ->
            val priceStr = formatMoney(item.pricePerUnit * item.quantity)
            drawLeftRight(item.menuItemName, "x${item.quantity}  $priceStr")
        }
        drawDivider("-")
        if (receiptSetting.showSubtotal) drawLeftRight("Subtotal:", formatMoney(order.subtotal))
        if (receiptSetting.showDiscount && order.discount > 0) drawLeftRight("Discount:", "-${formatMoney(order.discount)}")
        if (receiptSetting.showTotal) drawLeftRight("TOTAL:", formatMoney(order.total), isBold = true)

        y += 8f
        drawCentered("Payment: ${order.paymentMethod} (${if (order.isPaid) "Paid" else "Unpaid"})", boldPaint)
        drawDivider("=")
        if (receiptSetting.showFooter && receiptSetting.footerText.isNotBlank()) drawCentered(receiptSetting.footerText, normalPaint)

        y += 24f
        return Bitmap.createBitmap(tempBitmap, 0, 0, targetWidth, y.toInt().coerceAtLeast(10))
    }

    suspend fun printTestReceipt(setting: PrinterSettingEntity): PrintResult = withContext(Dispatchers.IO) {
        val rSetting = receiptSettingDao?.getReceiptSettingSync() ?: ReceiptSettingEntity()
        val bitmap = buildTestReceiptBitmap(setting, rSetting)
        return@withContext printBitmap(bitmap, setting)
    }

    suspend fun printOrderReceipt(orderWithItems: OrderWithItems): PrintResult = withContext(Dispatchers.IO) {
        val pSetting = getPrinterSettingSync()
        val rSetting = receiptSettingDao?.getReceiptSettingSync() ?: ReceiptSettingEntity()
        val bitmap = buildReceiptBitmap(orderWithItems, rSetting, pSetting.paperSize)
        return@withContext printBitmap(bitmap, pSetting)
    }

    private suspend fun printBitmap(bitmap: Bitmap, setting: PrinterSettingEntity): PrintResult {
        if (setting.connectionType == "BUILT_IN") {
            val sunmiRes = printBitmapViaSunmiService(bitmap)
            if (sunmiRes.success) return sunmiRes

            // If AIDL fails, fallback to direct device nodes
            val bytes = bitmapToEscPosBytes(bitmap)
            val fallbackRes = printToBuiltInHardwareNodes(bytes)
            if (fallbackRes.success) return fallbackRes
            return sunmiRes
        }

        val bytes = bitmapToEscPosBytes(bitmap)
        return sendBytesToPrinterHardware(bytes, setting)
    }

    private suspend fun printBitmapViaSunmiService(bitmap: Bitmap): PrintResult {
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
                        val stubClass = try {
                            Class.forName("${descriptor}\$Stub")
                        } catch (_: Exception) {
                            try {
                                Class.forName("woyou.aidlservice.jiuiv5.IWoyouService\$Stub")
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (stubClass != null) {
                            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                            val proxy = asInterface.invoke(null, service)
                            if (proxy != null) {
                                val methods = proxy.javaClass.methods
                                val printBitmapMethod = methods.firstOrNull {
                                    it.name.equals("printBitmap", true) || it.name.equals("printBitmapCustom", true)
                                }
                                val lineWrapMethod = methods.firstOrNull { it.name.equals("lineWrap", true) }

                                if (printBitmapMethod != null) {
                                    val params = printBitmapMethod.parameterTypes
                                    if (params.size == 2 && params[0] == Bitmap::class.java) {
                                        val cbClass = params[1]
                                        val dummyCb = java.lang.reflect.Proxy.newProxyInstance(
                                            cbClass.classLoader,
                                            arrayOf(cbClass)
                                        ) { _, _, _ -> null }
                                        printBitmapMethod.invoke(proxy, bitmap, dummyCb)
                                    } else if (params.size == 1 && params[0] == Bitmap::class.java) {
                                        printBitmapMethod.invoke(proxy, bitmap)
                                    } else if (params.size == 3 && params[0] == Bitmap::class.java) {
                                        val cbClass = params[2]
                                        val dummyCb = java.lang.reflect.Proxy.newProxyInstance(
                                            cbClass.classLoader,
                                            arrayOf(cbClass)
                                        ) { _, _, _ -> null }
                                        printBitmapMethod.invoke(proxy, bitmap, 0, dummyCb)
                                    }

                                    try {
                                        if (lineWrapMethod != null) {
                                            val lParams = lineWrapMethod.parameterTypes
                                            if (lParams.size == 2 && lParams[0] == Int::class.javaPrimitiveType) {
                                                val cbClass = lParams[1]
                                                val dummyCb = java.lang.reflect.Proxy.newProxyInstance(
                                                    cbClass.classLoader,
                                                    arrayOf(cbClass)
                                                ) { _, _, _ -> null }
                                                lineWrapMethod.invoke(proxy, 4, dummyCb)
                                            }
                                        }
                                    } catch (_: Exception) {}

                                    deferred.complete(PrintResult(true, "Printed successfully via Sunmi Service"))
                                    return
                                }
                            }
                        }
                        deferred.complete(PrintResult(false, "Sunmi print method not found"))
                    } catch (e: Throwable) {
                        deferred.complete(PrintResult(false, "Sunmi error: ${e.message}"))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

            try {
                val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                if (bound) {
                    val result = withTimeoutOrNull(4000) { deferred.await() }
                    try { context.unbindService(conn) } catch (_: Exception) {}
                    if (result != null && result.success) return result
                }
            } catch (_: Exception) {
                try { context.unbindService(conn) } catch (_: Exception) {}
            }
        }

        return PrintResult(false, "Built-in printer unavailable on this device.")
    }

    private fun bitmapToEscPosBytes(bitmap: Bitmap): ByteArray {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val widthBytes = (originalWidth + 7) / 8
        val paddedWidth = widthBytes * 8

        val workingBitmap = if (originalWidth != paddedWidth) {
            val bmp = Bitmap.createBitmap(paddedWidth, originalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            bmp
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        stream.write(byteArrayOf(0x1B, 0x40))
        stream.write(byteArrayOf(0x1B, 0x61, 0x01))
        stream.write(byteArrayOf(0x1B, 0x33, 0x00))

        val pixels = IntArray(paddedWidth * originalHeight)
        workingBitmap.getPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, originalHeight)

        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()

        val sliceHeight = 48
        var currentY = 0

        while (currentY < originalHeight) {
            val chunkHeight = minOf(sliceHeight, originalHeight - currentY)
            val yL = (chunkHeight % 256).toByte()
            val yH = (chunkHeight / 256).toByte()

            val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH)
            stream.write(header)

            val rasterData = ByteArray(widthBytes * chunkHeight)
            var byteIdx = 0

            for (row in 0 until chunkHeight) {
                val y = currentY + row
                for (xByte in 0 until widthBytes) {
                    var currentByte = 0
                    for (bit in 0 until 8) {
                        val x = xByte * 8 + bit
                        val pixel = pixels[y * paddedWidth + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        if (luminance < 160) {
                            currentByte = currentByte or (0x80 shr bit)
                        }
                    }
                    rasterData[byteIdx++] = currentByte.toByte()
                }
            }

            stream.write(rasterData)
            currentY += chunkHeight
        }

        stream.write(byteArrayOf(0x1B, 0x64, 0x04))
        stream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00))

        return stream.toByteArray()
    }

    private suspend fun sendBytesToPrinterHardware(bytes: ByteArray, setting: PrinterSettingEntity): PrintResult {
        return when (setting.connectionType) {
            "BUILT_IN" -> printToBuiltInHardwareNodes(bytes)
            "BLUETOOTH" -> printToBluetoothPrinter(bytes, setting.macAddress)
            "WIFI_LAN" -> printToWifiPrinter(bytes, setting.ipAddress, setting.port)
            "USB" -> printToUsbPrinter(bytes, setting.printerName)
            else -> PrintResult(false, "Unknown connection type: ${setting.connectionType}")
        }
    }

    private suspend fun printToBuiltInHardwareNodes(bytes: ByteArray): PrintResult {
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
                    return PrintResult(true, "Printed successfully to POS hardware port ($nodePath)")
                } catch (_: Exception) {}
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

        return PrintResult(false, "Built-in printer unavailable on this device.")
    }

    private fun printToBluetoothPrinter(bytes: ByteArray, macAddress: String): PrintResult {
        if (macAddress.isBlank()) {
            return PrintResult(false, "No Bluetooth printer selected.")
        }
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return PrintResult(false, "Bluetooth is not supported on this device")
            if (!bluetoothAdapter.isEnabled) {
                return PrintResult(false, "Bluetooth is turned off")
            }
            val device: BluetoothDevice = try {
                bluetoothAdapter.getRemoteDevice(macAddress)
            } catch (_: IllegalArgumentException) {
                return PrintResult(false, "Invalid Bluetooth MAC address: $macAddress")
            }
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            bluetoothAdapter.cancelDiscovery()
            val socket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (_: SecurityException) {
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
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) {}
                PrintResult(false, "Bluetooth connection failed: ${e.message}")
            }
        } catch (e: Exception) {
            return PrintResult(false, "Bluetooth error: ${e.message}")
        }
    }

    private fun printToWifiPrinter(bytes: ByteArray, ipAddress: String, port: Int): PrintResult {
        if (ipAddress.isBlank()) {
            return PrintResult(false, "Invalid IP address")
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
        } catch (e: Exception) {
            PrintResult(false, "Wi-Fi connection failed: ${e.message}")
        }
    }

    private fun printToUsbPrinter(bytes: ByteArray, printerName: String): PrintResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return PrintResult(false, "USB Service unavailable")
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            return PrintResult(false, "USB printing not supported.")
        }
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            return PrintResult(false, "No USB printer attached")
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
            return PrintResult(false, "USB permission denied")
        }
        val usbInterface = (0 until targetDevice.interfaceCount)
            .map { targetDevice.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            ?: targetDevice.getInterface(0)
        val endpoint = (0 until usbInterface.endpointCount)
            .map { usbInterface.getEndpoint(it) }
            .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (endpoint == null) {
            return PrintResult(false, "No USB output endpoint found")
        }
        val connection = usbManager.openDevice(targetDevice)
            ?: return PrintResult(false, "Could not open USB connection")
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
                PrintResult(false, "USB printer rejected transfer")
            }
        } catch (e: Exception) {
            try { connection.close() } catch (_: Exception) {}
            PrintResult(false, "USB print failed: ${e.message}")
        }
    }
}
