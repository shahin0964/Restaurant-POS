package com.restaurant.pos.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
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

    var backupToDelete by remember { mutableStateOf<BackupFileInfo?>(null) }

    val isCloudInProgress = cloudState is BackupOpState.Progress
    val isLocalInProgress = backupState is BackupOpState.Progress

    // Automatically handle cloud backup completion / error with toast feedback
    LaunchedEffect(cloudState) {
        when (val state = cloudState) {
            is BackupOpState.Success -> {
                Toast.makeText(context, "${state.title}: ${state.detail}", Toast.LENGTH_LONG).show()
                viewModel.clearCloudOpState()
            }
            is BackupOpState.Error -> {
                Toast.makeText(context, "Cloud Error: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.clearCloudOpState()
            }
            else -> {}
        }
    }

    // Automatically handle local backup success / error
    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupOpState.Success -> {
                Toast.makeText(context, "${state.title}", Toast.LENGTH_SHORT).show()
                viewModel.clearBackupOpState()
            }
            is BackupOpState.Error -> {
                Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.clearBackupOpState()
            }
            else -> {}
        }
    }

    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current

    val createDocumentLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                viewModel.createBackupToUri(uri)
            }
        }
    } else null

    val openDocumentLauncher = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.validateAndPrepareRestore(uri)
            }
        }
    } else null

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "Backup & Restore",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Securely backup and restore your app data",
                        color = TextSecondary,
                        fontSize = 12.sp
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // READ-ONLY WARNING (If unauthorized)
            if (!isAuthorized) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = StatusCancelled)
                            Column {
                                Text(
                                    text = "READ-ONLY ACCESS",
                                    color = StatusCancelled,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
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

            // CARD 1: AES-256 ENCRYPTED OFFLINE BACKUP
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AES-256 Encrypted Offline Backup",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All data including customers, bills, payments, expenses & settings are password-protected and saved locally.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // GRID ROW: CREATE BACKUP & RESTORE BACKUP
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // CREATE BACKUP
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderOutline),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_create_btn")
                            .clickable(enabled = !isLocalInProgress) {
                                if (!isAuthorized) {
                                    Toast.makeText(context, "Unauthorized: Only Managers & Admins can create backups", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                val timestampStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                                val suggestedName = "RestaurantPOS_Backup_$timestampStr.json"
                                createDocumentLauncher?.launch(suggestedName)
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(BrandPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLocalInProgress) {
                                    CircularProgressIndicator(
                                        color = BrandPrimary,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Create Backup",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isLocalInProgress) "Encrypting..." else "Encrypt & export data",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // RESTORE BACKUP
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderOutline),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_restore_btn")
                            .clickable(enabled = !isLocalInProgress) {
                                if (!isAuthorized) {
                                    Toast.makeText(context, "Unauthorized: Only Managers & Admins can restore backups", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                openDocumentLauncher?.launch(arrayOf("application/json", "*/*"))
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(BrandAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = BrandAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Restore Backup",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Decrypt & load file",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // CARD 3: CLOUD AUTO-BACKUP
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_backup_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(BrandPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCloudInProgress) {
                                    CircularProgressIndicator(
                                        color = BrandPrimary,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Auto-Backup",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isCloudInProgress) {
                                        (cloudState as? BackupOpState.Progress)?.message ?: "Syncing with cloud..."
                                    } else {
                                        "Backup and restore your data securely with your account"
                                    },
                                    color = if (isCloudInProgress) BrandPrimary else TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!isAuthorized) {
                                        Toast.makeText(context, "Unauthorized action", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (!isAuthenticated) {
                                        Toast.makeText(context, "Please login to perform cloud backup", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.cloudBackup()
                                },
                                enabled = isAuthorized && !isCloudInProgress,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPrimary,
                                    contentColor = Color.White,
                                    disabledContainerColor = BrandPrimary.copy(alpha = 0.5f),
                                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("cloud_backup_btn")
                            ) {
                                if (isCloudInProgress) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Syncing...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Backup to Cloud",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (!isAuthorized) {
                                        Toast.makeText(context, "Unauthorized action", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    if (!isAuthenticated) {
                                        Toast.makeText(context, "Please login to perform cloud restore", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    showCloudRestoreConfirm = true
                                },
                                enabled = isAuthorized && !isCloudInProgress,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BrandPrimary
                                ),
                                border = BorderStroke(1.dp, BrandPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("cloud_restore_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Restore from Cloud",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = BrandPrimary
                                )
                            }
                        }
                    }
                }
            }

            // SECTION HEADER: LOCAL BACKUP HISTORY
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Local Backup History",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // LOCAL BACKUP HISTORY LIST / EMPTY STATE
            if (recentBackups.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No local backups created yet",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(recentBackups, key = { it.uriString }) { info ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderOutline),
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
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = info.fileName,
                                        color = TextPrimary,
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
                                        color = TextSecondary.copy(alpha = 0.7f),
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

    // CLOUD RESTORE CONFIRMATION DIALOG
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary, contentColor = Color.White)
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

    // RESTORE WARNING DIALOG
    pendingRestoreData?.let { data ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            title = {
                Text("Restore Backup?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Restoring will replace or update existing application data.",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Data to be restored:\n" +
                        "• Menu Items: ${data.menuItems.size}\n" +
                        "• Orders: ${data.orders.size}\n" +
                        "• Categories: ${data.categories.size}\n" +
                        "• Tables: ${data.tables.size}\n" +
                        "• Users/Staff: ${data.users.size}\n" +
                        "• Expenses: ${data.expenses.size}\n" +
                        "• Offers: ${data.offers.size}\n" +
                        "• Notifications: ${data.notifications.size}\n" +
                        "• Staff Food: ${data.staffFood.size}\n" +
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

    // DELETE BACKUP DIALOG
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
                    Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold)
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
