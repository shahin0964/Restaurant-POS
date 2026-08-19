package com.restaurant.pos.ui.screens

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.restaurant.pos.R
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: RestaurantViewModel,
    onNavigateNext: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()

    // Fullscreen edge-to-edge system UI handling (hide status bar & navigation bar)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val activity = context as? Activity
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Exact 1500 ms (1.5 seconds) splash screen duration with session restoration
    LaunchedEffect(Unit) {
        val restoredUser = viewModel.restoreSessionIfNeeded()
        delay(1500)
        val hasFirebaseUser = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            false
        }
        val currentSessionUser = restoredUser ?: viewModel.currentUser.value ?: currentUser
        val isLoggedIn = currentSessionUser != null && hasFirebaseUser
        onNavigateNext(isLoggedIn)
    }

    // Smooth continuous rotation animation for the gold loading ring
    val infiniteTransition = rememberInfiniteTransition(label = "SplashLoadingRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0806))
            .testTag("splash_screen")
    ) {
        // Atmospheric dark restaurant background canvas with warm ambient gold radial lighting
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38230B),
                        Color(0xFF1E1307),
                        Color(0xFF0A0806)
                    ),
                    center = Offset(w / 2f, h * 0.40f),
                    radius = w * 0.95f
                )
            )
        }

        // Center Content Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .offset(y = (-20).dp)
        ) {
            // Gold Cloche + POS Emblem
            RestaurantPosLogoEmblem(size = 190.dp)

            Spacer(modifier = Modifier.height(28.dp))

            // Main Brand Title: "Restaurant" in white serif font
            Text(
                text = "Restaurant",
                color = Color.White,
                fontSize = 42.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Sub Brand Title: "POS" flanked by golden divider rules
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.width(230.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFFFFD700))
                            )
                        )
                )
                Text(
                    text = "POS",
                    color = Color(0xFFFFC107),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFD700), Color.Transparent)
                            )
                        )
                )
            }

            // Tagline: "Smart POS for Smart Restaurants"
            Text(
                text = "Smart POS for Smart Restaurants",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Features line: "Fast • Reliable • Secure"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 18.dp)
            ) {
                Text(stringResource(R.string.splash_slogan_fast), color = Color.White.copy(alpha = 0.85f), fontSize = 13.5.sp, fontWeight = FontWeight.Normal)
                Text("  •  ", color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.splash_slogan_reliable), color = Color.White.copy(alpha = 0.85f), fontSize = 13.5.sp, fontWeight = FontWeight.Normal)
                Text("  •  ", color = Color(0xFFFFC107), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.splash_slogan_secure), color = Color.White.copy(alpha = 0.85f), fontSize = 13.5.sp, fontWeight = FontWeight.Normal)
            }
        }

        // Bottom Animated Gold Loading Ring & Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { rotationZ = rotationAngle }
            ) {
                val strokeWidth = 3.5.dp.toPx()
                // Track ring
                drawCircle(
                    color = Color(0xFFFFC107).copy(alpha = 0.18f),
                    style = Stroke(width = strokeWidth)
                )
                // Gold rotating arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFFC107).copy(alpha = 0.05f),
                            Color(0xFFFFE082),
                            Color(0xFFFFC107),
                            Color(0xFFFF8F00)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Loading...",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun RestaurantPosLogoEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft golden glow behind emblem
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.toPx() / 2f
            val centerY = size.toPx() / 2f
            val radius = size.toPx() * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD447).copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.3f
                )
            )
        }

        // Vector Logo Emblem
        Icon(
            painter = painterResource(id = R.drawable.ic_restaurant_logo),
            contentDescription = "Restaurant POS Emblem",
            tint = Color.Unspecified,
            modifier = Modifier.size(size)
        )
    }
}
