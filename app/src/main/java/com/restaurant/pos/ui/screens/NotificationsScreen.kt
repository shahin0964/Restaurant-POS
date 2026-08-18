package com.restaurant.pos.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.NotificationEntity
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val allNotifications by viewModel.allNotifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: History, 1: Settings
    var filterUnreadOnly by remember { mutableStateOf(false) }

    val filteredList = remember(allNotifications, filterUnreadOnly) {
        if (filterUnreadOnly) {
            allNotifications.filter { !it.isRead }
        } else {
            allNotifications
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("notifications_back_btn")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Text(
                            text = "Notifications",
                        color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = BrandPrimary,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$unreadCount unread",
                        color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (selectedTab == 0 && allNotifications.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.markAllNotificationsAsRead() },
                            modifier = Modifier.testTag("notifications_mark_all_read_btn")
                        ) {
                            Text(
                                text = "Mark All Read",
                                color = CurrencyGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkBackground,
                    contentColor = CurrencyGold,
                    divider = { HorizontalDivider(color = DarkSurface) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "History (${allNotifications.size})",
                                color = if (selectedTab == 0) CurrencyGold else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Settings",
                                color = if (selectedTab == 1) CurrencyGold else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("notifications_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (selectedTab == 0) {
                // Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !filterUnreadOnly,
                            onClick = { filterUnreadOnly = false },
                            label = { Text("All (${allNotifications.size})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CurrencyGold,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )
                        FilterChip(
                            selected = filterUnreadOnly,
                            onClick = { filterUnreadOnly = true },
                            label = { Text("Unread ($unreadCount)", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CurrencyGold,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )
                    }

                    if (allNotifications.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAllNotifications() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = StatusPending
                            )
                        }
                    }
                }

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = if (filterUnreadOnly) "No unread notifications" else "No notifications yet",
                                color = TextPrimary,
                        fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real events (new orders, stock alerts, payment, order ready) will appear here.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = filteredList,
                            key = { it.id },
                            contentType = { "notification_card" }
                        ) { item ->
                            NotificationCardItem(
                                notification = item,
                                onClick = {
                                    if (!item.isRead) {
                                        viewModel.markNotificationAsRead(item.id)
                                    }
                                },
                                onDelete = {
                                    viewModel.deleteNotification(item.id)
                                }
                            )
                        }
                    }
                }
            } else {
                // Settings Tab
                NotificationSettingsTab(viewModel)
            }
        }
    }
}

@Composable
fun NotificationCardItem(
    notification: NotificationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(notification.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(notification.timestamp))
    }

    val (iconBg, iconText, titleColor) = when (notification.type) {
        "NEW_ORDER" -> Triple(BrandPrimary.copy(alpha = 0.2f), "🛍️", Color.White)
        "LOW_STOCK" -> Triple(CurrencyGold.copy(alpha = 0.2f), "⚠️", CurrencyGold)
        "OUT_OF_STOCK" -> Triple(StatusPending.copy(alpha = 0.2f), "🚫", StatusPending)
        "PAYMENT_CONFIRMED" -> Triple(StatusReady.copy(alpha = 0.2f), "💳", StatusReady)
        "ORDER_READY" -> Triple(StatusReady.copy(alpha = 0.2f), "🍽️", StatusReady)
        else -> Triple(DarkSurfaceVariant, "🔔", Color.White)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) DarkSurfaceVariant else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("notification_item_${notification.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconText, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        color = titleColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateStr,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!notification.isRead) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = CurrencyGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "UNREAD",
                            color = CurrencyGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun NotificationSettingsTab(viewModel: RestaurantViewModel) {
    var notifyNewOrder by remember { mutableStateOf(viewModel.isNotificationCategoryEnabled("notify_new_order")) }
    var notifyLowStock by remember { mutableStateOf(viewModel.isNotificationCategoryEnabled("notify_low_stock")) }
    var notifyOutOfStock by remember { mutableStateOf(viewModel.isNotificationCategoryEnabled("notify_out_of_stock")) }
    var notifyPayment by remember { mutableStateOf(viewModel.isNotificationCategoryEnabled("notify_payment")) }
    var notifyOrderReady by remember { mutableStateOf(viewModel.isNotificationCategoryEnabled("notify_order_ready")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Event Notification Preferences",
                        color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Toggle active alert notifications generated from real system events.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            NotificationToggleCard(
                title = "New Order Alerts",
                subtitle = "Trigger notification when a real new order is created",
                icon = "🛍️",
                checked = notifyNewOrder,
                onCheckedChange = {
                    notifyNewOrder = it
                    viewModel.setNotificationCategoryEnabled("notify_new_order", it)
                }
            )
        }

        item {
            NotificationToggleCard(
                title = "Low Stock Alerts",
                subtitle = "Trigger notification when item reaches low stock threshold",
                icon = "⚠️",
                checked = notifyLowStock,
                onCheckedChange = {
                    notifyLowStock = it
                    viewModel.setNotificationCategoryEnabled("notify_low_stock", it)
                }
            )
        }

        item {
            NotificationToggleCard(
                title = "Out of Stock Alerts",
                subtitle = "Trigger notification when item stock reaches zero",
                icon = "🚫",
                checked = notifyOutOfStock,
                onCheckedChange = {
                    notifyOutOfStock = it
                    viewModel.setNotificationCategoryEnabled("notify_out_of_stock", it)
                }
            )
        }

        item {
            NotificationToggleCard(
                title = "Payment Confirmed Alerts",
                subtitle = "Trigger notification when payment is confirmed for an order",
                icon = "💳",
                checked = notifyPayment,
                onCheckedChange = {
                    notifyPayment = it
                    viewModel.setNotificationCategoryEnabled("notify_payment", it)
                }
            )
        }

        item {
            NotificationToggleCard(
                title = "Order Ready Alerts",
                subtitle = "Trigger notification when order status changes to Ready",
                icon = "🍽️",
                checked = notifyOrderReady,
                onCheckedChange = {
                    notifyOrderReady = it
                    viewModel.setNotificationCategoryEnabled("notify_order_ready", it)
                }
            )
        }
    }
}

@Composable
fun NotificationToggleCard(
    title: String,
    subtitle: String,
    icon: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = CurrencyGold,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
        }
    }
}
