package com.restaurant.pos.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.restaurant.pos.data.backupv2.BackupMetadataV2
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.BackupUiStateV2
import com.restaurant.pos.ui.viewmodel.BackupViewModelV2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreenV2(
    viewModel: BackupViewModelV2 = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val autoBackupIntervalHours by viewModel.autoBackupIntervalHours.collectAsState()
    val lastAutoBackupTimestamp by viewModel.lastAutoBackupTimestamp.collectAsState()
    val lastAutoBackupStatus by viewModel.lastAutoBackupStatus.collectAsState()
    val lastAutoBackupMessage by viewModel.lastAutoBackupMessage.collectAsState()
    val lastAutoBackupFilename by viewModel.lastAutoBackupFilename.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAndValidateFromUri(uri)
        }
    }

    val defaultFilename = remember {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        "pos_backup_v2_${sdf.format(Date())}.json"
    }

    val isProcessing = uiState is BackupUiStateV2.Loading

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("backup_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Backup & Restore",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("backup_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Info Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "POS Backup & Restore V2",
                            color = CurrencyGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Safely export your restaurant data (orders, menu items, categories, staff users, settings, and image assets) or restore from an existing V2 backup JSON file.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Automatic Backup Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Backup",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Scheduled background backup via WorkManager",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setAutoBackupEnabled(enabled, 24)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4CAF50),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkBackground
                                ),
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("auto_backup_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetaRow("Schedule Frequency", "Daily (Every $autoBackupIntervalHours hours)")
                            MetaRow(
                                "Status",
                                when (lastAutoBackupStatus) {
                                    "SUCCESS" -> "Success"
                                    "FAILED" -> "Failed"
                                    else -> "Not Run Yet"
                                }
                            )
                            val lastTimeStr = if (lastAutoBackupTimestamp > 0L) {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                sdf.format(Date(lastAutoBackupTimestamp))
                            } else {
                                "Never"
                            }
                            MetaRow("Last Automatic Backup", lastTimeStr)

                            if (lastAutoBackupStatus == "FAILED" && lastAutoBackupMessage.isNotBlank()) {
                                Text(
                                    text = "Error: $lastAutoBackupMessage",
                                    color = Color(0xFFF44336),
                                    fontSize = 12.sp
                                )
                            } else if (lastAutoBackupFilename.isNotBlank()) {
                                MetaRow("Latest Auto File", lastAutoBackupFilename)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                if (!isProcessing) {
                                    viewModel.triggerManualAutoBackupNow()
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF4CAF50)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("run_auto_backup_now_btn")
                        ) {
                            Text(
                                text = "Run Automatic Backup Now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Action 1: Export / Backup
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CurrencyGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CurrencyGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = null,
                                    tint = CurrencyGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup Data",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Export database and images to a JSON file",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (!isProcessing) {
                                    createDocumentLauncher.launch(defaultFilename)
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CurrencyGold,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("backup_export_btn")
                        ) {
                            Text(
                                text = "Export Backup File",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Action 2: Import / Restore
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Restore Data",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Restore database from a POS_BACKUP_V2 file",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                if (!isProcessing) {
                                    openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF2196F3)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2196F3)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("backup_restore_btn")
                        ) {
                            Text(
                                text = "Select Backup File to Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Processing Loading Overlay
            if (uiState is BackupUiStateV2.Loading) {
                val message = (uiState as BackupUiStateV2.Loading).message
                AlertDialog(
                    onDismissRequest = { /* Non-dismissable */ },
                    confirmButton = {},
                    title = {
                        Text(
                            text = "Please Wait",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = CurrencyGold, modifier = Modifier.size(36.dp))
                            Text(text = message, color = TextSecondary, fontSize = 14.sp)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            // Restore Confirmation Dialog
            if (uiState is BackupUiStateV2.ValidationSuccess) {
                val validationSuccess = uiState as BackupUiStateV2.ValidationSuccess
                val payload = validationSuccess.payload

                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    title = {
                        Text(
                            text = "Confirm Data Restore",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFF44336),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Restoring this backup will replace the current saved POS data. Continue?",
                                        color = Color(0xFFFFCDD2),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                text = "Validated Backup Metadata:",
                                color = CurrencyGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MetaRow("Format Identifier", BackupMetadataV2.FORMAT_IDENTIFIER)
                                MetaRow("Format Version", "v${payload.metadata.formatVersion}")
                                MetaRow("Created Date", payload.metadata.createdAtFormatted)
                                MetaRow("DB Schema Version", "v${payload.metadata.dbVersion}")
                                MetaRow("App Version", payload.metadata.appVersion)
                                if (payload.metadata.deviceModel.isNotBlank()) {
                                    MetaRow("Device Model", payload.metadata.deviceModel)
                                }
                            }

                            HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))

                            Text(
                                text = "Payload Contents:",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MetaRow("Orders", "${payload.databaseData.orders.size}")
                                MetaRow("Order Items", "${payload.databaseData.orderItems.size}")
                                MetaRow("Products / Menu Items", "${payload.databaseData.menuItems.size}")
                                MetaRow("Categories", "${payload.databaseData.categories.size}")
                                MetaRow("Users / Staff", "${payload.databaseData.users.size}")
                                MetaRow("Expenses", "${payload.databaseData.expenses.size}")
                                MetaRow("Local Assets / Images", "${payload.assets.size}")
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmRestore(payload) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier.testTag("restore_confirm_btn")
                        ) {
                            Text("Restore", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.testTag("restore_cancel_btn")
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = DarkSurface,
                    modifier = Modifier.testTag("restore_confirm_dialog")
                )
            }

            // Export Success Dialog
            if (uiState is BackupUiStateV2.ExportSuccess) {
                val success = uiState as BackupUiStateV2.ExportSuccess
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text("Backup Created Successfully", color = TextPrimary, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(text = success.summary, color = TextSecondary, fontSize = 14.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetState() },
                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                        ) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            // Export Error Dialog
            if (uiState is BackupUiStateV2.ExportError) {
                val error = uiState as BackupUiStateV2.ExportError
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text("Backup Export Failed", color = TextPrimary, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(text = error.message, color = TextSecondary, fontSize = 14.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("OK", color = Color.White)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            // Validation Error Dialog
            if (uiState is BackupUiStateV2.ValidationError) {
                val error = uiState as BackupUiStateV2.ValidationError
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text("Invalid Backup File", color = TextPrimary, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(
                            text = "The selected file cannot be restored: ${error.message}",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("OK", color = Color.White)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            // Restore Success Dialog
            if (uiState is BackupUiStateV2.RestoreSuccess) {
                val success = uiState as BackupUiStateV2.RestoreSuccess
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text("Backup Restored Successfully", color = TextPrimary, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(text = success.summary, color = TextSecondary, fontSize = 14.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetState() },
                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                        ) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            // Restore Error Dialog
            if (uiState is BackupUiStateV2.RestoreError) {
                val error = uiState as BackupUiStateV2.RestoreError
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text("Restore Failed", color = TextPrimary, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(text = error.message, color = TextSecondary, fontSize = 14.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resetState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("OK", color = Color.White)
                        }
                    },
                    containerColor = DarkSurface
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
