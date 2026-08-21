package com.restaurant.pos.data.backupv2

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.restaurant.pos.data.db.CategoryEntity
import com.restaurant.pos.data.db.MenuItemEntity
import com.restaurant.pos.data.db.ReceiptSettingEntity
import java.io.File

/**
 * Handles encoding, exporting, and restoring required local file assets (images, logos).
 */
object BackupAssetManagerV2 {

    fun exportAssets(
        context: Context,
        categories: List<CategoryEntity>,
        menuItems: List<MenuItemEntity>,
        receiptSetting: ReceiptSettingEntity?
    ): List<BackupAssetV2> {
        val assets = mutableListOf<BackupAssetV2>()
        val processedPaths = mutableSetOf<String>()

        fun processFilePathOrUri(rawPath: String?, defaultSubDir: String) {
            if (rawPath.isNullOrBlank()) return
            val trimmed = rawPath.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("http://") || trimmed.startsWith("https://")) return
            if (processedPaths.contains(trimmed)) return
            processedPaths.add(trimmed)

            try {
                val file = if (trimmed.startsWith("file://")) {
                    File(Uri.parse(trimmed).path ?: "")
                } else if (trimmed.startsWith("/")) {
                    File(trimmed)
                } else {
                    File(context.filesDir, trimmed)
                }

                if (file.exists() && file.isFile && file.length() > 0) {
                    val relativePath = if (file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                        file.absolutePath.removePrefix(context.filesDir.absolutePath).removePrefix("/")
                    } else {
                        "$defaultSubDir/${file.name}"
                    }

                    val bytes = file.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = getMimeType(file.name)

                    assets.add(
                        BackupAssetV2(
                            relativePath = relativePath,
                            mimeType = mime,
                            base64Data = base64,
                            sizeBytes = bytes.size.toLong()
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 1. Process category images
        categories.forEach { processFilePathOrUri(it.imageUrl, "category_images") }

        // 2. Process menu item images
        menuItems.forEach { processFilePathOrUri(it.imageUrl, "item_images") }

        // 3. Process receipt logo
        if (receiptSetting != null) {
            processFilePathOrUri(receiptSetting.logoUri, "receipt_logos")
        }

        // 4. Scan subdirectories in filesDir for any extra images
        val imageDirs = listOf("item_images", "category_images", "receipt_logos")
        for (dirName in imageDirs) {
            val dir = File(context.filesDir, dirName)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0 && !processedPaths.contains(file.absolutePath)) {
                        processedPaths.add(file.absolutePath)
                        try {
                            val relativePath = "$dirName/${file.name}"
                            val bytes = file.readBytes()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            assets.add(
                                BackupAssetV2(
                                    relativePath = relativePath,
                                    mimeType = getMimeType(file.name),
                                    base64Data = base64,
                                    sizeBytes = bytes.size.toLong()
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        return assets
    }

    /**
     * Decodes and restores local asset files to context.filesDir.
     * Returns a map of (relativePath -> restoredAbsolutePath).
     */
    fun restoreAssets(context: Context, assets: List<BackupAssetV2>): Map<String, String> {
        val restoredPathMap = mutableMapOf<String, String>()

        for (asset in assets) {
            try {
                if (asset.base64Data.isBlank()) continue
                val targetFile = File(context.filesDir, asset.relativePath)
                targetFile.parentFile?.mkdirs()

                val bytes = Base64.decode(asset.base64Data, Base64.DEFAULT)
                targetFile.writeBytes(bytes)

                restoredPathMap[asset.relativePath] = targetFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return restoredPathMap
    }

    private fun getMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
