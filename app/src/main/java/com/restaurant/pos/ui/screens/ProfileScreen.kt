package com.restaurant.pos.ui.screens

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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: RestaurantViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState(initial = null)
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingCount by viewModel.pendingSyncCount.collectAsState()
    val lastSyncLong by viewModel.lastSyncTime.collectAsState()
    val isSyncActive by viewModel.isSyncActive.collectAsState()
    val lastSyncError by viewModel.lastSyncError.collectAsState()

    var showChangePasswordDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Profile",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("profile_screen")
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
                // CARD 1: User Account & Details
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                        .testTag("profile_user_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CurrencyGold.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Avatar",
                                    tint = CurrencyGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentUser?.name ?: "Unknown User",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentUser?.emailOrPhone ?: "-",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("profile_email")
                                )
                            }
                        }

                        Text(
                            text = "Change Password",
                            color = CurrencyGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showChangePasswordDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("profile_change_password_link")
                        )
                    }
                }

                // CARD 2: Sync Status
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                        .testTag("profile_sync_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Sync Status",
                                    tint = if (isOnline) {
                                        if (isSyncActive) StatusPending
                                        else if (!lastSyncError.isNullOrBlank()) StatusCancelled
                                        else if (pendingCount > 0) StatusPending
                                        else StatusCompleted
                                    } else {
                                        TextMuted
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sync Status",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Status pill
                            val (statusText, statusColor) = when {
                                !isOnline -> "Offline" to StatusCancelled
                                isSyncActive -> "Syncing..." to StatusPending
                                !lastSyncError.isNullOrBlank() -> "Sync Failed" to StatusCancelled
                                pendingCount > 0 -> "Backup Pending" to StatusPending
                                else -> "Synced" to StatusCompleted
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isOnline && pendingCount == 0 && lastSyncError.isNullOrBlank() && !isSyncActive) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Synced",
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("profile_sync_status_badge")
                                )
                            }
                        }

                        if (isOnline && !lastSyncError.isNullOrBlank()) {
                            HorizontalDivider(color = BorderOutline, thickness = 1.dp)
                            Text(
                                text = lastSyncError ?: "",
                                color = StatusCancelled,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("profile_sync_error_msg")
                            )
                        }
                    }
                }

                // CARD 3: Pending Backup Count
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                        .testTag("profile_pending_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Pending Backup",
                                tint = if (pendingCount > 0) StatusPending else CurrencyGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Pending Backup Count",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pendingCount.toString(),
                                color = if (pendingCount > 0) StatusPending else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("profile_pending_count_value")
                            )
                        }
                    }
                }

                // CARD 4: Last Backup Time
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                        .testTag("profile_last_backup_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Last Backup Time",
                                tint = CurrencyGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Last Backup Time",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val lastSyncValue = lastSyncLong
                        val formattedTime = remember(lastSyncValue) {
                            if (lastSyncValue == null || lastSyncValue == 0L) {
                                "Never"
                            } else {
                                val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                sdf.format(Date(lastSyncValue))
                            }
                        }

                        Text(
                            text = formattedTime,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("profile_last_backup_value")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Logout Button
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_logout_btn")
                ) {
                    Text(
                        text = "Logout",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showChangePasswordDialog) {
        var oldPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmNewPassword by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        var successMessage by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        var oldPasswordVisible by remember { mutableStateOf(false) }
        var newPasswordVisible by remember { mutableStateOf(false) }
        var confirmNewPasswordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isLoading) showChangePasswordDialog = false
            },
            title = {
                Text(
                    text = "Change Password",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_dialog_form"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it; errorMessage = ""; successMessage = "" },
                        label = { Text("Current Password") },
                        visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                                Icon(
                                    imageVector = if (oldPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("old_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CurrencyGold,
                            focusedLabelColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            cursorColor = CurrencyGold
                        ),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMessage = ""; successMessage = "" },
                        label = { Text("New Password") },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CurrencyGold,
                            focusedLabelColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            cursorColor = CurrencyGold
                        ),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it; errorMessage = ""; successMessage = "" },
                        label = { Text("Confirm New Password") },
                        visualTransformation = if (confirmNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmNewPasswordVisible = !confirmNewPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TextMuted)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_new_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CurrencyGold,
                            focusedLabelColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            cursorColor = CurrencyGold
                        ),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            color = StatusCancelled,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("change_password_error")
                        )
                    }

                    if (successMessage.isNotBlank()) {
                        Text(
                            text = successMessage,
                            color = StatusCompleted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("change_password_success")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (oldPassword.isBlank() || newPassword.isBlank() || confirmNewPassword.isBlank()) {
                            errorMessage = "All fields are required."
                            return@Button
                        }
                        if (newPassword.length < 6) {
                            errorMessage = "New password must be at least 6 characters."
                            return@Button
                        }
                        if (newPassword != confirmNewPassword) {
                            errorMessage = "New passwords do not match."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = ""
                        successMessage = ""

                        viewModel.updatePassword(oldPassword, newPassword) { result ->
                            isLoading = false
                            if (result.isSuccess) {
                                successMessage = "Password updated successfully!"
                                oldPassword = ""
                                newPassword = ""
                                confirmNewPassword = ""
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to update password."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold),
                    modifier = Modifier.testTag("change_password_dialog_confirm_btn"),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("change_password_dialog_cancel_btn")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
