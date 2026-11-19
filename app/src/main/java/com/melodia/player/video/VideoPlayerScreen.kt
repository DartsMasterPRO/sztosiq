package com.melodia.player.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.melodia.player.ui.SpeedDialog
import com.melodia.player.ui.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Dopasuj",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Wypełnij",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Rozciągnij",
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    vm: VideoViewModel,
    inPip: Boolean,
    onBack: () -> Unit,
    onPip: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val controller by vm.controller.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val bgAudio by vm.backgroundAudio.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var gestureIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var showSpeed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var orientationSet by remember { mutableStateOf(false) }

    val durationState = rememberUpdatedState(st.duration)
    val posState = rememberUpdatedState(pos)

    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val subsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.loadSubtitles(uri)
    }

    // Automatyczny obrót pod proporcje filmu (raz na film)
    LaunchedEffect(st.width, st.height, st.title) {
        if (st.width > 0 && st.height > 0 && !orientationSet) {
            activity.requestedOrientation = if (st.width >= st.height)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            orientationSet = true
        }
    }
    // Chowanie przycisków po 3,5 s
    LaunchedEffect(controlsVisible, interaction, st.isPlaying) {
        if (controlsVisible && st.isPlaying && !menuOpen) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestureLabel) {
        if (gestureLabel != null) {
            delay(900)
            gestureLabel = null
            gestureIcon = null
        }
    }
    LaunchedEffect(st.ended) { if (st.ended) controlsVisible = true }
    LaunchedEffect(st.isPlaying, bgAudio, st.width, st.height) {
        (activity as? VideoActivity)?.refreshPipParams()
    }

    fun touch() { interaction++ }
    fun flash(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
        gestureIcon = icon
        gestureLabel = text
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                if (view.player !== controller) view.player = controller
                view.resizeMode = RESIZE_MODES[resizeIndex].first
                view.keepScreenOn = st.isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        if (inPip) return@Box

        // ---------- Gesty ----------
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            touch()
                        },
                        onDoubleTap = { o ->
                            if (locked) return@detectTapGestures
                            when {
                                o.x < size.width / 3f -> {
                                    vm.seekBy(-10_000); flash(Icons.Default.Replay10, "-10 s")
                                }
                                o.x > size.width * 2f / 3f -> {
                                    vm.seekBy(10_000); flash(Icons.Default.Forward10, "+10 s")
                                }
                                else -> vm.togglePlay()
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var mode = 0 // 1 jasność, 2 głośność, 3 przewijanie
                    var start = Offset.Zero
                    var acc = Offset.Zero
                    var startBrightness = 0.5f
                    var startVolume = 0
                    var seekStart = 0L
                    var seekTarget = 0L
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0
                            start = o
                            acc = Offset.Zero
                        },
                        onDragEnd = {
                            if (mode == 3) vm.seekTo(seekTarget)
                            mode = 0
                        },
                        onDragCancel = { mode = 0 }
                    ) { change, drag ->
                        acc += drag
                        if (mode == 0 && acc.getDistance() > 28f) {
                            mode = when {
                                abs(acc.x) > abs(acc.y) -> 3
                                start.x < size.width / 2f -> 1
                                else -> 2
                            }
                            val lp = activity.window.attributes.screenBrightness
                            startBrightness = if (lp in 0f..1f) lp else systemBrightness(ctx)
                            startVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                            seekStart = posState.value
                        }
                        when (mode) {
                            1 -> {
                                val b = (startBrightness - acc.y / size.height * 1.3f).coerceIn(0.01f, 1f)
                                val lp = activity.window.attributes
                                lp.screenBrightness = b
                                activity.window.attributes = lp
                                flash(Icons.Default.BrightnessMedium, "Jasność ${(b * 100).roundToInt()}%")
                            }
                            2 -> {
                                val v = (startVolume - acc.y / size.height * maxVol * 1.3f).roundToInt().coerceIn(0, maxVol)
                                runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
                                flash(Icons.Default.VolumeUp, "Głośność ${v * 100 / maxVol}%")
                            }
                            3 -> {
                                val dur = durationState.value
                                val range = if (dur > 0) minOf(dur, 180_000L) else 180_000L
                                seekTarget = (seekStart + (acc.x / size.width * range).toLong())
                                    .coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
                                val delta = (seekTarget - seekStart) / 1000
                                flash(
                                    if (delta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                                    "${if (delta >= 0) "+" else ""}$delta s  (${formatDuration(seekTarget)})"
                                )
                            }
                        }
                        change.consume()
                    }
                }
        )

        // ---------- Komunikat gestu ----------
        gestureLabel?.let { label ->
            Row(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                gestureIcon?.let { Icon(it, null, tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (st.buffering && !st.isPlaying) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        // ---------- Blokada ----------
        if (locked) {
            AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                IconButton(
                    onClick = { locked = false; touch() },
                    modifier = Modifier.padding(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) { Icon(Icons.Default.Lock, "Odblokuj", tint = Color.White) }
            }
            return@Box
        }

        // ---------- Przyciski ----------
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // górny pasek
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz", tint = Color.White) }
                    Text(
                        st.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { subsLauncher.launch(arrayOf("*/*")); touch() }) {
                        Icon(
                            Icons.Default.ClosedCaption, "Napisy",
                            tint = if (st.hasSubtitles && st.subtitlesOn) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { vm.setBackgroundAudio(!bgAudio); touch() }) {
                        Icon(Icons.Default.Headset, "Dźwięk w tle", tint = if (bgAudio) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = onPip) { Icon(Icons.Default.PictureInPictureAlt, "Obraz w obrazie", tint = Color.White) }
                    Box {
                        IconButton(onClick = { menuOpen = true; touch() }) { Icon(Icons.Default.MoreVert, "Więcej", tint = Color.White) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false; touch() }) {
                            DropdownMenuItem(
                                text = { Text("Wczytaj napisy (.srt / .vtt)") },
                                leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                                onClick = { menuOpen = false; subsLauncher.launch(arrayOf("*/*")) })
                            if (st.hasSubtitles) {
                                DropdownMenuItem(
                                    text = { Text(if (st.subtitlesOn) "Wyłącz napisy" else "Włącz napisy") },
                                    leadingIcon = { Icon(Icons.Default.ClosedCaption, null) },
                                    onClick = { menuOpen = false; vm.setSubtitlesOn(!st.subtitlesOn) })
                            }
                            DropdownMenuItem(
                                text = { Text("Prędkość: %.2fx".format(st.speed)) },
                                leadingIcon = { Icon(Icons.Default.Speed, null) },
                                onClick = { menuOpen = false; showSpeed = true })
                            DropdownMenuItem(
                                text = { Text(if (bgAudio) "Dźwięk w tle: włączony" else "Dźwięk w tle: wyłączony") },
                                leadingIcon = { Icon(Icons.Default.Headset, null) },
                                onClick = { menuOpen = false; vm.setBackgroundAudio(!bgAudio) })
                            DropdownMenuItem(
                                text = { Text("Zablokuj ekran") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                onClick = { menuOpen = false; locked = true; touch() })
                        }
                    }
                }

                // środek
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    RoundButton(Icons.Default.SkipPrevious, "Poprzedni", 48) { vm.previous(); touch() }
                    RoundButton(Icons.Default.Replay10, "-10 s", 52) { vm.seekBy(-10_000); touch() }
                    RoundButton(
                        if (st.isPlaying) Icons.Default.Pause else if (st.ended) Icons.Default.Replay else Icons.Default.PlayArrow,
                        "Odtwórz / pauza", 72
                    ) { vm.togglePlay(); touch() }
                    RoundButton(Icons.Default.Forward10, "+10 s", 52) { vm.seekBy(10_000); touch() }
                    RoundButton(Icons.Default.SkipNext, "Następny", 48, enabled = st.hasNext) { vm.next(); touch() }
                }

                // dół
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val duration = st.duration.coerceAtLeast(1L).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatDuration((dragPosition ?: pos.toFloat()).toLong()),
                            color = Color.White, style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = (dragPosition ?: pos.toFloat()).coerceIn(0f, duration),
                            onValueChange = { dragPosition = it; touch() },
                            onValueChangeFinished = {
                                dragPosition?.let { vm.seekTo(it.toLong()) }
                                dragPosition = null
                            },
                            valueRange = 0f..duration,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Text(formatDuration(st.duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { locked = true; touch() }) { Icon(Icons.Default.LockOpen, "Zablokuj", tint = Color.White) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showSpeed = true }) {
                            Text("%.2fx".format(st.speed).replace(".00", ""), color = Color.White)
                        }
                        TextButton(onClick = { resizeIndex = (resizeIndex + 1) % RESIZE_MODES.size; touch() }) {
                            Icon(Icons.Default.AspectRatio, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(RESIZE_MODES[resizeIndex].second, color = Color.White)
                        }
                        IconButton(onClick = {
                            activity.requestedOrientation =
                                if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            touch()
                        }) { Icon(Icons.Default.ScreenRotation, "Obróć", tint = Color.White) }
                    }
                }
            }
        }
    }

    if (showSpeed) SpeedDialog(st.speed, onChange = { vm.setSpeed(it) }) { showSpeed = false }
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    sizeDp: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(sizeDp.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
    ) {
        Icon(
            icon, description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size((sizeDp * 0.55f).dp)
        )
    }
}

private fun systemBrightness(ctx: Context): Float = runCatching {
    android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
}.getOrDefault(0.5f)
