package com.restaurant.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.restaurant.pos.R
import com.restaurant.pos.ui.theme.*

import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

enum class BottomNavScreen(val route: String, val labelResId: Int, val icon: ImageVector) {
    HOME("dashboard", R.string.nav_home, Icons.Default.Home),
    ORDERS("order_list", R.string.nav_orders, Icons.AutoMirrored.Filled.ReceiptLong),
    NEW_ORDER("new_order", R.string.nav_new_order, Icons.Default.Add),
    KITCHEN("kitchen", R.string.nav_kitchen, Icons.Default.Restaurant),
    MORE("more", R.string.nav_more, Icons.Default.Menu)
}

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .border(width = 1.dp, color = BorderOutline)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavScreen.entries.forEach { screen ->
                val isSelected = currentRoute == screen.route

                if (screen == BottomNavScreen.NEW_ORDER) {
                    // Central FAB Plus Button (Red + Orange Gradient)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-6).dp)
                                .size(48.dp)
                                .shadow(6.dp, CircleShape, spotColor = BrandPrimary)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(BrandPrimary, BrandAccent)
                                    )
                                )
                                .clickable { onNavigate(screen.route) }
                                .testTag("nav_new_order_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Order",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BrandPrimary else Color.Transparent)
                                .clickable { onNavigate(screen.route) }
                                .padding(vertical = 6.dp, horizontal = 12.dp)
                                .testTag("nav_${screen.route}_btn")
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = stringResource(screen.labelResId),
                                tint = if (isSelected) Color.White else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(screen.labelResId),
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
