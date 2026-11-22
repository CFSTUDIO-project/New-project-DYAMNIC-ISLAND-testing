package app.novapill.android

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.novapill.android.core.AppSettings
import app.novapill.android.core.AppSettingsStore
import app.novapill.android.lyrics.LrcParser
import app.novapill.android.lyrics.LyricLine
import app.novapill.android.media.MediaSessionHub
import app.novapill.android.media.MediaUiState
import app.novapill.android.overlay.IslandOverlayService
import app.novapill.android.utilities.StopwatchEngine
import app.novapill.android.utilities.TimerEngine
import app.novapill.android.utilities.TorchController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Date
import java.util.function.Consumer
import kotlin.math.max

private val Ink = Color(0xFF050506)
private val Panel = Color(0xFF101014)
private val PanelRaised = Color(0xFF17171C)
private val Edge = Color(0xFF29292F)
private val SoftWhite = Color(0xFFF2F0F0)
private val Muted = Color(0xFF929199)
private val Crimson = Color(0xFFD94A59)
private val Success = Color(0xFF55B88A)
private val Warning = Color(0xFFE0B85B)

class MainActivity : ComponentActivity() {
    private var beingRecorded by mutableStateOf(false)
    private var recordingCallback: Consumer<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            NovaTheme {
                NovaRoot(isBeingRecorded = beingRecorded)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 35) {
            val callback = Consumer<Int> { state ->
                beingRecorded = state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
            }
            recordingCallback = callback
            runCatching {
                val current = windowManager.addScreenRecordingCallback(mainExecutor, callback)
                callback.accept(current)
            }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 35) {
            recordingCallback?.let { callback ->
                runCatching { windowManager.removeScreenRecordingCallback(callback) }
            }
        }
        recordingCallback = null
        beingRecorded = false
        super.onStop()
    }
}

@Composable
private fun NovaTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Crimson,
        onPrimary = Color.White,
        secondary = Color(0xFFB5AEB2),
        background = Ink,
        onBackground = SoftWhite,
        surface = Panel,
        onSurface = SoftWhite,
        surfaceVariant = PanelRaised,
        onSurfaceVariant = Muted,
        outline = Edge
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Page { HOME, TOOLS, SETTINGS }

@Composable
private fun NovaRoot(isBeingRecorded: Boolean) {
    val context = LocalContext.current
    val store = remember { AppSettingsStore(context) }
    val settings by store.settings.collectAsState(initial = AppSettings())
    val media by MediaSessionHub.media.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.HOME) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissions = remember(permissionRefresh, refreshToken) { readPermissions(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshToken++ }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (content != null) scope.launch { store.setImportedLrc(content); page = Page.TOOLS }
        }
    }

    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        if (!settings.setupComplete && !settings.welcomeSeen) {
            WelcomeScreen(onGetStarted = { scope.launch { store.setWelcomeSeen(true) } })
        } else if (!settings.setupComplete) {
            SetupScreen(
                permissions = permissions,
                onPermission = { kind -> openPermission(context, kind, notificationPermissionLauncher) },
                onContinue = { scope.launch { store.setSetupComplete(true) } },
                onRefresh = { refreshToken++ }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF08080A), Ink, Color(0xFF09090C))))
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                ) {
                    AnimatedContent(targetState = page, label = "main_page") { current ->
                        when (current) {
                            Page.HOME -> HomeScreen(
                                media = media,
                                permissions = permissions,
                                isBeingRecorded = isBeingRecorded,
                                onStartIsland = {
                                    if (permissions.overlay) IslandOverlayService.start(context)
                                    else openPermission(context, PermissionKind.OVERLAY, notificationPermissionLauncher)
                                },
                                onStopIsland = { IslandOverlayService.stop(context) },
                                onOpenPermissions = { page = Page.SETTINGS },
                                onOpenTools = { page = Page.TOOLS }
                            )
                            Page.TOOLS -> ToolsScreen(
                                media = media,
                                settings = settings,
                                store = store,
                                onImportLrc = { documentLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                                onRefreshPermissions = { refreshToken++ }
                            )
                            Page.SETTINGS -> SettingsScreen(
                                settings = settings,
                                store = store,
                                permissions = permissions,
                                onPermission = { kind -> openPermission(context, kind, notificationPermissionLauncher) },
                                onRefresh = { refreshToken++ }
                            )
                        }
                    }
                }
                BottomNavigation(current = page, onSelect = { page = it })
                Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
        }
    }
}

