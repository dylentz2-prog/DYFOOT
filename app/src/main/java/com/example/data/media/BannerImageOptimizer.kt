package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class BannerImageSelection(
    val sourceUri: Uri,
    val displayName: String,
    val originalMimeType: String,
    val formatLabel: String,
    val originalSizeBytes: Long,
    val optimizedBytes: ByteArray,
    val optimizedSizeBytes: Long,
    val optimizedMimeType: String,
    val optimizedExtension: String,
    val width: Int,
    val height: Int
)

object BannerImageOptimizer {
    const val MAX_BANNER_FILE_BYTES: Long = 5L * 1024L * 1024L
    const val MAX_TARGET_WIDTH: Int = 1440
    const val MAX_TARGET_HEIGHT: Int = 810
    private const val TARGET_FAST_LOAD_BYTES: Int = 900 * 1024

    private val allowedMimeTypes = mapOf(
        "image/jpeg" to "JPEG",
        "image/jpg" to "JPG",
        "image/png" to "PNG",
        "image/webp" to "WEBP"
    )

    private val allowedExtensions = mapOf(
        "jpg" to "JPG",
        "jpeg" to "JPEG",
        "png" to "PNG",
        "webp" to "WEBP"
    )

    fun resolveAllowedImageFormat(mimeType: String?, fileName: String?): String? {
        val cleanMime = mimeType?.trim()?.lowercase()?.substringBefore(';').orEmpty()
        allowedMimeTypes[cleanMime]?.let { return it }

        val ext = fileName?.trim()?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return allowedExtensions[ext]
    }

    fun detectImageFormatFromHeader(bytes: ByteArray): String? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "JPEG"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "PNG"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "WEBP"
        }
        return null
    }

    fun validateFileSize(sizeBytes: Long): String? {
        if (sizeBytes <= 0L) {
            return "Selected image file is empty or unreadable."
        }
        if (sizeBytes > MAX_BANNER_FILE_BYTES) {
            return "Image is too large (${formatReadableSize(sizeBytes)}). Maximum allowed size is 5 MB."
        }
        return null
    }

    fun computeTargetDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int = MAX_TARGET_WIDTH,
        maxHeight: Int = MAX_TARGET_HEIGHT
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return 0 to 0
        if (srcWidth <= maxWidth && srcHeight <= maxHeight) {
            return srcWidth to srcHeight
        }
        val widthRatio = maxWidth.toFloat() / srcWidth.toFloat()
        val heightRatio = maxHeight.toFloat() / srcHeight.toFloat()
        val scale = minOf(widthRatio, heightRatio)
        val outW = max(1, (srcWidth * scale).roundToInt())
        val outH = max(1, (srcHeight * scale).roundToInt())
        return outW to outH
    }

    fun computeInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    fun sanitizeBannerDestination(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.success("")
        if (trimmed.length > 1000) {
            return Result.failure(IllegalArgumentException("Destination URL must be 1000 characters or fewer."))
        }
        val lower = trimmed.lowercase()
        val valid = lower.startsWith("dyfoot://") ||
            lower.startsWith("https://") ||
            lower.startsWith("http://")
        return if (valid) {
            Result.success(trimmed)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Destination must start with dyfoot://, https://, or http:// (or leave blank)."
                )
            )
        }
    }

    fun formatReadableSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "${kb.roundToInt()} KB"
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.2f MB", mb)
    }

    suspend fun inspectAndOptimize(context: Context, uri: Uri): Result<BannerImageSelection> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)
                var displayName = "banner_image"
                var queriedSize = -1L

                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: displayName
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            queriedSize = cursor.getLong(sizeIdx)
                        }
                    }
                }

                if (queriedSize > MAX_BANNER_FILE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException(validateFileSize(queriedSize) ?: "Image exceeds 5 MB limit.")
                    )
                }

                val rawBytes = resolver.openInputStream(uri)?.use { input ->
                    readBytesCapped(input, MAX_BANNER_FILE_BYTES)
                } ?: return@withContext Result.failure(
                    IllegalStateException("Unable to read the selected image file.")
                )

                val actualSize = rawBytes.size.toLong()
                validateFileSize(actualSize)?.let { msg ->
                    return@withContext Result.failure(IllegalArgumentException(msg))
                }

                val inferredMime = mimeType ?: MimeTypeMap.getFileExtensionFromUrl(displayName)
                    ?.lowercase()
                    ?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }

                val formatLabel = detectImageFormatFromHeader(rawBytes)
                    ?: resolveAllowedImageFormat(inferredMime, displayName)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException(
                            "Unsupported image format. Please choose a JPG, JPEG, PNG, or WEBP image."
                        )
                    )

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("The selected file is not a valid readable image.")
                    )
                }

                val sampleSize = computeInSampleSize(
                    srcWidth = boundsOpts.outWidth,
                    srcHeight = boundsOpts.outHeight,
                    reqWidth = MAX_TARGET_WIDTH,
                    reqHeight = MAX_TARGET_HEIGHT
                )
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                var bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Could not decode the selected image.")
                    )

                bitmap = applyExifOrientation(bitmap, rawBytes)

                val (targetW, targetH) = computeTargetDimensions(bitmap.width, bitmap.height)
                if (targetW > 0 && targetH > 0 && (bitmap.width != targetW || bitmap.height != targetH)) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    if (scaled !== bitmap) {
                        bitmap.recycle()
                        bitmap = scaled
                    }
                }

                val finalWidth = bitmap.width
                val finalHeight = bitmap.height
                val optimizedBytes = compressOptimalWebp(bitmap)
                bitmap.recycle()

                if (optimizedBytes.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Image optimization failed."))
                }
                if (optimizedBytes.size.toLong() > MAX_BANNER_FILE_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Optimized image still exceeds 5 MB. Please choose a simpler image.")
                    )
                }

                Result.success(
                    BannerImageSelection(
                        sourceUri = uri,
                        displayName = displayName,
                        originalMimeType = inferredMime ?: "image/${formatLabel.lowercase()}",
                        formatLabel = formatLabel,
                        originalSizeBytes = actualSize,
                        optimizedBytes = optimizedBytes,
                        optimizedSizeBytes = optimizedBytes.size.toLong(),
                        optimizedMimeType = "image/webp",
                        optimizedExtension = "webp",
                        width = finalWidth,
                        height = finalHeight
                    )
                )
            } catch (e: OutOfMemoryError) {
                Result.failure(IllegalStateException("Image is too large to process in memory. Choose a smaller file."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Image exceeds the 5 MB maximum file size limit.")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun applyExifOrientation(bitmap: Bitmap, rawBytes: ByteArray): Bitmap {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(rawBytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    @Suppress("DEPRECATION")
    private fun compressOptimalWebp(bitmap: Bitmap): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        var quality = 85
        var bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.toByteArray()
        }
        while (bytes.size > TARGET_FAST_LOAD_BYTES && quality > 65) {
            quality -= 8
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(format, quality, out)
                out.toByteArray()
            }
        }
        return bytes
    }
}
