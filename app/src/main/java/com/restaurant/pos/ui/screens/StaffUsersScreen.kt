package com.restaurant.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.restaurant.pos.data.db.UserEntity
import com.restaurant.pos.data.model.AppPermission
import com.restaurant.pos.data.model.PermissionCategory
import com.restaurant.pos.data.model.UserRole
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel

@Composable
fun StaffUsersScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val isCurrentUserAdmin = currentUser?.isAdmin() == true
    val canViewUsers = isCurrentUserAdmin || (currentUser?.hasPermission(AppPermission.USERS_VIEW) == true)
    val canAddUsers = isCurrentUserAdmin || (currentUser?.hasPermission(AppPermission.USERS_ADD) == true)
    val canEditUsers = isCurrentUserAdmin || (currentUser?.hasPermission(AppPermission.USERS_EDIT) == true)
    val canDeleteUsers = isCurrentUserAdmin || (currentUser?.hasPermission(AppPermission.USERS_DELETE) == true)
    val canManageRoles = isCurrentUserAdmin || (currentUser?.hasPermission(AppPermission.USERS_ROLES) == true)
    val canManagePermissions = isCurrentUserAdmin || (currentUser?.hasPermission(AppPermission.USERS_PERMISSIONS) == true)

    var showAddEditDialog by remember { mutableStateOf(false) }
    var staffToEdit by remember { mutableStateOf<UserEntity?>(null) }
    var staffToDelete by remember { mutableStateOf<UserEntity?>(null) }
    var staffToViewPermissions by remember { mutableStateOf<UserEntity?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("staff_users_back_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Staff & User Management",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Role-Based Access Control (RBAC)",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        floatingActionButton = {
            if (canAddUsers) {
                FloatingActionButton(
                    onClick = {
                        staffToEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = CurrencyGold,
                    contentColor = Color.Black,
                    modifier = Modifier.testTag("add_staff_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Account")
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "more", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("staff_users_screen")
    ) { innerPadding ->
        if (!canViewUsers && currentUser != null) {
            // Access Restricted View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Access Restricted",
                            tint = StatusCancelled,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Access Restricted",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "You do not have permission to view Staff & User Management. Please contact your restaurant Administrator.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                        ) {
                            Text("GO BACK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Stats / Summary Bar
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE ROLES & PERMISSIONS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${allUsers.size} Total Accounts registered",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CurrencyGold.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${AppPermission.entries.size} Permissions",
                                color = CurrencyGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (allUsers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No staff accounts found",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = allUsers,
                            key = { it.id },
                            contentType = { "staff_card" }
                        ) { staff ->
                            val isSelf = currentUser?.id == staff.id
                            val isStaffAdmin = staff.isAdmin()

                            StaffUserCardRow(
                                staff = staff,
                                isSelf = isSelf,
                                isAdmin = isStaffAdmin,
                                canEdit = canEditUsers,
                                canDelete = canDeleteUsers && !isStaffAdmin && !isSelf,
                                onViewPermissions = {
                                    staffToViewPermissions = staff
                                },
                                onEdit = {
                                    if (canEditUsers) {
                                        staffToEdit = staff
                                        showAddEditDialog = true
                                    } else {
                                        warningMessage = "You do not have permission to edit user accounts."
                                    }
                                },
                                onDelete = {
                                    if (isStaffAdmin) {
                                        warningMessage = "The Administrator account cannot be deleted (Admin Protection)."
                                    } else if (isSelf) {
                                        warningMessage = "You cannot delete your own logged-in account."
                                    } else if (canDeleteUsers) {
                                        staffToDelete = staff
                                    } else {
                                        warningMessage = "You do not have permission to delete user accounts."
                                    }
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Account Dialog
    if (showAddEditDialog) {
        AddEditStaffDialog(
            staff = staffToEdit,
            isSelf = currentUser?.id == staffToEdit?.id,
            canManageRoles = canManageRoles,
            canManagePermissions = canManagePermissions,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, email, role, password, isActive, permissions, onError ->
                viewModel.saveStaffUser(
                    id = staffToEdit?.id ?: 0L,
                    name = name,
                    emailOrPhone = email,
                    role = role,
                    password = password,
                    isActive = isActive,
                    permissions = permissions,
                    onResult = { result ->
                        result.onSuccess {
                            showAddEditDialog = false
                        }.onFailure { ex ->
                            onError(ex.message ?: "Failed to save account.")
                        }
                    }
                )
            }
        )
    }

    // View Permissions Dialog
    if (staffToViewPermissions != null) {
        ViewPermissionsDialog(
            staff = staffToViewPermissions!!,
            onDismiss = { staffToViewPermissions = null }
        )
    }

    // Delete Confirmation Dialog
    if (staffToDelete != null) {
        val targetStaff = staffToDelete!!
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = {
                Text(
                    text = "Delete Staff Account?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${targetStaff.name}' (${targetStaff.emailOrPhone})? This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStaffUser(targetStaff) { result ->
                            result.onFailure { ex ->
                                warningMessage = ex.message
                            }
                        }
                        staffToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Warning / Protection Dialog
    if (warningMessage != null) {
        AlertDialog(
            onDismissRequest = { warningMessage = null },
            title = {
                Text(
                    text = "Notice",
                    color = StatusCancelled,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = warningMessage!!,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { warningMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun StaffUserCardRow(
    staff: UserEntity,
    isSelf: Boolean,
    isAdmin: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onViewPermissions: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val userRoleObj = UserRole.fromRoleName(staff.role)
    val effectivePerms = staff.getEffectivePermissions()
    val totalPermsCount = AppPermission.entries.size
    val activePermsCount = if (isAdmin) totalPermsCount else effectivePerms.size

    val roleColor = when (userRoleObj) {
        UserRole.ADMIN -> CurrencyGold
        UserRole.MANAGER -> Color(0xFF42A5F5)
        UserRole.CASHIER -> Color(0xFF66BB6A)
        UserRole.STAFF -> Color(0xFFFFB74D)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isAdmin) CurrencyGold.copy(alpha = 0.5f) else BorderOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_card_${staff.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role Icon Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(roleColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userRoleObj.icon,
                        fontSize = 22.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // User Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = staff.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelf) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CurrencyGold.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "YOU",
                                    color = CurrencyGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = staff.emailOrPhone,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Edit & Delete Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canEdit) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("edit_staff_${staff.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Account",
                                tint = CurrencyGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_staff_${staff.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Account",
                                tint = StatusCancelled,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderOutline.copy(alpha = 0.5f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Role and Permissions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Role Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(roleColor.copy(alpha = 0.15f))
                            .border(0.8.dp, roleColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = staff.role.uppercase(),
                            color = roleColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Active / Inactive Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (staff.isActive) StatusReady.copy(alpha = 0.15f) else StatusCancelled.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (staff.isActive) "Active" else "Inactive",
                            color = if (staff.isActive) StatusReady else StatusCancelled,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Permissions View Button / Chip
                Surface(
                    onClick = onViewPermissions,
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(0.8.dp, BorderOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Permissions",
                            tint = if (isAdmin) CurrencyGold else TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAdmin) "Full Access" else "$activePermsCount/$totalPermsCount Perms",
                            color = if (isAdmin) CurrencyGold else TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditStaffDialog(
    staff: UserEntity?,
    isSelf: Boolean,
    canManageRoles: Boolean,
    canManagePermissions: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        email: String,
        role: String,
        password: String,
        isActive: Boolean,
        permissions: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    val isTargetAdmin = staff?.isAdmin() == true

    var name by remember { mutableStateOf(staff?.name ?: "") }
    var emailOrPhone by remember { mutableStateOf(staff?.emailOrPhone ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Selected Role
    var selectedRole by remember {
        mutableStateOf(
            if (staff != null) {
                UserRole.fromRoleName(staff.role)
            } else {
                UserRole.STAFF
            }
        )
    }

    // Role Dropdown expanded state
    var roleDropdownExpanded by remember { mutableStateOf(false) }

    // Selected Permissions State (Set of permission keys)
    var selectedPermissions by remember {
        mutableStateOf(
            if (staff != null) {
                staff.getEffectivePermissions().toSet()
            } else {
                UserRole.STAFF.defaultPermissions
            }
        )
    }

    var isActive by remember { mutableStateOf(if (isTargetAdmin) true else (staff?.isActive ?: true)) }
    var errorMessage by remember { mutableStateOf("") }

    // Category Expand/Collapse states for accordion
    var expandedCategories by remember {
        mutableStateOf(PermissionCategory.entries.associateWith { true })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, BorderOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (staff == null) "Create Staff / User Account" else if (isTargetAdmin) "Edit Administrator" else "Edit Staff / User Account",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Configure user credentials, role and granular permissions",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)

                if (errorMessage.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = StatusCancelled, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = errorMessage, color = StatusCancelled, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: Basic Information
                    Text(
                        text = "1. ACCOUNT CREDENTIALS",
                        color = CurrencyGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    // Full Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = "" },
                        label = { Text("Full Name *", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Email / Login
                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = { emailOrPhone = it; errorMessage = "" },
                        label = { Text("Email (Firebase Auth Login) *", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = CurrencyGold,
                            unfocusedBorderColor = BorderOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        enabled = staff == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password (for new accounts)
                    if (staff == null) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = "" },
                            label = { Text("Password (min 6 characters) *", color = TextMuted) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle visibility",
                                        tint = TextMuted
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = CurrencyGold,
                                unfocusedBorderColor = BorderOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // SECTION 2: Role Selection
                    Text(
                        text = "2. ROLE SELECTION",
                        color = CurrencyGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    if (isTargetAdmin) {
                        // Protected Root Administrator notice
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CurrencyGold.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CurrencyGold.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👑", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Administrator (Root Account)",
                                        color = CurrencyGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Root administrator maintains full immutable access to all application features.",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Role Selection Cards / Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            UserRole.entries.forEach { roleOption ->
                                val isSelected = selectedRole == roleOption
                                val roleBorderColor = if (isSelected) CurrencyGold else BorderOutline

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) DarkSurfaceVariant else DarkSurface
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, roleBorderColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = canManageRoles) {
                                            selectedRole = roleOption
                                            // Apply defaults for this role
                                            selectedPermissions = roleOption.defaultPermissions
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = if (canManageRoles) {
                                                {
                                                    selectedRole = roleOption
                                                    selectedPermissions = roleOption.defaultPermissions
                                                }
                                            } else null,
                                            colors = RadioButtonDefaults.colors(selectedColor = CurrencyGold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = roleOption.icon, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = roleOption.displayName,
                                                color = if (isSelected) CurrencyGold else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = roleOption.description,
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Account Active Switch
                    if (!isTargetAdmin) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Account Status (Active / Inactive)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (isActive) "User can log in & perform authorized POS actions" else "User login is blocked",
                                        color = if (isActive) StatusReady else StatusCancelled,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isActive,
                                    onCheckedChange = {
                                        if (isSelf && !it) {
                                            errorMessage = "You cannot deactivate your own logged-in account."
                                        } else {
                                            isActive = it
                                            errorMessage = ""
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = CurrencyGold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // SECTION 3: Permission Management
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "3. ROLE & CUSTOM PERMISSIONS",
                                color = CurrencyGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${selectedPermissions.size} of ${AppPermission.entries.size} Permissions Enabled",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        // Presets & Quick Actions
                        if (!isTargetAdmin && canManagePermissions) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        selectedPermissions = AppPermission.allKeys()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("All", color = CurrencyGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        selectedPermissions = emptySet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Category-wise Permission Accordions
                    PermissionCategory.entries.forEach { category ->
                        val categoryPermissions = AppPermission.entries.filter { it.category == category }
                        val enabledInCategory = categoryPermissions.count { selectedPermissions.contains(it.key) }
                        val isAllCategoryEnabled = categoryPermissions.isNotEmpty() && enabledInCategory == categoryPermissions.size
                        val isExpanded = expandedCategories[category] ?: true

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Category Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCategories = expandedCategories.toMutableMap().apply {
                                                put(category, !isExpanded)
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(text = category.icon, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = category.title,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = category.description,
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Category Status Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isAllCategoryEnabled) StatusReady.copy(alpha = 0.15f)
                                                    else if (enabledInCategory > 0) CurrencyGold.copy(alpha = 0.15f)
                                                    else DarkSurface
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$enabledInCategory/${categoryPermissions.size}",
                                                color = if (isAllCategoryEnabled) StatusReady
                                                else if (enabledInCategory > 0) CurrencyGold
                                                else TextMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Quick Category Toggle Button
                                        if (!isTargetAdmin && canManagePermissions) {
                                            IconButton(
                                                onClick = {
                                                    val keys = categoryPermissions.map { it.key }
                                                    selectedPermissions = if (isAllCategoryEnabled) {
                                                        selectedPermissions - keys.toSet()
                                                    } else {
                                                        selectedPermissions + keys
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isAllCategoryEnabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = "Toggle category",
                                                    tint = if (isAllCategoryEnabled) StatusReady else TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Expand/Collapse",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Category Items
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = BorderOutline.copy(alpha = 0.4f), thickness = 0.8.dp)

                                        categoryPermissions.forEach { permission ->
                                            val isPermChecked = isTargetAdmin || selectedPermissions.contains(permission.key)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable(enabled = !isTargetAdmin && canManagePermissions) {
                                                        selectedPermissions = if (isPermChecked) {
                                                            selectedPermissions - permission.key
                                                        } else {
                                                            selectedPermissions + permission.key
                                                        }
                                                    }
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = permission.title,
                                                        color = if (isPermChecked) TextPrimary else TextMuted,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isPermChecked) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = permission.description,
                                                        color = TextSecondary.copy(alpha = 0.8f),
                                                        fontSize = 10.sp
                                                    )
                                                }

                                                Checkbox(
                                                    checked = isPermChecked,
                                                    onCheckedChange = if (!isTargetAdmin && canManagePermissions) {
                                                        { checked ->
                                                            selectedPermissions = if (checked) {
                                                                selectedPermissions + permission.key
                                                            } else {
                                                                selectedPermissions - permission.key
                                                            }
                                                        }
                                                    } else null,
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = CurrencyGold,
                                                        checkmarkColor = Color.Black,
                                                        uncheckedColor = BorderOutline
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || emailOrPhone.isBlank()) {
                                errorMessage = "Full Name and Email are required."
                            } else if (staff == null && password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                            } else {
                                val permsString = if (isTargetAdmin) {
                                    AppPermission.allKeys().joinToString(",")
                                } else {
                                    selectedPermissions.joinToString(",")
                                }
                                onSave(
                                    name.trim(),
                                    emailOrPhone.trim(),
                                    if (isTargetAdmin) "Administrator" else selectedRole.roleName,
                                    password,
                                    isActive,
                                    permsString
                                ) { err ->
                                    errorMessage = err
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black)
                    ) {
                        Text(
                            text = if (staff == null) "CREATE ACCOUNT" else "SAVE CHANGES",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ViewPermissionsDialog(
    staff: UserEntity,
    onDismiss: () -> Unit
) {
    val isAdmin = staff.isAdmin()
    val effectivePerms = staff.getEffectivePermissions()
    val totalPermsCount = AppPermission.entries.size
    val activePermsCount = if (isAdmin) totalPermsCount else effectivePerms.size
    val roleObj = UserRole.fromRoleName(staff.role)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, BorderOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CurrencyGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = roleObj.icon, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = staff.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${staff.role} • $activePermsCount/$totalPermsCount Permissions Granted",
                                color = CurrencyGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)

                // Permissions breakdown
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PermissionCategory.entries.forEach { category ->
                        val perms = AppPermission.entries.filter { it.category == category }
                        val grantedCount = perms.count { isAdmin || effectivePerms.contains(it.key) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, BorderOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = category.icon, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = category.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "$grantedCount/${perms.size}",
                                        color = if (grantedCount == perms.size) StatusReady else CurrencyGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                perms.forEach { perm ->
                                    val isGranted = isAdmin || effectivePerms.contains(perm.key)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = perm.title,
                                            color = if (isGranted) TextPrimary else TextMuted,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (isGranted) "✓ Granted" else "✗ Restricted",
                                            color = if (isGranted) StatusReady else StatusCancelled,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderOutline)

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
