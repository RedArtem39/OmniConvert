@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omniconvert.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniconvert.app.model.MediaItem
import com.omniconvert.app.model.MediaType
import com.omniconvert.app.model.VideoOptions
import com.omniconvert.app.ui.components.ActionButton
import com.omniconvert.app.ui.components.ChoiceChip
import com.omniconvert.app.ui.components.FilePickerBanner
import com.omniconvert.app.ui.components.SectionCard
import com.omniconvert.app.util.FFmpegManager
import com.omniconvert.app.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File

enum class VideoSubMode {
    CONVERT,
    COMPRESS,
    TRIM
}

@Composable
fun VideoScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var currentSubMode by remember { mutableStateOf(VideoSubMode.CONVERT) }

    var targetFormat by remember { mutableStateOf("mp4") }
    var hardwareAcceleration by remember { mutableStateOf(true) }
    var resolution by remember { mutableStateOf("original") }
    var crfValue by remember { mutableFloatStateOf(23f) }
    var targetMb by remember { mutableStateOf<Float?>(null) }
    var muteAudio by remember { mutableStateOf(false) }

    var trimStartSec by remember { mutableFloatStateOf(0f) }
    var trimEndSec by remember { mutableFloatStateOf(0f) }

    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }
    var conversionSpeed by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val item = FileUtils.getMediaItemFromUri(context, it, MediaType.VIDEO)
                selectedItem = item
                trimStartSec = 0f
                trimEndSec = (item.durationMs / 1000f).coerceAtLeast(1f)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Video Picker Banner
        FilePickerBanner(
            title = selectedItem?.name ?: "Выберите видео",
            subtitle = if (selectedItem != null) {
                "${selectedItem?.formattedSize} • ${selectedItem?.formattedDuration} • ${selectedItem?.width}x${selectedItem?.height}"
            } else {
                "Поддерживает MP4, MKV, MOV, WebM, AVI, TS"
            },
            icon = Icons.Rounded.VideoFile,
            onClick = { videoPickerLauncher.launch("video/*") }
        )

        // SubMode Tabs (Convert, Compress, Trim)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                VideoSubMode.CONVERT to "Формат",
                VideoSubMode.COMPRESS to "Сжатие",
                VideoSubMode.TRIM to "Обрезка"
            ).forEach { (mode, title) ->
                ChoiceChip(
                    selected = currentSubMode == mode,
                    text = title,
                    onClick = { currentSubMode = mode },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 1. Convert Settings
        if (currentSubMode == VideoSubMode.CONVERT) {
            SectionCard(title = "Формат на выходе") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("mp4", "mkv", "mov", "webm", "gif").forEach { fmt ->
                        ChoiceChip(
                            selected = targetFormat == fmt,
                            text = fmt.uppercase(),
                            onClick = { targetFormat = fmt }
                        )
                    }
                }
            }

            SectionCard(title = "Разрешение") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("original" to "Оригинал", "1080p" to "1080p", "720p" to "720p", "480p" to "480p").forEach { (resKey, resLabel) ->
                        ChoiceChip(
                            selected = resolution == resKey,
                            text = resLabel,
                            onClick = { resolution = resKey }
                        )
                    }
                }
            }
        }

        // 2. Compress Settings
        if (currentSubMode == VideoSubMode.COMPRESS) {
            SectionCard(title = "Сжать под целевой размер") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        null to "По качеству",
                        10f to "10 МБ",
                        25f to "25 МБ",
                        50f to "50 МБ",
                        100f to "100 МБ"
                    ).forEach { (mb, label) ->
                        ChoiceChip(
                            selected = targetMb == mb,
                            text = label,
                            onClick = { targetMb = mb }
                        )
                    }
                }

                if (targetMb == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Качество (CRF): ${crfValue.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = crfValue,
                        onValueChange = { crfValue = it },
                        valueRange = 18f..32f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Лучше качество (18)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Сильнее сжатие (32)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 3. Trim Settings
        if (currentSubMode == VideoSubMode.TRIM && selectedItem != null) {
            val totalDurationSec = (selectedItem!!.durationMs / 1000f).coerceAtLeast(1f)
            SectionCard(title = "Обрезка по времени") {
                Text(
                    "Начало: ${String.format("%.1f", trimStartSec)} сек  •  Конец: ${String.format("%.1f", trimEndSec)} сек",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                RangeSlider(
                    value = trimStartSec..trimEndSec,
                    onValueChange = { range ->
                        trimStartSec = range.start
                        trimEndSec = range.endInclusive
                    },
                    valueRange = 0f..totalDurationSec,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }

        // Hardware Acceleration & Audio settings
        SectionCard(title = "Параметры кодирования") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Аппаратное ускорение (Qualcomm MediaCodec)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ускоряет обработку в 5-10 раз и экономит заряд батареи",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = hardwareAcceleration,
                    onCheckedChange = { hardwareAcceleration = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Отключить звук (Mute)", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = muteAudio,
                    onCheckedChange = { muteAudio = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }

        // Progress Card
        if (isConverting) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${(conversionProgress * 100).toInt()}%  $conversionSpeed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { conversionProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }
        }

        // Convert Button
        ActionButton(
            text = if (isConverting) "Конвертирую..." else "Запустить обработку",
            enabled = selectedItem != null && !isConverting,
            onClick = {
                val item = selectedItem ?: return@ActionButton
                isConverting = true
                statusMessage = "Подготовка файла..."

                coroutineScope.launch {
                    try {
                        val tempInput = FileUtils.copyUriToTempFile(context, item.uri, "input_", ".${targetFormat}")
                        val tempOutput = File(context.cacheDir, "output_${System.currentTimeMillis()}.$targetFormat")

                        val encoder = if (hardwareAcceleration) "h264_mediacodec" else "libx264"

                        val options = VideoOptions(
                            format = targetFormat,
                            encoder = encoder,
                            resolution = resolution,
                            crf = crfValue.toInt(),
                            targetSizeMb = targetMb,
                            trimStartMs = (trimStartSec * 1000).toLong(),
                            trimEndMs = (trimEndSec * 1000).toLong(),
                            muteAudio = muteAudio
                        )

                        statusMessage = "Кодирование видео..."
                        val result = FFmpegManager.convertVideo(
                            inputPath = tempInput.absolutePath,
                            outputPath = tempOutput.absolutePath,
                            durationMs = item.durationMs,
                            options = options,
                            onProgress = { progress, speed ->
                                conversionProgress = progress
                                conversionSpeed = speed
                            }
                        )

                        if (result.isSuccess) {
                            val savedUri = FileUtils.saveToGalleryOrDownloads(context, tempOutput, MediaType.VIDEO, targetFormat)
                            Toast.makeText(context, "Готово! Сохранено в папку Фильмы/OmniConvert", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Ошибка: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }

                        tempInput.delete()
                        tempOutput.delete()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isConverting = false
                        conversionProgress = 0f
                        conversionSpeed = ""
                        statusMessage = ""
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
