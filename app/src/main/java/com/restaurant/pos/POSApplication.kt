package com.restaurant.pos

import android.app.Application
import com.restaurant.pos.data.backupv2.AutoBackupSchedulerV2
import com.restaurant.pos.data.db.AppDatabase
import com.restaurant.pos.data.syncv3.RealtimeCloudListener
import com.restaurant.pos.data.syncv3.RealtimeSyncRepository
import com.restaurant.pos.data.syncv3.NetworkStateObserver
import com.restaurant.pos.data.syncv3.SyncQueueWorker

class POSApplication : Application() {
    private lateinit var cloudListener: RealtimeCloudListener
    private lateinit var networkStateObserver: NetworkStateObserver

    override fun onCreate() {
        super.onCreate()
        AutoBackupSchedulerV2.initializeOnAppStart(this)

        try {
            networkStateObserver = NetworkStateObserver(this)
            networkStateObserver.startObserving()
            SyncQueueWorker.enqueuePeriodicSync(this)
        } catch (e: Exception) {
            android.util.Log.e("POSApplication", "Failed to initialize NetworkStateObserver or enqueue Sync: ${e.message}")
        }
        
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
                try {
                    com.google.firebase.database.FirebaseDatabase.getInstance("https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/")
                        .setPersistenceEnabled(true)
                    android.util.Log.i("POSApplication", "Firebase Realtime Database offline persistence enabled.")
                } catch (e: Exception) {
                    android.util.Log.e("POSApplication", "Failed to setPersistenceEnabled: ${e.message}")
                }
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
