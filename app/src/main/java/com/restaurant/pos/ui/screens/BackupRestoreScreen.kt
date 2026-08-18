package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.backup.BackupFileInfo
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.BackupOpState
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val backupState by viewModel.backupOperationState.collectAsState()
    val cloudState by viewModel.cloudOperationState.collectAsState()
    val pendingRestoreData by viewModel.pendingRestoreData.collectAsState()
    val recentBackups by viewModel.recentBackups.collectAsState()

    var showCloudRestoreConfirm by remember { mutableStateOf(false) }

    val isAuthorized = currentUser == null || currentUser?.role in listOf("Administrator", "Manager")
    val isAuthenticated = currentUser != null

    // Delete confirmation dialog state
    var backupToDelete by remember { mutableStateOf<BackupFileInfo?>(null) }

    // Safe ActivityResultRegistryOwner check
    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current

    // File launcher for creating backup
    val createDocumentLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                viewModel.createBackupToUri(uri)
            }
        }
    } else {
        null
    }

    // File launcher for selecting restore file
    val openDocumentLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.validateAndPrepareRestore(uri)
            }
        }
    } else {
        null
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onBack() },
                        modifier = Modifier.testTag("backup_restore_back_btn")
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
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("backup_restore_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Read-only notice if unauthorized
            if (!isAuthorized) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = StatusCancelled)
                            Column {
                                Text(
                                    text = "READ-ONLY ACCESS",
                                    color = StatusCancelled,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Only Administrators & Managers can create or restore backups.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // SECTION: CLOUD BACKUP & RESTORE
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StatusReady.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = StatusReady,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "CLOUD BACKUP & RESTORE",
                        color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Securely sync your POS data to the cloud",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (!isAuthenticated) {
                            Text(
                                text = "Please login to enable Cloud Backup and Restore features.",
                                color = StatusCancelled,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Cloud Backup ensures all your local data is synchronized with Firestore. Cloud Restore pulls all your business data from the cloud to this device.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.cloudBackup() },
                                    enabled = isAuthorized,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusReady,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(46.dp).testTag("cloud_backup_btn")
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("BACKUP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showCloudRestoreConfirm = true },
                                    enabled = isAuthorized,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, if (isAuthorized) StatusReady else TextMuted),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(46.dp).testTag("cloud_restore_btn")
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, tint = StatusReady, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RESTORE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 1: CREATE BACKUP
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CurrencyGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = CurrencyGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "CREATE BACKUP",
                        color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Export all real POS data to a structured, versioned file",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Text(
                            text = "Backs up orders, menu items, inventory, expenses, categories, offers, business & receipt settings, and printer configuration.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = {
                                if (!isAuthorized) {
                                    Toast.makeText(context, "Unauthorized: Only Managers & Admins can create backups", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val timestampStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                                val suggestedName = "RestaurantPOS_Backup_$timestampStr.json"
                                createDocumentLauncher?.launch(suggestedName)
                            },
                            enabled = isAuthorized,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CurrencyGold,
                                contentColor = Color.Black,
                                disabledContainerColor = DarkSurfaceVariant,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("backup_create_btn")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CREATE BACKUP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // SECTION 2: RESTORE BACKUP
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StatusPreparing.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = null,
                                    tint = StatusPreparing,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "RESTORE BACKUP",
                        color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Restore application data from a valid backup file",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Text(
                            text = "Select a valid Restaurant POS backup JSON file from storage. The file will be thoroughly validated before restoring.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        OutlinedButton(
                            onClick = {
                                if (!isAuthorized) {
                                    Toast.makeText(context, "Unauthorized: Only Managers & Admins can restore backups", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                openDocumentLauncher?.launch(arrayOf("application/json", "*/*"))
                            },
                            enabled = isAuthorized,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, if (isAuthorized) StatusPreparing else TextMuted),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("backup_restore_btn")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = StatusPreparing, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RESTORE BACKUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }

            // SECTION 3: BACKUP HISTORY / RECENT BACKUPS
            item {
                Text(
                    text = "BACKUP HISTORY",
                    color = CurrencyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (recentBackups.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                                Text("No recent backups recorded", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                items(recentBackups, key = { it.uriString }) { info ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = CurrencyGold, modifier = Modifier.size(22.dp))
                                Column {
                                    Text(
                                        text = info.fileName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${info.createdAtFormatted} • ${info.sizeFormatted}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = info.recordSummary,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { backupToDelete = info },
                                enabled = isAuthorized
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = StatusCancelled,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // CLOUD OPERATION PROGRESS DIALOG
    when (val state = cloudState) {
        is BackupOpState.Progress -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = StatusReady,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text("Cloud Sync", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(state.message, color = TextSecondary, fontSize = 13.sp)
                },
                containerColor = DarkSurface
            )
        }

        is BackupOpState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearCloudOpState() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusReady)
                        Text(state.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(state.detail, color = TextSecondary, fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearCloudOpState() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusReady, contentColor = Color.White)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = DarkSurface
            )
        }

        is BackupOpState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearCloudOpState() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = StatusCancelled)
                        Text("Cloud Error", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(state.message, color = TextSecondary, fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearCloudOpState() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                    ) {
                        Text("CLOSE", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = DarkSurface
            )
        }

        BackupOpState.Idle -> {}
    }

    // CLOUD RESTORE CONFIRMATION
    if (showCloudRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirm = false },
            title = {
                Text("Cloud Restore?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "This will download all your data from the cloud and merge it with your local database. Existing records will be updated if the cloud version is newer.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudRestoreConfirm = false
                        viewModel.cloudRestore()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusReady, contentColor = Color.White)
                ) {
                    Text("START RESTORE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudRestoreConfirm = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // PROGRESS DIALOG
    when (val state = backupState) {
        is BackupOpState.Progress -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CurrencyGold,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text("Please Wait", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(state.message, color = TextSecondary, fontSize = 13.sp)
                },
                containerColor = DarkSurface
            )
        }

        is BackupOpState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearBackupOpState() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusReady)
                        Text(state.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(state.detail, color = TextSecondary, fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearBackupOpState() },
                        colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = DarkSurface
            )
        }

        is BackupOpState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearBackupOpState() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = StatusCancelled)
                        Text("Error", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(state.message, color = TextSecondary, fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearBackupOpState() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                    ) {
                        Text("CLOSE", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = DarkSurface
            )
        }

        BackupOpState.Idle -> {}
    }

    // RESTORE WARNING DIALOG (Requirement 9 & 10)
    pendingRestoreData?.let { data ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            title = {
                Text("Restore Backup?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restoring will replace or update existing application data.", color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Data to be restored:\n" +
                        "• Menu Items: ${data.menuItems.size}\n" +
                        "• Orders: ${data.orders.size}\n" +
                        "• Categories: ${data.categories.size}\n" +
                        "• Expenses: ${data.expenses.size}\n" +
                        "• Offers: ${data.offers.size}\n" +
                        "• Settings: Included",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmRestore() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled, contentColor = Color.White)
                ) {
                    Text("CONTINUE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingRestore() }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // DELETE BACKUP DIALOG (Requirement 20)
    backupToDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = {
                Text("Delete this backup file?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Are you sure you want to remove '${info.fileName}' from history?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeRecentBackup(info)
                        backupToDelete = null
                        Toast.makeText(context, "Backup removed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("DELETE", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
