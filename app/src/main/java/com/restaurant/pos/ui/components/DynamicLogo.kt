package com.restaurant.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.R
import com.restaurant.pos.ui.theme.*

@Composable
fun DynamicLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color(0xFFFFD447).copy(alpha = 0.35f), spotColor = Color(0xFFE5A900))
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C1A17),
                        Color(0xFF0D0C0A),
                        Color(0xFF050505)
                    )
                )
            )
            .border(1.5.dp, Color(0xFFFFD447).copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_restaurant_logo),
            contentDescription = "Restaurant POS Logo",
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 0.82f)
        )
    }
}

@Composable
fun DynamicLogoHeader(
    modifier: Modifier = Modifier,
    showSubtext: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        DynamicLogoBadge(size = 90.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "RESTAURANT",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "— POS —",
            color = CurrencyGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
        if (showSubtext) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Smart POS for Smart Restaurants",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
