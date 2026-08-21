package com.restaurant.pos.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.restaurant.pos.data.db.SyncRecordDao
import com.restaurant.pos.data.db.SyncRecordEntity
import com.restaurant.pos.data.db.UserDao
import com.restaurant.pos.data.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AuthRepository(
    private val context: Context,
    private val userDao: UserDao,
    private val syncRecordDao: SyncRecordDao
) {

    val currentUser: Flow<UserEntity?> = userDao.getCurrentSessionUser()
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun seedDefaultUserIfNeeded() {
        // No fake / mock accounts are created.
    }

    suspend fun restoreSessionIfNeeded(): UserEntity? {
        val fbUser = try {
            firebaseAuth.currentUser
        } catch (e: Exception) {
            null
        } ?: return null

        // Check if there is already a local user matching this Firebase UID or Email
        var localUser = userDao.getUserByFirebaseUid(fbUser.uid)
            ?: (if (!fbUser.email.isNullOrBlank()) userDao.getUserByEmailOrPhone(fbUser.email!!) else null)

        if (localUser != null) {
            if (!localUser.isCurrentSession) {
                userDao.clearCurrentSessions()
                userDao.setCurrentSession(localUser.id)
            }
            return localUser.copy(isCurrentSession = true)
        }

        // If local user is missing but Firebase session is valid, fetch profile from Firestore with timeout
        return try {
            val firestoreUserDoc = withTimeoutOrNull(3000L) {
                firestore.collection("users").document(fbUser.uid).get().await()
            }
            val remoteRole = firestoreUserDoc?.getString("role") ?: "Administrator"
            val remoteName = firestoreUserDoc?.getString("name") ?: (fbUser.displayName ?: "User")
            val remotePermissions = when (val p = firestoreUserDoc?.get("permissions")) {
                is String -> p
                is List<*> -> p.filterIsInstance<String>().joinToString(",")
                else -> null
            }
            val defaultPerms = if (remoteRole.equals("Administrator", true) || remoteRole.equals("Admin", true)) {
                com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
            } else {
                com.restaurant.pos.data.model.UserRole.fromRoleName(remoteRole).defaultPermissions.joinToString(",")
            }
            val newUser = UserEntity(
                emailOrPhone = fbUser.email ?: "",
                name = remoteName,
                role = remoteRole,
                passwordHash = "",
                firebaseUid = fbUser.uid,
                isCurrentSession = true,
                isActive = true,
                permissions = remotePermissions ?: defaultPerms
            )
            val newId = userDao.insertUser(newUser)
            userDao.clearCurrentSessions()
            userDao.setCurrentSession(newId)
            newUser.copy(id = newId)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to restore user from Firestore on startup: ${e.message}")
            null
        }
    }

    /**
     * Checks if an Administrator already exists in either the remote Firestore
     * or the local database to enforce the Single Administrator constraint.
     */
    private suspend fun hasAnyAdministrator(): Boolean {
        // 1. Check local Room database
        val localAdmin = userDao.getAdministrator()
        if (localAdmin != null) return true

        // 2. Check remote Firestore users collection
        return try {
            val querySnap = firestore.collection("users")
                .whereIn("role", listOf("Administrator", "ADMINISTRATOR", "Admin", "ADMIN"))
                .get()
                .await()
            !querySnap.isEmpty
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to query Firestore for administrator check", e)
            false
        }
    }

    suspend fun login(emailOrPhone: String, password: String): Result<UserEntity> {
        val trimmedEmail = emailOrPhone.trim()
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Authentication failed, user is null")

            // Fetch user record from Firestore if it exists (with timeout protection)
            val firestoreUserDoc = try {
                withTimeoutOrNull(3000L) {
                    firestore.collection("users").document(firebaseUser.uid).get().await()
                }
            } catch (e: Exception) {
                null
            }

            val remoteRole = firestoreUserDoc?.getString("role")
            val remoteName = firestoreUserDoc?.getString("name")
            val remoteIsActive = firestoreUserDoc?.getBoolean("isActive") ?: true
            val remotePermissions = when (val p = firestoreUserDoc?.get("permissions")) {
                is String -> p
                is List<*> -> p.filterIsInstance<String>().joinToString(",")
                else -> null
            }

            if (!remoteIsActive) {
                firebaseAuth.signOut()
                return Result.failure(Exception("This staff account is inactive. Please contact your Administrator."))
            }

            var localUser = userDao.getUserByFirebaseUid(firebaseUser.uid)
                ?: userDao.getUserByEmailOrPhone(firebaseUser.email ?: trimmedEmail)

            if (localUser == null) {
                val assignedRole = if (remoteRole != null) {
                    remoteRole
                } else {
                    "Administrator"
                }
                val defaultPerms = if (assignedRole.equals("Administrator", true) || assignedRole.equals("Admin", true)) {
                    com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
                } else {
                    com.restaurant.pos.data.model.UserRole.fromRoleName(assignedRole).defaultPermissions.joinToString(",")
                }
                localUser = UserEntity(
                    emailOrPhone = firebaseUser.email ?: trimmedEmail,
                    name = remoteName ?: (firebaseUser.displayName ?: "User"),
                    role = assignedRole,
                    passwordHash = "",
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true,
                    permissions = remotePermissions ?: defaultPerms
                )
                val newId = userDao.insertUser(localUser)
                localUser = localUser.copy(id = newId)
            } else {
                if (!localUser.isActive) {
                    firebaseAuth.signOut()
                    return Result.failure(Exception("This staff account is inactive. Please contact your Administrator."))
                }
                val finalRole = remoteRole ?: localUser.role
                val finalName = remoteName ?: (firebaseUser.displayName ?: localUser.name)
                val finalPermissions = remotePermissions ?: if (localUser.permissions.isNotBlank()) {
                    localUser.permissions
                } else {
                    if (finalRole.equals("Administrator", true) || finalRole.equals("Admin", true)) {
                        com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
                    } else {
                        com.restaurant.pos.data.model.UserRole.fromRoleName(finalRole).defaultPermissions.joinToString(",")
                    }
                }
                localUser = localUser.copy(
                    name = finalName,
                    role = finalRole,
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true,
                    permissions = finalPermissions
                )
                userDao.updateUser(localUser)
            }

            // Sync user profile to Firestore (with safe timeout so it never blocks login)
            try {
                val userDocMap = mapOf(
                    "id" to localUser.id,
                    "name" to localUser.name,
                    "emailOrPhone" to localUser.emailOrPhone,
                    "role" to localUser.role,
                    "firebaseUid" to firebaseUser.uid,
                    "isActive" to true,
                    "permissions" to localUser.permissions,
                    "isCurrentSession" to false,
                    "_lastUpdated" to System.currentTimeMillis()
                )
                withTimeoutOrNull(3000L) {
                    firestore.collection("users").document(firebaseUser.uid)
                        .set(userDocMap, SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to sync user document to Firestore on login", e)
            }

            updateSyncRecordWithUid(localUser.id, firebaseUser.uid)
            userDao.clearCurrentSessions()
            userDao.setCurrentSession(localUser.id)

            Result.success(localUser.copy(isCurrentSession = true))
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<UserEntity> {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Google Authentication failed")

            val email = firebaseUser.email ?: throw Exception("Google account must have an email")
            val displayName = firebaseUser.displayName ?: "Google User"

            // Check Firestore doc (with timeout protection)
            val firestoreUserDoc = try {
                withTimeoutOrNull(3000L) {
                    firestore.collection("users").document(firebaseUser.uid).get().await()
                }
            } catch (e: Exception) {
                null
            }

            val remoteRole = firestoreUserDoc?.getString("role")
            val remoteName = firestoreUserDoc?.getString("name")
            val remoteIsActive = firestoreUserDoc?.getBoolean("isActive") ?: true

            if (!remoteIsActive) {
                firebaseAuth.signOut()
                return Result.failure(Exception("This staff account is inactive. Please contact your Administrator."))
            }

            var localUser = userDao.getUserByFirebaseUid(firebaseUser.uid)
                ?: userDao.getUserByEmailOrPhone(email)

            if (localUser == null) {
                val assignedRole = if (remoteRole != null) {
                    remoteRole
                } else {
                    "Administrator"
                }
                localUser = UserEntity(
                    emailOrPhone = email,
                    name = remoteName ?: displayName,
                    role = assignedRole,
                    passwordHash = "",
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true
                )
                val newId = userDao.insertUser(localUser)
                localUser = localUser.copy(id = newId)
            } else {
                if (!localUser.isActive) {
                    firebaseAuth.signOut()
                    return Result.failure(Exception("This staff account is inactive. Please contact your Administrator."))
                }
                val finalRole = remoteRole ?: localUser.role
                val finalName = remoteName ?: (displayName.ifBlank { localUser.name })
                localUser = localUser.copy(
                    name = finalName,
                    role = finalRole,
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true
                )
                userDao.updateUser(localUser)
            }

            // Sync user profile to Firestore (with safe timeout so it never blocks login)
            try {
                val userDocMap = mapOf(
                    "id" to localUser.id,
                    "name" to localUser.name,
                    "emailOrPhone" to localUser.emailOrPhone,
                    "role" to localUser.role,
                    "firebaseUid" to firebaseUser.uid,
                    "isActive" to true,
                    "isCurrentSession" to false,
                    "_lastUpdated" to System.currentTimeMillis()
                )
                withTimeoutOrNull(3000L) {
                    firestore.collection("users").document(firebaseUser.uid)
                        .set(userDocMap, SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to sync Google user document to Firestore", e)
            }

            updateSyncRecordWithUid(localUser.id, firebaseUser.uid)
            userDao.clearCurrentSessions()
            userDao.setCurrentSession(localUser.id)

            Result.success(localUser.copy(isCurrentSession = true))
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    suspend fun register(name: String, emailOrPhone: String, password: String): Result<UserEntity> {
        val trimmedEmail = emailOrPhone.trim()
        val trimmedName = name.trim()
        return try {
            // Determine role before creating: All signups are Administrator by default
            val role = "Administrator"

            val authResult = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Registration failed, user is null")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(trimmedName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            userDao.clearCurrentSessions()
            val existingLocalUser = userDao.getUserByEmailOrPhone(firebaseUser.email ?: trimmedEmail)

            val finalUser = if (existingLocalUser != null) {
                val updated = existingLocalUser.copy(
                    name = trimmedName,
                    passwordHash = "",
                    firebaseUid = firebaseUser.uid,
                    role = if (existingLocalUser.role.equals("Administrator", ignoreCase = true)) "Administrator" else role,
                    isCurrentSession = true,
                    isActive = true
                )
                userDao.updateUser(updated)
                updated
            } else {
                val newUser = UserEntity(
                    emailOrPhone = firebaseUser.email ?: trimmedEmail,
                    name = trimmedName,
                    role = role,
                    passwordHash = "",
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true
                )
                val id = userDao.insertUser(newUser)
                newUser.copy(id = id)
            }

            // Write user profile to Firestore users/{uid}
            try {
                val userDocMap = mapOf(
                    "id" to finalUser.id,
                    "name" to trimmedName,
                    "emailOrPhone" to (firebaseUser.email ?: trimmedEmail),
                    "role" to finalUser.role,
                    "firebaseUid" to firebaseUser.uid,
                    "isActive" to true,
                    "isCurrentSession" to false,
                    "_lastUpdated" to System.currentTimeMillis()
                )
                firestore.collection("users").document(firebaseUser.uid)
                    .set(userDocMap, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to write registered user profile to Firestore", e)
            }

            updateSyncRecordWithUid(finalUser.id, firebaseUser.uid)
            userDao.setCurrentSession(finalUser.id)

            Result.success(finalUser)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Administrator creates or edits Staff accounts with chosen Role & Permissions.
     * New staff accounts are created in Firebase Authentication (via secondary FirebaseApp)
     * and in Firestore users/{uid}.
     */
    suspend fun saveStaffUser(
        id: Long = 0,
        name: String,
        emailOrPhone: String,
        role: String,
        password: String,
        isActive: Boolean,
        permissions: String = "",
        currentUserId: Long?
    ): Result<UserEntity> {
        val trimmedEmail = emailOrPhone.trim()
        val trimmedName = name.trim()
        val chosenRole = if (role.isBlank()) "Staff" else role.trim()

        if (trimmedEmail.isBlank() || trimmedName.isBlank()) {
            return Result.failure(Exception("Name and Email are required."))
        }

        // CREATE NEW STAFF ACCOUNT
        if (id == 0L) {
            if (password.length < 6) {
                return Result.failure(Exception("Password must be at least 6 characters for Firebase Authentication."))
            }

            val existing = userDao.getUserByEmailOrPhone(trimmedEmail)
            if (existing != null) {
                return Result.failure(Exception("An account with this email already exists."))
            }

            val effectivePermissions = if (permissions.isNotBlank()) {
                permissions.trim()
            } else {
                if (chosenRole.equals("Administrator", true) || chosenRole.equals("Admin", true)) {
                    com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
                } else {
                    com.restaurant.pos.data.model.UserRole.fromRoleName(chosenRole).defaultPermissions.joinToString(",")
                }
            }

            // Create Firebase Authentication user via secondary FirebaseApp so current session stays intact
            val tempAppName = "StaffCreator_${System.currentTimeMillis()}"
            val defaultOptions = FirebaseApp.getInstance().options
            val secondaryApp = FirebaseApp.initializeApp(context, defaultOptions, tempAppName)
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

            val createdUid: String
            try {
                val authResult = secondaryAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                val createdUser = authResult.user ?: throw Exception("Failed to create Firebase Auth user for staff.")
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                createdUser.updateProfile(profileUpdates).await()
                createdUid = createdUser.uid
            } catch (e: Exception) {
                return Result.failure(Exception("Firebase Auth creation failed: ${e.message}"))
            } finally {
                try {
                    secondaryAuth.signOut()
                    secondaryApp.delete()
                } catch (ignored: Exception) {}
            }

            // Save Firestore user document under users/{uid}
            try {
                val staffData = mapOf(
                    "name" to trimmedName,
                    "emailOrPhone" to trimmedEmail,
                    "role" to chosenRole,
                    "isActive" to isActive,
                    "permissions" to effectivePermissions,
                    "firebaseUid" to createdUid,
                    "isCurrentSession" to false,
                    "_lastUpdated" to System.currentTimeMillis()
                )
                firestore.collection("users").document(createdUid)
                    .set(staffData, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to write staff profile to Firestore", e)
            }

            // Save in local Room DB
            val newUser = UserEntity(
                emailOrPhone = trimmedEmail,
                name = trimmedName,
                role = chosenRole,
                passwordHash = "",
                firebaseUid = createdUid,
                isCurrentSession = false,
                isActive = isActive,
                permissions = effectivePermissions
            )
            val newLocalId = userDao.insertUser(newUser)
            updateSyncRecordWithUid(newLocalId, createdUid)

            return Result.success(newUser.copy(id = newLocalId))
        } else {
            // EDIT EXISTING USER
            val targetUser = userDao.getUserById(id)
                ?: return Result.failure(Exception("Staff account not found."))

            val isTargetAdmin = targetUser.role.equals("Administrator", ignoreCase = true) ||
                    targetUser.role.equals("Admin", ignoreCase = true)

            // Protected Administrator account rules:
            // 1. Root Administrator role remains Administrator
            // 2. Administrator cannot be deactivated
            val finalRole = if (isTargetAdmin) "Administrator" else chosenRole
            val finalIsActive = if (isTargetAdmin) true else isActive

            if (currentUserId != null && id == currentUserId && !finalIsActive) {
                return Result.failure(Exception("You cannot deactivate your own logged-in account."))
            }

            val finalPermissions = if (isTargetAdmin) {
                com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
            } else if (permissions.isNotBlank()) {
                permissions.trim()
            } else {
                com.restaurant.pos.data.model.UserRole.fromRoleName(finalRole).defaultPermissions.joinToString(",")
            }

            val updatedUser = targetUser.copy(
                name = trimmedName,
                emailOrPhone = trimmedEmail,
                role = finalRole,
                isActive = finalIsActive,
                permissions = finalPermissions
            )
            userDao.updateUser(updatedUser)

            // Update in Firestore
            if (targetUser.firebaseUid != null) {
                try {
                    val updateMap = mutableMapOf<String, Any>(
                        "name" to trimmedName,
                        "role" to finalRole,
                        "isActive" to finalIsActive,
                        "permissions" to finalPermissions,
                        "_lastUpdated" to System.currentTimeMillis()
                    )
                    firestore.collection("users").document(targetUser.firebaseUid)
                        .set(updateMap, SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.e("AuthRepository", "Failed to update user in Firestore", e)
                }
            }

            return Result.success(updatedUser)
        }
    }

    suspend fun deleteStaffUser(user: UserEntity, currentUserId: Long?): Result<Boolean> {
        // Enforce: Administrator can never be deleted
        if (user.role.equals("Administrator", ignoreCase = true) || user.role.equals("Admin", ignoreCase = true)) {
            return Result.failure(Exception("The Administrator account cannot be deleted."))
        }

        // Enforce: Logged-in user cannot delete self
        if (currentUserId != null && user.id == currentUserId) {
            return Result.failure(Exception("You cannot delete your own logged-in account."))
        }

        // Delete from Firestore
        if (user.firebaseUid != null) {
            try {
                firestore.collection("users").document(user.firebaseUid).delete().await()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to delete staff user from Firestore", e)
            }
        }

        // Delete from local DB
        userDao.deleteUser(user)

        // Mark sync record as deleted
        val record = syncRecordDao.getRecordByLocalId("users", user.id)
        if (record != null) {
            syncRecordDao.insertOrUpdate(record.copy(
                isDeleted = true,
                operation = "DELETE",
                pendingSync = true,
                lastSyncTime = System.currentTimeMillis()
            ))
        }

        return Result.success(true)
    }

    suspend fun logout() {
        firebaseAuth.signOut()
        userDao.clearCurrentSessions()
    }

    private suspend fun updateSyncRecordWithUid(localId: Long, uid: String) {
        val record = syncRecordDao.getRecordByLocalId("users", localId)
        if (record != null) {
            syncRecordDao.insertOrUpdate(record.copy(
                firestoreId = uid,
                pendingSync = true,
                operation = "UPDATE",
                lastSyncTime = System.currentTimeMillis()
            ))
        } else {
            syncRecordDao.insertOrUpdate(SyncRecordEntity(
                tableName = "users",
                localId = localId,
                firestoreId = uid,
                lastSyncTime = System.currentTimeMillis(),
                pendingSync = true,
                operation = "INSERT",
                isDeleted = false
            ))
        }
    }

    private fun mapAuthException(e: Exception): Exception {
        val message = e.message ?: ""
        return when {
            e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
            message.contains("credential is incorrect", ignoreCase = true) ||
            message.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            message.contains("wrong-password", ignoreCase = true) -> {
                Exception("Incorrect email or password. Please verify your credentials.")
            }
            e is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
            message.contains("user-not-found", ignoreCase = true) -> {
                Exception("No account found with this email. Please check your email or Sign Up.")
            }
            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
            message.contains("email-already-in-use", ignoreCase = true) -> {
                Exception("An account with this email already exists. Please log in.")
            }
            e is com.google.firebase.auth.FirebaseAuthWeakPasswordException ||
            message.contains("weak-password", ignoreCase = true) -> {
                Exception("Password must be at least 6 characters.")
            }
            e is com.google.firebase.FirebaseNetworkException ||
            message.contains("network", ignoreCase = true) -> {
                Exception("Network connection error. Please check your internet connection.")
            }
            message.contains("badly formatted", ignoreCase = true) ||
            message.contains("invalid-email", ignoreCase = true) -> {
                Exception("Please enter a valid email address (e.g. user@example.com).")
            }
            else -> e
        }
    }

    suspend fun updatePassword(userId: Long, oldPass: String, newPass: String): Result<Boolean> {
        return try {
            val currentAuthUser = firebaseAuth.currentUser ?: throw Exception("No authenticated user.")
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(currentAuthUser.email!!, oldPass)
            currentAuthUser.reauthenticate(credential).await()
            currentAuthUser.updatePassword(newPass).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }
}
