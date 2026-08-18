package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printer_settings")
data class PrinterSettingEntity(
    @PrimaryKey val id: Int = 1,
    val connectionType: String = "BUILT_IN", // "BUILT_IN", "BLUETOOTH", "WIFI_LAN", "USB"
    val printerName: String = "",
    val macAddress: String = "",
    val ipAddress: String = "192.168.1.100",
    val port: Int = 9100,
    val paperSize: String = "58mm", // "58mm", "80mm"
    val autoPrintOnOrder: Boolean = true,
    val isConnected: Boolean = false,
    val printerType: String = "Sunmi InnerPrinter",
    val bluetoothAddress: String = ""
)

