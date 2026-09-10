package com.omniconvert.app.util

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.omniconvert.app.model.AudioOptions
import com.omniconvert.app.model.VideoOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object FFmpegManager {

    suspend fun convertVideo(
        inputPath: String,
        outputPath: String,
        durationMs: Long,
        options: VideoOptions,
        onProgress: (Float, String) -> Unit
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val cmdList = mutableListOf<String>()

        cmdList.add("-y")

        // Trimming start
        if (options.trimStartMs > 0) {
            cmdList.add("-ss")
            cmdList.add(String.format(java.util.Locale.US, "%.3f", options.trimStartMs / 1000.0))
        }

        cmdList.add("-i")
        cmdList.add(inputPath)

        // Trimming end / duration
        if (options.trimEndMs > options.trimStartMs) {
            val trimDurationSec = (options.trimEndMs - options.trimStartMs) / 1000.0
            cmdList.add("-t")
            cmdList.add(String.format(java.util.Locale.US, "%.3f", trimDurationSec))
        }

        // Calculate effective duration for progress calculation
        val effectiveDurationMs = if (options.trimEndMs > options.trimStartMs) {
            options.trimEndMs - options.trimStartMs
        } else {
            durationMs
        }

        val videoFilters = mutableListOf<String>()

        // Resolution scaling
        when (options.resolution) {
            "1080p" -> videoFilters.add("scale='min(1920,iw)':-2")
            "720p" -> videoFilters.add("scale='min(1280,iw)':-2")
            "480p" -> videoFilters.add("scale='min(854,iw)':-2")
            "360p" -> videoFilters.add("scale='min(640,iw)':-2")
        }

        // FPS filter
        if (options.fps != null && options.fps > 0) {
            videoFilters.add("fps=${options.fps}")
        }

        if (videoFilters.isNotEmpty()) {
            cmdList.add("-vf")
            cmdList.add(videoFilters.joinToString(","))
        }

        // GIF export
        if (options.format.lowercase() == "gif") {
            cmdList.add("-vf")
            cmdList.add("fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse")
            cmdList.add(outputPath)
        } else {
            // Video codec
            val codec = options.encoder
            cmdList.add("-c:v")
            cmdList.add(codec)

            // Bitrate or CRF
            if (options.targetSizeMb != null && options.targetSizeMb > 0 && effectiveDurationMs > 0) {
                // Target file size bitrate calculation
                val targetBits = (options.targetSizeMb * 8 * 1024 * 1024 * 0.95).toLong()
                val durationSec = (effectiveDurationMs / 1000.0).coerceAtLeast(1.0)
                val totalBitrate = (targetBits / durationSec).toLong()
                val audioBitrate = 128_000L
                val videoBitrateKbps = ((totalBitrate - audioBitrate) / 1000).coerceAtLeast(100).toInt()

                cmdList.add("-b:v")
                cmdList.add("${videoBitrateKbps}k")
                cmdList.add("-maxrate")
                cmdList.add("${(videoBitrateKbps * 1.2).toInt()}k")
                cmdList.add("-bufsize")
                cmdList.add("${videoBitrateKbps * 2}k")
            } else {
                if (codec.contains("mediacodec")) {
                    // Hardware encoder uses bitrate
                    cmdList.add("-b:v")
                    cmdList.add("4000k")
                } else {
                    // Software encoder (libx264, libx265)
                    cmdList.add("-crf")
                    cmdList.add(options.crf.toString())
                    cmdList.add("-preset")
                    cmdList.add(if (options.fastPreset) "fast" else "medium")
                }
            }

            // Audio codec
            if (options.muteAudio) {
                cmdList.add("-an")
            } else {
                cmdList.add("-c:a")
                cmdList.add("aac")
                cmdList.add("-b:a")
                cmdList.add("160k")
            }

            cmdList.add(outputPath)
        }

        val session = FFmpegKit.executeWithArgumentsAsync(
            cmdList.toTypedArray(),
            { completeSession ->
                val returnCode = completeSession.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    continuation.resume(Result.success(File(outputPath)))
                } else if (ReturnCode.isCancel(returnCode)) {
                    continuation.resume(Result.failure(Exception("Conversion cancelled by user")))
                } else {
                    val logs = completeSession.allLogsAsString
                    continuation.resume(Result.failure(Exception("FFmpeg error: $logs")))
                }
            },
            { /* logs */ },
            { statistics ->
                if (effectiveDurationMs > 0 && statistics != null) {
                    val time = statistics.time.toFloat()
                    val progress = (time / effectiveDurationMs).coerceIn(0f, 1f)
                    val speed = String.format(java.util.Locale.US, "%.1fx", statistics.speed)
                    onProgress(progress, speed)
                }
            }
        )

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    suspend fun extractAudio(
        inputPath: String,
        outputPath: String,
        durationMs: Long,
        options: AudioOptions,
        onProgress: (Float, String) -> Unit
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val cmdList = mutableListOf<String>()
        cmdList.add("-y")

        if (options.trimStartMs > 0) {
            cmdList.add("-ss")
            cmdList.add(String.format(java.util.Locale.US, "%.3f", options.trimStartMs / 1000.0))
        }

        cmdList.add("-i")
        cmdList.add(inputPath)

        if (options.trimEndMs > options.trimStartMs) {
            val trimDurationSec = (options.trimEndMs - options.trimStartMs) / 1000.0
            cmdList.add("-t")
            cmdList.add(String.format(java.util.Locale.US, "%.3f", trimDurationSec))
        }

        cmdList.add("-vn") // Disable video

        when (options.format.lowercase()) {
            "mp3" -> {
                cmdList.add("-c:a")
                cmdList.add("libmp3lame")
                cmdList.add("-b:a")
                cmdList.add("${options.bitrateKbps}k")
            }
            "aac", "m4a" -> {
                cmdList.add("-c:a")
                cmdList.add("aac")
                cmdList.add("-b:a")
                cmdList.add("${options.bitrateKbps}k")
            }
            "opus" -> {
                cmdList.add("-c:a")
                cmdList.add("libopus")
                cmdList.add("-b:a")
                cmdList.add("${options.bitrateKbps}k")
            }
            "flac" -> {
                cmdList.add("-c:a")
                cmdList.add("flac")
            }
            "wav" -> {
                cmdList.add("-c:a")
                cmdList.add("pcm_s16le")
            }
        }

        cmdList.add(outputPath)

        val session = FFmpegKit.executeWithArgumentsAsync(
            cmdList.toTypedArray(),
            { completeSession ->
                if (ReturnCode.isSuccess(completeSession.returnCode)) {
                    continuation.resume(Result.success(File(outputPath)))
                } else {
                    continuation.resume(Result.failure(Exception("Audio extraction failed: ${completeSession.failStackTrace}")))
                }
            },
            {},
            { stats ->
                if (durationMs > 0 && stats != null) {
                    val progress = (stats.time.toFloat() / durationMs).coerceIn(0f, 1f)
                    onProgress(progress, String.format(java.util.Locale.US, "%.1fx", stats.speed))
                }
            }
        )

        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }
}
