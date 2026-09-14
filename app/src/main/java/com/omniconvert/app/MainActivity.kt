@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.omniconvert.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omniconvert.app.ui.screens.AudioScreen
import com.omniconvert.app.ui.screens.PhotoScreen
import com.omniconvert.app.ui.screens.VideoScreen
import com.omniconvert.app.ui.theme.OmniConvertTheme
import com.omniconvert.app.util.AppSettings

enum class AppTab(val title: String, val icon: ImageVector) {
    VIDEO("Видео", Icons.Rounded.Videocam),
    PHOTO("Фото", Icons.Rounded.Image),
    AUDIO("Аудио", Icons.Rounded.Audiotrack)
}

class MainActivity : ComponentActivity() {

    private var sharedContent by mutableStateOf(SharedContent())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestAppPermissions()
        sharedContent = readSharedContent(intent)

        setContent {
            val context = LocalContext.current
            var useDynamicColors by remember { mutableStateOf(AppSettings.useDynamicColors(context)) }
            var useHardwareAcceleration by remember { mutableStateOf(AppSettings.useHardwareAcceleration(context)) }
            var stripExifByDefault by remember { mutableStateOf(AppSettings.stripExifByDefault(context)) }
            var selectedTab by remember { mutableStateOf(sharedContent.tab ?: AppTab.VIDEO) }
            var showSettings by remember { mutableStateOf(false) }
            var showAbout by remember { mutableStateOf(false) }

            LaunchedEffect(sharedContent) {
                sharedContent.tab?.let { selectedTab = it }
            }

            OmniConvertTheme(dynamicColor = useDynamicColors) {

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "OmniConvert",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            actions = {
                                IconButton(onClick = { showAbout = true }) {
                                    Icon(Icons.Rounded.Info, contentDescription = "О программе")
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            AppTab.values().forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (selectedTab) {
                            AppTab.VIDEO -> VideoScreen(
                                initialUri = sharedContent.videoUri,
                                defaultHardwareAcceleration = useHardwareAcceleration
                            )
                            AppTab.PHOTO -> PhotoScreen(
                                initialUris = sharedContent.imageUris,
                                defaultStripExif = stripExifByDefault
                            )
                            AppTab.AUDIO -> AudioScreen(initialUri = sharedContent.audioUri)
                        }
                    }
                }

                if (showSettings) {
                    SettingsDialog(
                        useDynamicColors = useDynamicColors,
                        onDynamicColorsChange = {
                            useDynamicColors = it
                            AppSettings.setUseDynamicColors(context, it)
                        },
                        useHardwareAcceleration = useHardwareAcceleration,
                        onHardwareAccelerationChange = {
                            useHardwareAcceleration = it
                            AppSettings.setUseHardwareAcceleration(context, it)
                        },
                        stripExifByDefault = stripExifByDefault,
                        onStripExifByDefaultChange = {
                            stripExifByDefault = it
                            AppSettings.setStripExifByDefault(context, it)
                        },
                        onDismiss = { showSettings = false }
                    )
                }

                if (showAbout) {
                    AboutDialog(onDismiss = { showAbout = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedContent = readSharedContent(intent)
    }

    @Suppress("DEPRECATION")
    private fun readSharedContent(intent: Intent?): SharedContent {
        if (intent == null || intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            return SharedContent()
        }

        val uris: List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.extras?.getParcelableArrayList<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        }
        if (uris.isEmpty()) return SharedContent()

        return when {
            intent.type?.startsWith("image/") == true -> SharedContent(tab = AppTab.PHOTO, imageUris = uris)
            intent.type?.startsWith("audio/") == true -> SharedContent(tab = AppTab.AUDIO, audioUri = uris.first())
            else -> SharedContent(tab = AppTab.VIDEO, videoUri = uris.first())
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}

private data class SharedContent(
    val tab: AppTab? = null,
    val imageUris: List<Uri> = emptyList(),
    val videoUri: Uri? = null,
    val audioUri: Uri? = null
)

@Composable
private fun SettingsDialog(
    useDynamicColors: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    useHardwareAcceleration: Boolean,
    onHardwareAccelerationChange: (Boolean) -> Unit,
    stripExifByDefault: Boolean,
    onStripExifByDefaultChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Внешний вид", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Динамические цвета")
                        Text(
                            "Использовать палитру устройства на Android 12 и новее",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useDynamicColors,
                        onCheckedChange = onDynamicColorsChange
                    )
                }
                HorizontalDivider()
                SettingSwitch(
                    title = "Аппаратное ускорение по умолчанию",
                    description = "Использовать MediaCodec для новых задач видео",
                    checked = useHardwareAcceleration,
                    onCheckedChange = onHardwareAccelerationChange
                )
                HorizontalDivider()
                SettingSwitch(
                    title = "Очищать EXIF у фото",
                    description = "Удалять геолокацию и данные камеры по умолчанию",
                    checked = stripExifByDefault,
                    onCheckedChange = onStripExifByDefaultChange
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("О программе") },
        text = {
            Text(
                "OmniConvert — бесплатный конвертер медиа без рекламы. " +
                    "Видео: MP4, MKV, MOV, WebM и GIF. Фото: JPG, PNG и WEBP. " +
                    "Аудио: MP3, AAC, Opus, FLAC и WAV.\n\n" +
                    "Готовые файлы сохраняются в папки OmniConvert в Галерее, Фильмах или Музыке."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
