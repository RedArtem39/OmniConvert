@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omniconvert.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.omniconvert.app.model.MediaType
import com.omniconvert.app.model.PhotoOptions
import com.omniconvert.app.ui.components.ActionButton
import com.omniconvert.app.ui.components.ChoiceChip
import com.omniconvert.app.ui.components.FilePickerBanner
import com.omniconvert.app.ui.components.SectionCard
import com.omniconvert.app.util.FileUtils
import com.omniconvert.app.util.ImageConverter
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PhotoScreen(
    initialUris: List<Uri> = emptyList(),
    defaultStripExif: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var targetFormat by remember { mutableStateOf("webp") }
    var quality by remember { mutableFloatStateOf(85f) }
    var resizePercent by remember { mutableIntStateOf(100) }
    var stripExif by remember { mutableStateOf(defaultStripExif) }

    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }
    var conversionStatusText by remember { mutableStateOf("") }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) selectedUris = initialUris
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
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
        // Top Banner / Picker
        FilePickerBanner(
            title = if (selectedUris.isEmpty()) "Выберите фото" else "Выбрано фото: ${selectedUris.size}",
            subtitle = if (selectedUris.isEmpty()) "Поддерживает JPG, PNG и WEBP" else "Нажмите, чтобы изменить выбор",
            icon = Icons.Rounded.AddPhotoAlternate,
            onClick = { photoPickerLauncher.launch("image/*") }
        )

        // Photo Preview Row
        if (selectedUris.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectedUris, key = { it.toString() }) { uri ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                remember(context, uri) { ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(160)
                                    .crossfade(false)
                                    .build() }
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = {
                                selectedUris = selectedUris.filter { it != uri }
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Format Selection Card
        SectionCard(title = "Формат на выходе") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("webp", "jpg", "png").forEach { fmt ->
                    ChoiceChip(
                        selected = targetFormat == fmt,
                        text = fmt.uppercase(),
                        onClick = { targetFormat = fmt }
                    )
                }
            }
        }

        // Quality & Compression Slider Card
        SectionCard(title = "Качество сжатия: ${quality.toInt()}%") {
            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 10f..100f,
                steps = 17,
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
                Text("Сильное сжатие", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Баланс (85%)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Без потерь", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Resolution Resize Card
        SectionCard(title = "Размер разрешения") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(100 to "100%", 75 to "75%", 50 to "50%", 25 to "25%").forEach { (pct, label) ->
                    ChoiceChip(
                        selected = resizePercent == pct,
                        text = label,
                        onClick = { resizePercent = pct }
                    )
                }
            }
        }

        // Privacy Options
        SectionCard(title = "Приватность") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Удалить EXIF-метаданные",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Очищает геолокацию, дату и модель камеры из файла",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = stripExif,
                    onCheckedChange = { stripExif = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }

        // Progress indicator
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
                        Text(conversionStatusText, style = MaterialTheme.typography.bodyMedium)
                        Text("${(conversionProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
            text = if (isConverting) "Конвертирую..." else "Конвертировать фото (${selectedUris.size})",
            enabled = selectedUris.isNotEmpty() && !isConverting,
            onClick = {
                isConverting = true
                coroutineScope.launch {
                    var successCount = 0
                    val total = selectedUris.size

                    val options = PhotoOptions(
                        format = targetFormat,
                        quality = quality.toInt(),
                        resizePercent = resizePercent,
                        stripExif = stripExif
                    )

                    selectedUris.forEachIndexed { index, uri ->
                        conversionStatusText = "Обработка фото ${index + 1} из $total..."
                        val tempOut = File(context.cacheDir, "photo_${System.currentTimeMillis()}_$index.$targetFormat")
                        val result = ImageConverter.convertImage(
                            context = context,
                            inputUri = uri,
                            outputFile = tempOut,
                            options = options,
                            onProgress = { fileProgress ->
                                conversionProgress = (index + fileProgress) / total.toFloat()
                            }
                        )

                        if (result.isSuccess) {
                            FileUtils.saveToGalleryOrDownloads(context, tempOut, MediaType.PHOTO, targetFormat)
                            tempOut.delete()
                            successCount++
                        }
                    }

                    isConverting = false
                    conversionProgress = 1f
                    Toast.makeText(
                        context,
                        "Успешно сконвертировано: $successCount фото в Pictures/OmniConvert",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
