package com.restaurant.pos.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.restaurant.pos.data.db.SyncRecordDao
import com.restaurant.pos.data.db.SyncRecordEntity
import com.restaurant.pos.data.db.UserDao
import com.restaurant.pos.data.db.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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

    private val authScope = CoroutineScope(Dispatchers.IO)

    suspend fun seedDefaultUserIfNeeded() {
        // No fake / mock accounts are created.
    }

    suspend fun restoreSessionIfNeeded(): UserEntity? {
        val fbUser = try {
            firebaseAuth.currentUser
        } catch (e: Exception) {
            null
        } ?: return null

        val defaultPerms = com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")

        // Check if there is already a local user matching this Firebase UID or Email
        val localUser = userDao.getUserByFirebaseUid(fbUser.uid)
            ?: (if (!fbUser.email.isNullOrBlank()) userDao.getUserByEmailOrPhone(fbUser.email!!) else null)

        if (localUser != null) {
            val updatedUser = localUser.copy(
                role = "Administrator",
                isActive = true,
                permissions = defaultPerms,
                isCurrentSession = true,
                firebaseUid = fbUser.uid
            )
            userDao.updateUser(updatedUser)
            if (!localUser.isCurrentSession) {
                userDao.clearCurrentSessions()
                userDao.setCurrentSession(localUser.id)
            }
            updateSyncRecordWithUid(updatedUser.id, fbUser.uid)
            return updatedUser
        }

        // If local user is missing but Firebase session is valid, construct local user immediately and sync in background
        val newUser = UserEntity(
            emailOrPhone = fbUser.email ?: "",
            name = fbUser.displayName ?: "User",
            role = "Administrator",
            passwordHash = "",
            firebaseUid = fbUser.uid,
            isCurrentSession = true,
            isActive = true,
            permissions = defaultPerms
        )
        val newId = userDao.insertUser(newUser)
        userDao.clearCurrentSessions()
        userDao.setCurrentSession(newId)
        val savedUser = newUser.copy(id = newId)
        updateSyncRecordWithUid(savedUser.id, fbUser.uid)

        return savedUser
    }

    /**
     * Checks if an Administrator already exists in either the remote Firestore
     * or the local database to enforce the Single Administrator constraint.
     */
    private suspend fun hasAnyAdministrator(): Boolean {
        // Check local Room database
        val localAdmin = userDao.getAdministrator()
        return localAdmin != null
    }

    suspend fun login(emailOrPhone: String, password: String): Result<UserEntity> {
        val trimmedEmail = emailOrPhone.trim()
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Authentication failed, user is null")

            val defaultPerms = com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
            var localUser = userDao.getUserByFirebaseUid(firebaseUser.uid)
                ?: userDao.getUserByEmailOrPhone(firebaseUser.email ?: trimmedEmail)

            if (localUser == null) {
                localUser = UserEntity(
                    emailOrPhone = firebaseUser.email ?: trimmedEmail,
                    name = firebaseUser.displayName ?: "User",
                    role = "Administrator",
                    passwordHash = "",
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true,
                    permissions = defaultPerms
                )
                val newId = userDao.insertUser(localUser)
                localUser = localUser.copy(id = newId)
            } else {
                localUser = localUser.copy(
                    role = "Administrator",
                    isActive = true,
                    permissions = defaultPerms,
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true
                )
                userDao.updateUser(localUser)
            }

            userDao.clearCurrentSessions()
            userDao.setCurrentSession(localUser.id)
            updateSyncRecordWithUid(localUser.id, firebaseUser.uid)

            val sessionUser = localUser.copy(isCurrentSession = true)

            Result.success(sessionUser)
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

            val defaultPerms = com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")
            var localUser = userDao.getUserByFirebaseUid(firebaseUser.uid)
                ?: userDao.getUserByEmailOrPhone(email)

            if (localUser == null) {
                localUser = UserEntity(
                    emailOrPhone = email,
                    name = displayName,
                    role = "Administrator",
                    passwordHash = "",
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true,
                    isActive = true,
                    permissions = defaultPerms
                )
                val newId = userDao.insertUser(localUser)
                localUser = localUser.copy(id = newId)
            } else {
                localUser = localUser.copy(
                    name = displayName.ifBlank { localUser.name },
                    role = "Administrator",
                    isActive = true,
                    permissions = defaultPerms,
                    firebaseUid = firebaseUser.uid,
                    isCurrentSession = true
                )
                userDao.updateUser(localUser)
            }

            userDao.clearCurrentSessions()
            userDao.setCurrentSession(localUser.id)
            updateSyncRecordWithUid(localUser.id, firebaseUser.uid)

            val sessionUser = localUser.copy(isCurrentSession = true)

            Result.success(sessionUser)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    suspend fun register(name: String, emailOrPhone: String, password: String): Result<UserEntity> {
        val trimmedEmail = emailOrPhone.trim()
        val trimmedName = name.trim()
        return try {
            val role = "Administrator"
            val defaultPerms = com.restaurant.pos.data.model.AppPermission.allKeys().joinToString(",")

            val authResult = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Registration failed, user is null")

            // Create/update user profile in Firebase Realtime Database under users/{userId} node
            val rtdb = FirebaseDatabase.getInstance("https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/")
            val ref = rtdb.getReference("users/${firebaseUser.uid}")
            val rtdbUserMap = mapOf(
                "uid" to firebaseUser.uid,
                "email" to (firebaseUser.email ?: trimmedEmail),
                "name" to trimmedName,
                "role" to "Administrator",
                "createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP
            )
            ref.setValue(rtdbUserMap).await()

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
                    role = "Administrator",
                    isCurrentSession = true,
                    isActive = true,
                    permissions = defaultPerms
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
                    isActive = true,
                    permissions = defaultPerms
                )
                val id = userDao.insertUser(newUser)
                newUser.copy(id = id)
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

                // Write/save each sub-account under users/{newUid} with role, email, name, createdBy, and createdAt
                val rtdb = FirebaseDatabase.getInstance("https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/")
                val ref = rtdb.getReference("users/$createdUid")
                val adminUid = firebaseAuth.currentUser?.uid ?: ""
                val rtdbUserMap = mapOf(
                    "uid" to createdUid,
                    "email" to trimmedEmail,
                    "name" to trimmedName,
                    "role" to chosenRole.lowercase().trim(),
                    "createdBy" to adminUid,
                    "createdAt" to System.currentTimeMillis()
                )
                ref.setValue(rtdbUserMap).await()

            } catch (e: Exception) {
                return Result.failure(Exception("Firebase Auth creation failed: ${e.message}"))
            } finally {
                try {
                    secondaryAuth.signOut()
                    secondaryApp.delete()
                } catch (ignored: Exception) {}
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

            // Update user profile in Firebase Realtime Database under users/{userId} node
            val targetUid = targetUser.firebaseUid
            if (!targetUid.isNullOrBlank()) {
                try {
                    val rtdb = FirebaseDatabase.getInstance("https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    val updateMap = mapOf(
                        "name" to trimmedName,
                        "email" to trimmedEmail,
                        "role" to finalRole.lowercase().trim(),
                        "isActive" to finalIsActive
                    )
                    rtdb.getReference("users/$targetUid").updateChildren(updateMap).await()
                } catch (ignored: Exception) {}
            }

            return Result.success(updatedUser)
        }
    }

    suspend fun deleteStaffUser(user: UserEntity, currentUserId: Long?): Result<Boolean> {
        // Enforce: root Administrator can never be deleted
        if (user.role.equals("Administrator", ignoreCase = true)) {
            return Result.failure(Exception("The root Administrator account cannot be deleted."))
        }

        // Enforce: Logged-in user cannot delete self
        if (currentUserId != null && user.id == currentUserId) {
            return Result.failure(Exception("You cannot delete your own logged-in account."))
        }

        // Delete from Realtime Database under users/{userId} node
        val targetUid = user.firebaseUid
        if (!targetUid.isNullOrBlank()) {
            try {
                val rtdb = FirebaseDatabase.getInstance("https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/")
                rtdb.getReference("users/$targetUid").removeValue().await()
            } catch (ignored: Exception) {}
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
