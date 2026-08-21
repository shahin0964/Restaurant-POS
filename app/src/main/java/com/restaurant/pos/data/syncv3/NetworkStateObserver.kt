package com.restaurant.pos.data.syncv3

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors network availability states (ONLINE / OFFLINE) and triggers SyncQueueWorker
 * immediately upon internet restoration to process any pending offline operations.
 */
class NetworkStateObserver(private val context: Context) {
    private val TAG = "NetworkStateObserver"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(isCurrentlyConnected())
    val isOnline = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.i(TAG, "Network is ONLINE. Signaling pending sync work to SyncQueueWorker.")
            _isOnline.value = true
            
            // Trigger SyncQueueWorker to run as soon as connectivity returns
            SyncQueueWorker.triggerImmediateSync(context)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.i(TAG, "Network is OFFLINE. Operations will be queued locally in Room.")
            _isOnline.value = false
        }
    }

    /**
     * Synchronously checks if there is an active network connection.
     */
    fun isCurrentlyConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Registers network callback to listen to dynamic connectivity changes.
     */
    fun startObserving() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "NetworkStateObserver started listening successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback.", e)
        }
    }

    /**
     * Unregisters network callback to prevent memory leaks.
     */
    fun stopObserving() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "NetworkStateObserver stopped listening successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback.", e)
        }
    }
}
