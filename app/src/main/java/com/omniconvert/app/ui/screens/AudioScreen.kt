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
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniconvert.app.model.AudioOptions
import com.omniconvert.app.model.MediaItem
import com.omniconvert.app.model.MediaType
import com.omniconvert.app.ui.components.ActionButton
import com.omniconvert.app.ui.components.ChoiceChip
import com.omniconvert.app.ui.components.FilePickerBanner
import com.omniconvert.app.ui.components.SectionCard
import com.omniconvert.app.util.FFmpegManager
import com.omniconvert.app.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var targetFormat by remember { mutableStateOf("mp3") }
    var bitrateKbps by remember { mutableIntStateOf(192) }

    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableStateOf("") }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                selectedItem = FileUtils.getMediaItemFromUri(context, it, MediaType.AUDIO)
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
        FilePickerBanner(
            title = selectedItem?.name ?: "Выберите видео или аудио",
            subtitle = if (selectedItem != null) "${selectedItem?.formattedSize} • ${selectedItem?.formattedDuration}" else "Извлеките чистый звук в MP3/AAC/FLAC",
            icon = Icons.Rounded.AudioFile,
            onClick = { mediaPickerLauncher.launch("*/*") }
        )

        SectionCard(title = "Формат аудио") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("mp3", "aac", "opus", "flac", "wav").forEach { fmt ->
                    ChoiceChip(
                        selected = targetFormat == fmt,
                        text = fmt.uppercase(),
                        onClick = { targetFormat = fmt }
                    )
                }
            }
        }

        if (targetFormat in listOf("mp3", "aac", "opus")) {
            SectionCard(title = "Битрейт: $bitrateKbps kbps") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(128, 192, 256, 320).forEach { br ->
                        ChoiceChip(
                            selected = bitrateKbps == br,
                            text = "${br}k",
                            onClick = { bitrateKbps = br }
                        )
                    }
                }
            }
        }

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
                        Text("Извлечение аудио...", style = MaterialTheme.typography.bodyMedium)
                        Text("${(progress * 100).toInt()}%  $speed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }
        }

        ActionButton(
            text = if (isConverting) "Извлекаю..." else "Извлечь / Конвертировать",
            enabled = selectedItem != null && !isConverting,
            onClick = {
                val item = selectedItem ?: return@ActionButton
                isConverting = true

                coroutineScope.launch {
                    try {
                        val tempInput = FileUtils.copyUriToTempFile(context, item.uri, "audio_in_", ".tmp")
                        val tempOutput = File(context.cacheDir, "audio_out_${System.currentTimeMillis()}.$targetFormat")

                        val options = AudioOptions(
                            format = targetFormat,
                            bitrateKbps = bitrateKbps
                        )

                        val result = FFmpegManager.extractAudio(
                            inputPath = tempInput.absolutePath,
                            outputPath = tempOutput.absolutePath,
                            durationMs = item.durationMs,
                            options = options,
                            onProgress = { p, sp ->
                                progress = p
                                speed = sp
                            }
                        )

                        if (result.isSuccess) {
                            FileUtils.saveToGalleryOrDownloads(context, tempOutput, MediaType.AUDIO, targetFormat)
                            Toast.makeText(context, "Сохранено в Музыка/OmniConvert", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Ошибка: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }

                        tempInput.delete()
                        tempOutput.delete()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isConverting = false
                        progress = 0f
                        speed = ""
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
