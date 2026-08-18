package com.restaurant.pos.data.repository

import android.content.Context
import com.restaurant.pos.data.db.NotificationDao
import com.restaurant.pos.data.db.NotificationEntity
import kotlinx.coroutines.flow.Flow

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)

    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadCount: Flow<Int> = notificationDao.getUnreadCount()

    fun isCategoryEnabled(categoryKey: String): Boolean {
        return prefs.getBoolean(categoryKey, true)
    }

    fun setCategoryEnabled(categoryKey: String, enabled: Boolean) {
        prefs.edit().putBoolean(categoryKey, enabled).apply()
    }

    suspend fun emitNotification(
        type: String,
        title: String,
        message: String,
        targetId: String? = null
    ) {
        val categoryKey = when (type) {
            "NEW_ORDER" -> "notify_new_order"
            "LOW_STOCK" -> "notify_low_stock"
            "OUT_OF_STOCK" -> "notify_out_of_stock"
            "PAYMENT_CONFIRMED" -> "notify_payment"
            "ORDER_READY" -> "notify_order_ready"
            else -> "notify_general"
        }

        if (!isCategoryEnabled(categoryKey)) {
            return
        }

        if (targetId != null && notificationDao.existsByTarget(type, targetId)) {
            return
        }

        val entity = NotificationEntity(
            type = type,
            title = title,
            message = message,
            targetId = targetId,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        notificationDao.insertNotification(entity)
    }

    suspend fun markAsRead(id: Long) {
        notificationDao.markAsRead(id)
    }

    suspend fun markAllAsRead() {
        notificationDao.markAllAsRead()
    }

    suspend fun deleteNotification(id: Long) {
        notificationDao.deleteNotification(id)
    }

    suspend fun clearAll() {
        notificationDao.clearAllNotifications()
    }
}
