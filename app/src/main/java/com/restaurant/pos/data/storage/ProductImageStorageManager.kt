package com.restaurant.pos.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Manages uploading, retrieving, and removing Product/MenuItem images in Firebase Storage.
 *
 * Path convention:
 * restaurant_pos/products/{productId}/image.jpg
 */
object ProductImageStorageManager {

    private const val TAG = "ProductImageStorage"
    private const val STORAGE_PATH_PREFIX = "restaurant_pos/products"
    private const val STORAGE_BUCKET_URL = "gs://restaurant-pos-99d57.firebasestorage.app"

    private val storage: FirebaseStorage
        get() = try {
            FirebaseStorage.getInstance(STORAGE_BUCKET_URL)
        } catch (e: Exception) {
            FirebaseStorage.getInstance()
        }

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    /**
     * Uploads a product image from a local Uri directly to Firebase Storage.
     * Compresses/downscales the bitmap to a reasonable max dimension (e.g., 1024px)
     * to keep upload fast, bandwidth low, and display crisp.
     *
     * Returns Result.success(downloadUrl) with the permanent HTTPS Firebase Storage URL.
     */
    suspend fun uploadProductImage(
        productId: String,
        imageUri: Uri,
        context: Context
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            Log.d(TAG, "Starting product image upload for product ID: $productId (User: ${user?.email})")

            val compressedBytes = prepareImageBytes(context, imageUri)
                ?: return@withContext Result.failure(Exception("Unable to read or process selected image file."))

            val sanitizedId = productId.ifBlank { System.currentTimeMillis().toString() }
            val storageRef = storage.reference
                .child(STORAGE_PATH_PREFIX)
                .child("product_$sanitizedId")
                .child("image.jpg")

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("productId", sanitizedId)
                .setCustomMetadata("uploadedAt", System.currentTimeMillis().toString())
                .build()

            // Upload bytes to Firebase Storage
            storageRef.putBytes(compressedBytes, metadata).await()

            // Retrieve the public HTTPS download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "Product image uploaded successfully. Download URL: $downloadUrl")

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Product image upload failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Safely deletes a product image from Firebase Storage if it exists.
     */
    suspend fun deleteProductImage(productId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sanitizedId = productId.ifBlank { return@withContext Result.success(Unit) }
            val storageRef = storage.reference
                .child(STORAGE_PATH_PREFIX)
                .child("product_$sanitizedId")
                .child("image.jpg")

            storageRef.delete().await()
            Log.d(TAG, "Product image deleted for product ID: $sanitizedId")
            Result.success(Unit)
        } catch (e: Exception) {
            // Non-fatal if image did not exist
            Log.d(TAG, "Delete product image notice: ${e.message}")
            Result.success(Unit)
        }
    }

    /**
     * Prepares and optimizes image bytes from Uri for cloud storage upload.
     */
    private fun prepareImageBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver

            // 1. Decode bounds to determine sampling
            var inputStream = resolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val maxDimension = 1024
            var sampleSize = 1
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth > maxDimension || origHeight > maxDimension) {
                val halfWidth = origWidth / 2
                val halfHeight = origHeight / 2
                while ((halfWidth / sampleSize) >= maxDimension && (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            // 2. Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            inputStream = resolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap == null) {
                // Fallback: direct stream copy if bitmap decoder returns null
                resolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                var finalBitmap = bitmap
                try {
                    val exifStream = resolver.openInputStream(uri)
                    if (exifStream != null) {
                        val exif = ExifInterface(exifStream)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        val matrix = Matrix()
                        when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                        }
                        if (!matrix.isIdentity) {
                            finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        exifStream.close()
                    }
                } catch (_: Exception) {
                    // Ignore EXIF parsing errors and use decoded bitmap
                }

                val baos = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                if (finalBitmap != bitmap) {
                    bitmap.recycle()
                }
                baos.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare image bytes: ${e.message}", e)
            null
        }
    }
}