private enum class PermissionKind { OVERLAY, NOTIFICATION_ACCESS, POST_NOTIFICATIONS, BATTERY }

private data class PermissionStatus(
    val overlay: Boolean,
    val notificationAccess: Boolean,
    val postNotifications: Boolean,
    val batteryExemption: Boolean
)

private fun readPermissions(context: Context): PermissionStatus {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val listenerEnabled = enabledListeners.split(':').any { component ->
        runCatching { ComponentName.unflattenFromString(component)?.packageName == context.packageName }.getOrDefault(false)
    }
    val postAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return PermissionStatus(
        overlay = Settings.canDrawOverlays(context),
        notificationAccess = listenerEnabled,
        postNotifications = postAllowed,
        batteryExemption = Build.VERSION.SDK_INT < 23 || power.isIgnoringBatteryOptimizations(context.packageName)
    )
}

private fun openPermission(
    context: Context,
    kind: PermissionKind,
    notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val intent = when (kind) {
        PermissionKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = android.net.Uri.parse("package:${context.packageName}") }
        PermissionKind.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        PermissionKind.POST_NOTIFICATIONS -> null
        PermissionKind.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
    if (kind == PermissionKind.POST_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else if (intent != null) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111014), Ink, Color.Black)))
            .padding(horizontal = 26.dp, vertical = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(420)) + scaleIn(initialScale = 0.94f, animationSpec = tween(520)) +
                slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(520))
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BrandMark(size = 62)
                Text("NOVA PILL", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                Text("Your screen,\nin the flow.", color = SoftWhite, fontSize = 39.sp, lineHeight = 43.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Music, notifications and everyday controls — gathered in one calm, interactive capsule.",
                    color = Muted, fontSize = 14.sp, lineHeight = 22.sp
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Color.Black).border(1.dp, Edge, RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniArtwork()
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("A quieter kind of live activity", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Designed for Android", color = Muted, fontSize = 10.sp)
                        }
                        Waveform(isPlaying = true)
                    }
                }
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson)
                ) {
                    Text("Get started", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(17.dp))
                }
                Text("No account. No backend. Your media metadata stays on this device.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SetupScreen(
    permissions: PermissionStatus,
    onPermission: (PermissionKind) -> Unit,
    onContinue: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandMark(size = 42)
        Text("NOVA PILL · SETUP", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text("Set up your\nlive layer.", color = SoftWhite, fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Enable only the access you want. Required access powers the overlay; optional features can be skipped and enabled later.",
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermissionCard("Display over other apps", "Needed to place the island above other screens.", permissions.overlay, true) {
                onPermission(PermissionKind.OVERLAY)
            }
            PermissionCard("Notification access", "Reads media session metadata and app notification identity. Message previews stay private by default.", permissions.notificationAccess, true) {
                onPermission(PermissionKind.NOTIFICATION_ACCESS)
            }
            PermissionCard("Notifications", "Allows the session status notification on Android 13 and newer.", permissions.postNotifications, false) {
                onPermission(PermissionKind.POST_NOTIFICATIONS)
            }
            PermissionCard("Battery guidance", "Optional. Some device makers may stop background work more aggressively.", permissions.batteryExemption, false) {
                onPermission(PermissionKind.BATTERY)
            }
            PermissionCard("Shizuku integration", "Optional and not required by NOVA PILL core features.", true, false, statusOverride = "NOT REQUIRED") { }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Edge)) {
                Icon(Icons.Outlined.Refresh, null, tint = Muted, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Refresh", color = SoftWhite)
            }
            Button(onClick = onContinue, modifier = Modifier.weight(1.25f), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) {
                Text("Continue"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(17.dp))
            }
        }
        Text("Optional permissions can be skipped and enabled later in Settings.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun HomeScreen(
    media: MediaUiState?,
    permissions: PermissionStatus,
    isBeingRecorded: Boolean,
    onStartIsland: () -> Unit,
    onStopIsland: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenTools: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        TopHeader()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your screen, in the flow.", color = SoftWhite, fontSize = 29.sp, fontWeight = FontWeight.SemiBold, lineHeight = 33.sp)
            Text("A live layer that keeps the essentials close.", color = Muted, fontSize = 13.sp)
        }
        PillPreview(media)
        SectionHeading("ISLAND SESSION", "Overlay controls")
        CardSurface {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Overlay service", color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(if (permissions.overlay) "Permission ready · service starts on request" else "Display permission required", color = Muted, fontSize = 11.sp)
                }
                StatusDot(permissions.overlay, if (permissions.overlay) "READY" else "ACTION")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartIsland, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Start island")
                }
                OutlinedButton(onClick = onStopIsland, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Edge)) {
                    Text("Stop island", color = SoftWhite)
                }
            }
            Text("Android may adjust overlay position or visibility. NOVA PILL does not override system controls.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        SectionHeading("NOW PLAYING", "Android media session")
        MediaCard(media)
        SectionHeading("QUICK ACCESS", "Tools and preferences")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Utilities", "Timer · volume · flash", Icons.Outlined.Build, Modifier.weight(1f), onOpenTools)
            QuickTile("Permissions", "Review system access", Icons.Outlined.Security, Modifier.weight(1f), onOpenPermissions)
        }
        if (!permissions.notificationAccess) {
            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Notifications, null, tint = Warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connect media access", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Enable Notification access to detect supported players.", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                }
            }
        }
        if (isBeingRecorded) {
            CardSurface {
                StatusDot(false, "OS SIGNAL")
                Text("This app is visible in a screen recording.", color = SoftWhite, fontSize = 13.sp)
                Text("Android reports capture visibility for NOVA PILL's own activities only; this is not a detector for other apps.", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ToolsScreen(
    media: MediaUiState?,
    settings: AppSettings,
    store: AppSettingsStore,
    onImportLrc: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timer by TimerEngine.state.collectAsState()
    val stopwatch by StopwatchEngine.state.collectAsState()
    val torchOn by TorchController.enabled.collectAsState()
    val torchError by TorchController.error.collectAsState()
    var timerMinutes by remember { mutableStateOf("5") }
    var city by remember(settings.selectedWeatherCity) { mutableStateOf(settings.selectedWeatherCity) }
    var volume by remember { mutableFloatStateOf(0f) }
    var maxVolume by remember { mutableIntStateOf(15) }
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var wallClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val battery = remember {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    DisposableEffect(Unit) {
        TorchController.attach(context)
        maxVolume = max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        onDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            wallClock = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(media?.isPlaying, settings.importedLrc) {
        while (media?.isPlaying == true && settings.importedLrc.isNotBlank()) {
            clock = SystemClock.elapsedRealtime()
            delay(250L)
        }
    }
    val lines = remember(settings.importedLrc) { LrcParser.parse(settings.importedLrc) }
    val activeLine = LrcParser.activeIndex(lines, media?.estimatedPosition(clock) ?: 0L, settings.lyricsSyncOffsetMs.toLong())

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopHeader()
        Text("Studio", color = SoftWhite, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("Small controls. Real system actions.", color = Muted, fontSize = 13.sp)

        SectionHeading("CLOCK", "Device local time")
        CardSurface {
            Text(
                android.text.format.DateFormat.getTimeFormat(context).format(Date(wallClock)),
                color = SoftWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
            Text(
                android.text.format.DateFormat.getLongDateFormat(context).format(Date(wallClock)),
                color = Muted,
                fontSize = 11.sp
            )
        }

        SectionHeading("MUSIC", "Live session")
        MediaCard(media, expanded = true)

        SectionHeading("LYRICS", "Your own timestamped files")
        CardSurface {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("LRC sync", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (lines.isNotEmpty()) "${lines.size} timestamped lines imported" else "No licensed lyric provider connected", color = Muted, fontSize = 11.sp)
                }
                OutlinedButton(onClick = onImportLrc, border = BorderStroke(1.dp, Edge)) { Text("Import .LRC", color = SoftWhite) }
            }
            if (lines.isNotEmpty()) {
                HorizontalDivider(color = Edge)
                val active = lines.getOrNull(activeLine)
                val before = lines.getOrNull(activeLine - 1)
                val after = lines.getOrNull(activeLine + 1)
                if (before != null) Text(before.text, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(active?.text ?: "Waiting for the first lyric timestamp", color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                if (after != null) Text(after.text, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Line-level timing from the imported file. No invented word sync.", color = Muted, fontSize = 10.sp)
            } else {
                Text("Import an .LRC file you are allowed to use. If no matching timed file is available, NOVA PILL will not guess lyrics.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

        SectionHeading("TIME", "Timer and stopwatch")
        CardSurface {
            Text("Countdown timer", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(formatDuration(timer.remainingMs), color = SoftWhite, fontSize = 31.sp, fontWeight = FontWeight.Light)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timerMinutes,
                    onValueChange = { timerMinutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
                Button(onClick = {
                    val mins = timerMinutes.toLongOrNull()?.coerceIn(1L, 1440L) ?: 5L
                    TimerEngine.start(mins * 60_000L)
                }, colors = ButtonDefaults.buttonColors(containerColor = Crimson)) { Text("Start") }
                OutlinedButton(onClick = { if (timer.running) TimerEngine.pause() else TimerEngine.resume() }, border = BorderStroke(1.dp, Edge)) {
                    Text(if (timer.running) "Pause" else "Resume", color = SoftWhite)
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(if (timer.complete) "Timer complete" else if (timer.running) "Running" else "Ready", color = if (timer.complete) Success else Muted, fontSize = 11.sp)
                Spacer(Modifier.width(12.dp))
                Text("Cancel", color = SoftWhite, fontSize = 11.sp, modifier = Modifier.clickable { TimerEngine.cancel() }.padding(horizontal = 4.dp))
            }
            HorizontalDivider(color = Edge)
            Text("Stopwatch", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(formatDuration(stopwatch.elapsedMs), color = SoftWhite, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (stopwatch.running) StopwatchEngine.pause() else StopwatchEngine.start() }, colors = ButtonDefaults.buttonColors(containerColor = Crimson)) {
                    Text(if (stopwatch.running) "Pause" else "Start")
                }
                OutlinedButton(onClick = { StopwatchEngine.reset() }, border = BorderStroke(1.dp, Edge)) { Text("Reset", color = SoftWhite) }
            }
        }

        SectionHeading("DEVICE", "Quick controls")
        CardSurface {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Flashlight", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(torchError ?: if (torchOn) "Torch is on" else "Device flashlight state", color = Muted, fontSize = 11.sp)
                }
                Button(onClick = { TorchController.toggle(context) }, colors = ButtonDefaults.buttonColors(containerColor = if (torchOn) Crimson else PanelRaised)) {
                    Icon(Icons.Outlined.FlashOn, null, tint = SoftWhite); Spacer(Modifier.width(5.dp)); Text(if (torchOn) "Turn off" else "Turn on", color = SoftWhite)
                }
            }
            HorizontalDivider(color = Edge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Media volume", color = SoftWhite, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${volume.toInt()} / $maxVolume", color = Muted, fontSize = 11.sp)
            }
            Slider(value = volume, onValueChange = { volume = it; audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVolume.toFloat())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryChargingFull, null, tint = Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Battery", color = SoftWhite, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("$battery%", color = Muted, fontSize = 12.sp)
            }
        }

        SectionHeading("WEATHER", "Manual city")
        CardSurface {
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weather provider is not connected in this build.", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { scope.launch { store.setWeatherCity(city) }; onRefreshPermissions() }, border = BorderStroke(1.dp, Edge)) { Text("Save city", color = SoftWhite) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    store: AppSettingsStore,
    permissions: PermissionStatus,
    onPermission: (PermissionKind) -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopHeader()
        Text("Settings", color = SoftWhite, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("A considered setup, not a wall of switches.", color = Muted, fontSize = 13.sp)
        SectionHeading("APPEARANCE", "Motion and feedback")
        CardSurface {
            SettingSwitch("Mini visualizer", "Animate only while a media session is playing.", settings.visualizerEnabled) { scope.launch { store.setVisualizerEnabled(it) } }
            HorizontalDivider(color = Edge)
            SettingSwitch("Reduced motion", "Keep transitions restrained and avoid continuous preview motion.", settings.reducedMotion) { scope.launch { store.setReducedMotion(it) } }
            HorizontalDivider(color = Edge)
            SettingSwitch("Hide when idle", "Fade the island away after the media session becomes inactive.", settings.hideWhenIdle) { scope.launch { store.setHideWhenIdle(it) } }
            var islandWidth by remember(settings.islandWidthDp) { mutableFloatStateOf(settings.islandWidthDp.toFloat()) }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Collapsed width", color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${islandWidth.toInt()} dp", color = Muted, fontSize = 11.sp)
                }
                Slider(
                    value = islandWidth,
                    onValueChange = { islandWidth = it },
                    onValueChangeFinished = { scope.launch { store.setIslandWidth(islandWidth.toInt()) } },
                    valueRange = 154f..210f
                )
            }
            var topOffset by remember(settings.topOffsetDp) { mutableFloatStateOf(settings.topOffsetDp.toFloat()) }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Top offset", color = SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${topOffset.toInt()} dp", color = Muted, fontSize = 11.sp)
                }
                Slider(
                    value = topOffset,
                    onValueChange = { topOffset = it },
                    onValueChangeFinished = { scope.launch { store.setTopOffset(topOffset.toInt()) } },
                    valueRange = 0f..32f
                )
            }
        }
        SectionHeading("NOTIFICATIONS", "Privacy first")
        CardSurface {
            SettingSwitch("Notification peek", "Show a short notification identity when an app alert arrives.", settings.notificationPeek) { scope.launch { store.setNotificationPeek(it) } }
            HorizontalDivider(color = Edge)
            SettingSwitch("Private preview", "Show only the app name; hide notification text.", settings.privatePreview) { scope.launch { store.setPrivatePreview(it) } }
            Text("Peek duration", color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4, 6).forEach { value ->
                    val selected = settings.notificationDurationSeconds == value
                    Surface(
                        color = if (selected) Crimson.copy(alpha = 0.16f) else PanelRaised,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) Crimson else Edge),
                        modifier = Modifier.clickable { scope.launch { store.setNotificationDuration(value) } }
                    ) { Text("${value}s", color = if (selected) SoftWhite else Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)) }
                }
            }
        }
        SectionHeading("LYRICS", "Timing calibration")
        CardSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sync offset", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${if (settings.lyricsSyncOffsetMs > 0) "+" else ""}${settings.lyricsSyncOffsetMs} ms", color = Muted, fontSize = 11.sp)
            }
            var lyricOffset by remember(settings.lyricsSyncOffsetMs) { mutableFloatStateOf(settings.lyricsSyncOffsetMs.toFloat()) }
            Slider(
                value = lyricOffset,
                onValueChange = { lyricOffset = (it / 50f).toInt() * 50f },
                onValueChangeFinished = { scope.launch { store.setLyricsSyncOffset(lyricOffset.toInt()) } },
                valueRange = -1500f..1500f,
                steps = 59
            )
            OutlinedButton(onClick = { lyricOffset = 0f; scope.launch { store.setLyricsSyncOffset(0) } }, border = BorderStroke(1.dp, Edge)) {
                Text("Reset offset", color = SoftWhite)
            }
        }
        SectionHeading("ACCESS", "System permission status")
        CardSurface {
            PermissionCard("Display over other apps", "Required to render the live island.", permissions.overlay, true) { onPermission(PermissionKind.OVERLAY) }
            PermissionCard("Notification access", "Allows media-session discovery and notification identity.", permissions.notificationAccess, true) { onPermission(PermissionKind.NOTIFICATION_ACCESS) }
            PermissionCard("Post notifications", "Used for the visible overlay session status.", permissions.postNotifications, false) { onPermission(PermissionKind.POST_NOTIFICATIONS) }
            PermissionCard("Battery optimization", "Optional device-specific background guidance.", permissions.batteryExemption, false) { onPermission(PermissionKind.BATTERY) }
            PermissionCard("Shizuku", "Not used by core features.", true, false, statusOverride = "NOT REQUIRED") { }
            OutlinedButton(onClick = onRefresh, border = BorderStroke(1.dp, Edge), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Refresh permissions", color = SoftWhite)
            }
        }
        SectionHeading("RECORD INDICATOR", "Platform scope")
        CardSurface {
            SettingSwitch("Manual indicator", "A user-controlled visual marker. It does not detect recordings by itself.", settings.manualRecordingIndicator) { scope.launch { store.setManualRecording(it) } }
            Text("Android 15+ can report when NOVA PILL's own activity is visible in a recording. It cannot be used here as a universal detector for other apps.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        SectionHeading("ABOUT", "Build information")
        CardSurface {
            InfoLine("App version", "1.0.0 · Initial build")
            InfoLine("Package", "app.novapill.android")
            InfoLine("Minimum Android", "8.0 / API 26")
            InfoLine("Target SDK", "36")
            InfoLine("Lyrics", "User-imported LRC only")
            InfoLine("Network", "No account or backend required")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean,
    statusOverride: String? = null,
    onClick: () -> Unit
) {
    val stateText = statusOverride ?: if (granted) "ENABLED" else if (required) "REQUIRED" else "OPTIONAL"
    val stateColor = if (granted || statusOverride != null) Success else Warning
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Edge),
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth().then(
            if (statusOverride == null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(stateColor, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(stateText, color = stateColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            if (statusOverride == null && !granted) Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PillPreview(media: MediaUiState?) {
    CardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("LIVE ISLAND PREVIEW", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Text("A little more in the moment.", color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            StatusDot(media?.isPlaying == true, if (media?.isPlaying == true) "PLAYING" else "STANDBY")
        }
        Box(Modifier.fillMaxWidth().padding(top = 5.dp), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.Black)
                    .border(1.dp, Color(0xFF303036), RoundedCornerShape(30.dp)).padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniArtwork(media?.artwork)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(media?.title ?: "NOVA PILL", color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(media?.artist?.ifBlank { "Waiting for media" } ?: "Waiting for media", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Waveform(isPlaying = media?.isPlaying == true)
            }
        }
        Text("Preview uses sample styling. Live metadata appears after media access is enabled.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun MediaCard(media: MediaUiState?, expanded: Boolean = false) {
    var positionClock by remember(media?.sampledAtElapsed) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media?.isPlaying, media?.sampledAtElapsed) {
        while (media?.isPlaying == true) {
            positionClock = SystemClock.elapsedRealtime()
            delay(220L)
        }
    }
    val displayedPosition = media?.estimatedPosition(positionClock) ?: 0L
    CardSurface {
        if (media == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniArtwork()
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("No active media session", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Open a supported music player to see its metadata here.", color = Muted, fontSize = 11.sp)
                }
                StatusDot(false, "IDLE")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniArtwork(media.artwork)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(media.title, color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(media.artist.ifBlank { media.packageName }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusDot(media.isPlaying, if (media.isPlaying) "PLAYING" else "PAUSED")
            }
            if (expanded) {
                val progress = if (media.durationMs > 0) (displayedPosition.toFloat() / media.durationMs).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    drawRoundRect(Edge, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                    drawRoundRect(Crimson, size = androidx.compose.ui.geometry.Size(size.width * progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { MediaSessionHub.previous() }, enabled = media.canSkipPrevious) { Icon(Icons.Outlined.SkipPrevious, null, tint = if (media.canSkipPrevious) SoftWhite else Muted) }
                IconButton(onClick = { MediaSessionHub.togglePlayPause() }, enabled = media.canPlayPause) {
                    Icon(if (media.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = SoftWhite)
                }
                IconButton(onClick = { MediaSessionHub.next() }, enabled = media.canSkipNext) { Icon(Icons.Outlined.SkipNext, null, tint = if (media.canSkipNext) SoftWhite else Muted) }
            }
            Text("Controls are enabled only when the active player exposes the action.", color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun BottomNavigation(current: Page, onSelect: (Page) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFF111115)).border(1.dp, Edge, RoundedCornerShape(22.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomItem("Home", Icons.Outlined.Home, current == Page.HOME) { onSelect(Page.HOME) }
        BottomItem("Studio", Icons.Outlined.Build, current == Page.TOOLS) { onSelect(Page.TOOLS) }
        BottomItem("Settings", Icons.Outlined.Settings, current == Page.SETTINGS) { onSelect(Page.SETTINGS) }
    }
}

@Composable
private fun BottomItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, null, tint = if (selected) SoftWhite else Muted, modifier = Modifier.size(19.dp))
        Text(label, color = if (selected) SoftWhite else Muted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Box(Modifier.size(3.dp).background(Crimson, CircleShape))
    }
}

@Composable
private fun TopHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BrandMark(size = 30)
        Spacer(Modifier.width(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("NOVA PILL", color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
            Text("LIVE ISLAND", color = Muted, fontSize = 8.sp, letterSpacing = 1.8.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Panel, shape = CircleShape, border = BorderStroke(1.dp, Edge)) {
            Text("ANDROID", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp))
            .background(Color.Black).border(1.dp, Edge, RoundedCornerShape((size * 0.3f).dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding((size * 0.19f).dp)) {
            val mid = this.size.height / 2f
            val points = listOf(
                Offset(0f, mid), Offset(this.size.width * .19f, mid),
                Offset(this.size.width * .31f, this.size.height * .23f),
                Offset(this.size.width * .48f, this.size.height * .8f),
                Offset(this.size.width * .64f, this.size.height * .38f),
                Offset(this.size.width * .74f, mid), Offset(this.size.width, mid)
            )
            for (i in 0 until points.lastIndex) drawLine(Crimson, points[i], points[i + 1], strokeWidth = size * 0.055f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MiniArtwork(bitmap: Bitmap? = null) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF211D22)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(Modifier.size(20.dp)) {
                drawCircle(Crimson.copy(alpha = 0.22f), radius = size.minDimension * .49f)
                drawCircle(Crimson, radius = size.minDimension * .2f)
            }
        }
    }
}

@Composable
private fun Waveform(isPlaying: Boolean) {
    if (!isPlaying) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) { Box(Modifier.width(2.dp).height(4.dp).background(Muted, RoundedCornerShape(2.dp))) }
        }
    } else {
        val transition = rememberInfiniteTransition(label = "wave")
        val values = (0..3).map { index ->
            transition.animateFloat(
                initialValue = 4f,
                targetValue = 13f + (index % 2) * 5f,
                animationSpec = infiniteRepeatable(tween(380 + index * 90, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar_$index"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(18.dp)) {
            values.forEach { height -> Box(Modifier.width(2.dp).height(height.value.dp).background(SoftWhite, RoundedCornerShape(2.dp))) }
        }
    }
}

@Composable
private fun CardSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
            .border(1.dp, Edge, RoundedCornerShape(20.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = SoftWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.weight(1f))
        Text(subtitle, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun StatusDot(active: Boolean, label: String) {
    val color = if (active) Success else Muted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun QuickTile(title: String, description: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Edge, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(icon, null, tint = Crimson, modifier = Modifier.size(20.dp))
        Text(title, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, remainder)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, remainder)
}
