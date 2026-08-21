package com.restaurant.pos

import android.app.Application
import com.restaurant.pos.data.backupv2.AutoBackupSchedulerV2
import com.restaurant.pos.data.db.AppDatabase
import com.restaurant.pos.data.syncv3.RealtimeCloudListener
import com.restaurant.pos.data.syncv3.RealtimeSyncRepository

class POSApplication : Application() {
    private lateinit var cloudListener: RealtimeCloudListener

    override fun onCreate() {
        super.onCreate()
        AutoBackupSchedulerV2.initializeOnAppStart(this)
        
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
                val database = AppDatabase.getInstance(this)
                val syncRepo = RealtimeSyncRepository(this, database)
                cloudListener = RealtimeCloudListener(this, database, syncRepo)
                cloudListener.registerAuthListener()
            }
        } catch (e: Exception) {
            android.util.Log.e("POSApplication", "Failed to initialize RealtimeCloudListener: ${e.message}")
        }
    }
}
