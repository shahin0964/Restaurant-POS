package com.restaurant.pos.data.backupv2

import android.content.Context

/**
 * Handles exporting and restoring non-Room persistent preferences.
 */
object BackupPreferenceManagerV2 {

    val TARGET_PREFERENCES = listOf(
        "app_settings",
        "notification_settings",
        "auto_backup_settings"
    )

    fun exportPreferences(context: Context): Map<String, Map<String, Any?>> {
        val exportMap = mutableMapOf<String, Map<String, Any?>>()
        for (prefName in TARGET_PREFERENCES) {
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            if (allEntries.isNotEmpty()) {
                exportMap[prefName] = allEntries
            }
        }
        return exportMap
    }

    fun restorePreferences(context: Context, preferencesData: Map<String, Map<String, Any?>>) {
        for ((prefName, entries) in preferencesData) {
            if (!TARGET_PREFERENCES.contains(prefName)) continue
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.clear()
            for ((key, value) in entries) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Double -> editor.putLong(key, java.lang.Double.doubleToRawLongBits(value)) // edge case fallback
                    is String -> editor.putString(key, value)
                    else -> if (value != null) editor.putString(key, value.toString())
                }
            }
            editor.apply()
        }
    }
}
