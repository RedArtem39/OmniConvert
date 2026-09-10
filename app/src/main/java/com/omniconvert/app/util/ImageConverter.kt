package com.omniconvert.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.omniconvert.app.model.PhotoOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageConverter {

    suspend fun convertImage(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        options: PhotoOptions,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f)
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("Cannot open input image stream"))

            // Check EXIF orientation before decoding full bitmap
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                context.contentResolver.openInputStream(inputUri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Exception) {}

            onProgress(0.3f)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                return@withContext Result.failure(Exception("Failed to decode image"))
            }

            // Fix orientation if rotated
            bitmap = rotateBitmapIfNeeded(bitmap, orientation)

            // Resize if requested
            if (options.resizePercent in 1..99) {
                val newWidth = (bitmap.width * (options.resizePercent / 100f)).toInt().coerceAtLeast(1)
                val newHeight = (bitmap.height * (options.resizePercent / 100f)).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            } else if (options.customWidth != null && options.customHeight != null) {
                val scaled = Bitmap.createScaledBitmap(bitmap, options.customWidth, options.customHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            onProgress(0.6f)

            // Compress to target format
            val compressFormat = when (options.format.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        if (options.quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS
                        else Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                else -> Bitmap.CompressFormat.JPEG // jpg / jpeg default
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outStream ->
                val success = bitmap.compress(compressFormat, options.quality.coerceIn(1, 100), outStream)
                if (!success) {
                    return@withContext Result.failure(Exception("Bitmap compression failed"))
                }
            }

            bitmap.recycle()
            onProgress(1.0f)

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
