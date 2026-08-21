package com.restaurant.pos.data.syncv3

import android.content.Context

object LocalVersionTracker {
    private const val PREFS_NAME = "sync_versions_v1"

    fun getLocalVersion(context: Context, tableName: String, syncId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("${tableName}_${syncId}", 0L)
    }

    fun setLocalVersion(context: Context, tableName: String, syncId: String, version: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("${tableName}_${syncId}", version).apply()
    }

    fun removeLocalVersion(context: Context, tableName: String, syncId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("${tableName}_${syncId}").apply()
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
