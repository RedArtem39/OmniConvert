package com.omniconvert.app.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.omniconvert.app.model.MediaItem
import com.omniconvert.app.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    suspend fun getMediaItemFromUri(context: Context, uri: Uri, mediaType: MediaType): MediaItem = withContext(Dispatchers.IO) {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) name = cursor.getString(nameIdx)
                if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
            }
        }

        var durationMs = 0L
        var width = 0
        var height = 0
        val mimeType = context.contentResolver.getType(uri) ?: ""

        if (mediaType == MediaType.VIDEO || mediaType == MediaType.AUDIO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (!durStr.isNullOrEmpty()) {
                    durationMs = durStr.toLongOrNull() ?: 0L
                }
                if (mediaType == MediaType.VIDEO) {
                    val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    width = wStr?.toIntOrNull() ?: 0
                    height = hStr?.toIntOrNull() ?: 0
                }
                retriever.release()
            } catch (_: Exception) {}
        }

        MediaItem(
            uri = uri,
            name = name,
            sizeBytes = size,
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            mediaType = mediaType
        )
    }

    suspend fun copyUriToTempFile(context: Context, uri: Uri, prefix: String, suffix: String): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile(prefix, suffix, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    suspend fun saveToGalleryOrDownloads(
        context: Context,
        file: File,
        mediaType: MediaType,
        format: String
    ): Uri? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.SIZE, file.length())

            val mime = when (format.lowercase()) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "gif" -> "image/gif"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp3" -> "audio/mpeg"
                "aac", "m4a" -> "audio/aac"
                "opus" -> "audio/opus"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                else -> "*/*"
            }
            put(MediaStore.MediaColumns.MIME_TYPE, mime)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relPath = when (mediaType) {
                    MediaType.VIDEO -> Environment.DIRECTORY_MOVIES + "/OmniConvert"
                    MediaType.PHOTO -> Environment.DIRECTORY_PICTURES + "/OmniConvert"
                    MediaType.AUDIO -> Environment.DIRECTORY_MUSIC + "/OmniConvert"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = when (mediaType) {
            MediaType.VIDEO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            MediaType.PHOTO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            MediaType.AUDIO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        }

        val itemUri = contentResolver.insert(collection, values) ?: return@withContext null

        try {
            contentResolver.openOutputStream(itemUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(itemUri, values, null, null)
            }

            itemUri
        } catch (e: Exception) {
            contentResolver.delete(itemUri, null, null)
            null
        }
    }
}
