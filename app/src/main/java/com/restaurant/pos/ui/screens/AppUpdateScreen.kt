package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.repository.UpdateInfo
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    viewModel: RestaurantViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateInfo by viewModel.updateInfo.collectAsState()
    
    // Status text and checking state
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Automatically check for update on enter
        isChecking = true
        try {
            viewModel.checkForGitHubUpdates()
        } catch (e: Exception) {
            // Safe fallback
        } finally {
            isChecking = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(
                        text = "APP UPDATE",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("app_update_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Large Icon
            Text(
                text = "🔄",
                fontSize = 80.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "GitHub Auto Update",
                        color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Version info cards
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Version",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = com.restaurant.pos.BuildConfig.VERSION_NAME,
                            color = TextPrimary,
                        fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("app_update_current_version")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderOutline)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Latest Version",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (updateInfo != null) updateInfo!!.latestVersion else "-",
                            color = if (updateInfo != null && updateInfo!!.hasUpdate) CurrencyGold else TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("app_update_latest_version")
                        )
                    }
                }
            }

            // Status message
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (updateInfo != null && updateInfo!!.hasUpdate) {
                        BrandPrimary.copy(alpha = 0.15f)
                    } else {
                        DarkSurface
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            color = CurrencyGold,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Checking for updates...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    } else {
                        val statusText = when {
                            updateInfo == null -> "Not checked yet"
                            updateInfo!!.hasUpdate -> "⬆ New update available"
                            else -> "✓ Up to date"
                        }
                        val statusColor = when {
                            updateInfo == null -> TextSecondary
                            updateInfo!!.hasUpdate -> CurrencyGold
                            else -> StatusReady
                        }
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("app_update_status")
                        )
                    }
                }
            }

            // Actions
            if (updateInfo != null && updateInfo!!.hasUpdate) {
                Button(
                    onClick = {
                        val downloadUrl = updateInfo?.downloadUrl ?: ""
                        if (downloadUrl.isNotEmpty()) {
                            val downloadId = viewModel.updateRepo.startApkDownload(downloadUrl, updateInfo!!.latestVersion)
                            if (downloadId != -1L) {
                                Toast.makeText(context, "Downloading update v${updateInfo!!.latestVersion}... It will open automatically when finished.", Toast.LENGTH_LONG).show()
                            } else {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Opening browser download...", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Update download failed.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Unable to check for updates.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("app_update_action_button")
                ) {
                    Text(
                        text = "UPDATE NOW",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isChecking = true
                            try {
                                viewModel.checkForGitHubUpdates()
                                val latest = viewModel.updateInfo.value
                                if (latest != null) {
                                    if (latest.hasUpdate) {
                                        Toast.makeText(context, "New update available!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "You are using the latest version.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Unable to check for updates.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to check for updates.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isChecking = false
                            }
                        }
                    },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("app_update_action_button")
                ) {
                    Text(
                        text = if (isChecking) "Checking..." else "CHECK FOR UPDATE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (updateInfo != null && updateInfo!!.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Release Notes:",
                        color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = updateInfo!!.releaseNotes,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
