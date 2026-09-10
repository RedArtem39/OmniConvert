package com.omniconvert.app.model

import android.net.Uri

enum class MediaType {
    VIDEO,
    PHOTO,
    AUDIO
}

enum class TaskStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MediaItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val mediaType: MediaType = MediaType.VIDEO
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hours = min / 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, min % 60, sec)
            } else {
                String.format("%02d:%02d", min, sec)
            }
        }
}

data class VideoOptions(
    val format: String = "mp4",
    val encoder: String = "h264_mediacodec", // Qualcomm / Android Hardware encoder default
    val resolution: String = "original",     // original, 1080p, 720p, 480p
    val crf: Int = 23,                       // 18 - 35
    val targetSizeMb: Float? = null,         // e.g. 25 MB for Discord/Telegram
    val fps: Int? = null,                    // null = keep original, 30, 60
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val muteAudio: Boolean = false,
    val fastPreset: Boolean = true
)

data class PhotoOptions(
    val format: String = "webp",             // webp, jpg, png, heic, avif
    val quality: Int = 85,                   // 1 - 100
    val resizePercent: Int = 100,            // 25, 50, 75, 100
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    val stripExif: Boolean = true
)

data class AudioOptions(
    val format: String = "mp3",              // mp3, aac, opus, flac, wav
    val bitrateKbps: Int = 192,              // 128, 192, 256, 320
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L
)

data class ConversionTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val item: MediaItem,
    val outputFormat: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val speed: String = "",
    val outputPath: String? = null,
    val outputSizeBytes: Long = 0L,
    val errorMessage: String? = null
)
