package com.restaurant.pos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.restaurant.pos.data.model.AppPermission
import com.restaurant.pos.data.model.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emailOrPhone: String,
    val name: String,
    val role: String = "Administrator",
    val passwordHash: String = "",
    val firebaseUid: String? = null,
    val isCurrentSession: Boolean = false,
    val isActive: Boolean = true,
    val permissions: String = ""
) {
    fun isAdmin(): Boolean {
        val r = role.lowercase().trim()
        return r == "administrator" || r == "admin"
    }

    fun hasPermission(permission: AppPermission): Boolean {
        // Administrator always has full root access (Admin Protection)
        if (isAdmin()) return true
        if (!isActive) return false

        val assignedKeys = permissions.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (assignedKeys.isNotEmpty()) {
            val targetKey = permission.key.lowercase()
            val targetShortKey = targetKey.removePrefix("permission_")
            return assignedKeys.any { 
                it == targetKey || it == targetShortKey || it.removePrefix("permission_") == targetShortKey 
            }
        }

        // Fallback to role defaults
        val defaultRole = UserRole.fromRoleName(role)
        val defaultKeys = defaultRole.defaultPermissions.map { it.lowercase() }
        val targetKey = permission.key.lowercase()
        val targetShortKey = targetKey.removePrefix("permission_")
        return defaultKeys.any {
            it == targetKey || it == targetShortKey || it.removePrefix("permission_") == targetShortKey
        }
    }

    fun hasPermission(permissionKey: String): Boolean {
        val perm = AppPermission.fromKey(permissionKey)
        return if (perm != null) hasPermission(perm) else isAdmin()
    }

    fun getEffectivePermissions(): Set<String> {
        if (isAdmin()) return AppPermission.allKeys()
        val assignedKeys = permissions.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        if (assignedKeys.isNotEmpty()) {
            return assignedKeys
        }
        return UserRole.fromRoleName(role).defaultPermissions
    }
}


