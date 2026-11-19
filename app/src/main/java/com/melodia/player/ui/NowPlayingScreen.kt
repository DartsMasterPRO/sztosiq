@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.melodia.player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.melodia.player.MusicViewModel
import com.melodia.player.playback.VisualizerController
import com.melodia.player.ui.theme.LocalUiSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun hasAudioPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun NowPlayingScreen(vm: MusicViewModel, nav: NavController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUiSettings.current
    val p by vm.player.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val favorites by vm.favoriteSet.collectAsStateWithLifecycle()
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val artColor by vm.artColor.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val sessionId by vm.audioSessionId.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val sleepEndOfTrack by vm.sleepEndOfTrack.collectAsStateWithLifecycle()

    val meta = p.item?.mediaMetadata
    val currentSong = song
    val isFav = currentSong != null && currentSong.id in favorites

    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var editLyrics by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }

    // ---- wizualizacja ----
    var micGranted by remember { mutableStateOf(hasAudioPermission(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { micGranted = hasAudioPermission(ctx) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val vizActive = ui.visualizer && micGranted && sessionId != 0
    DisposableEffect(vizActive, sessionId) {
        if (vizActive) VisualizerController.start(sessionId)
        onDispose { VisualizerController.stop() }
    }
    val bars by VisualizerController.bars.collectAsStateWithLifecycle()
    val wave by VisualizerController.wave.collectAsStateWithLifecycle()
    val level by VisualizerController.level.collectAsStateWithLifecycle()
    val pulse by animateFloatAsState(if (vizActive && p.isPlaying) level else 0f, tween(90), label = "pulse")

    val lyricsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importLyrics(uri)
    }

    BackHandler(onBack = onClose)

    val scheme = MaterialTheme.colorScheme
    val accent = artColor ?: scheme.primary

    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Box(Modifier.fillMaxSize()) {
            // ---- Tło ----
            if (ui.npStyle == 2) {
                AsyncImage(
                    model = meta?.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to scheme.background.copy(alpha = 0.35f),
                            0.45f to scheme.background.copy(alpha = 0.55f),
                            0.7f to scheme.background.copy(alpha = 0.92f),
                            1f to scheme.background
                        )
                    )
                )
            } else {
                AsyncImage(
                    model = meta?.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.30f)
                )
                // Dynamiczne tło – gradient w kolorze okładki
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent, scheme.background))
                    )
                )
            }
            if (ui.animatedBg) {
                AnimatedBlobs(
                    colors = listOf(accent, scheme.tertiary, scheme.secondary),
                    pulse = pulse,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Górny pasek ----
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Zwiń", Modifier.size(32.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Teraz odtwarzane", style = MaterialTheme.typography.labelLarge)
                        meta?.albumTitle?.let {
                            Text(
                                it.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Więcej") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Wyłącznik czasowy") },
                                leadingIcon = { Icon(Icons.Default.Timer, null) },
                                onClick = { menuOpen = false; showTimer = true })
                            DropdownMenuItem(
                                text = { Text("Prędkość odtwarzania") },
                                leadingIcon = { Icon(Icons.Default.Speed, null) },
                                onClick = { menuOpen = false; showSpeed = true })
                            DropdownMenuItem(
                                text = { Text("Equalizer") },
                                leadingIcon = { Icon(Icons.Default.Equalizer, null) },
                                onClick = { menuOpen = false; onClose(); nav.navigate("eq") { launchSingleTop = true } })
                            DropdownMenuItem(
                                text = { Text("Wygląd odtwarzacza") },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = { menuOpen = false; onClose(); nav.navigate("settings") { launchSingleTop = true } })
                            if (currentSong != null) {
                                DropdownMenuItem(
                                    text = { Text("Dodaj do playlisty") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                    onClick = { menuOpen = false; showAddToPlaylist = true })
                                DropdownMenuItem(
                                    text = { Text("Przejdź do albumu") },
                                    leadingIcon = { Icon(Icons.Default.Album, null) },
                                    onClick = { menuOpen = false; onClose(); nav.navigate("album/${currentSong.albumId}") })
                                DropdownMenuItem(
                                    text = { Text("Przejdź do wykonawcy") },
                                    leadingIcon = { Icon(Icons.Default.Person, null) },
                                    onClick = {
                                        menuOpen = false; onClose()
                                        nav.navigate("artist/${Uri.encode(currentSong.artist)}")
                                    })
                            }
                        }
                    }
                }

                // ---- Okładka / tekst ----
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyrics) {
                        LyricsView(
                            lyrics = lyrics,
                            positionMs = position,
                            hasSong = currentSong != null,
                            onSeek = { vm.seekTo(it) },
                            onImport = { lyricsLauncher.launch(arrayOf("*/*")) },
                            onEdit = { editLyrics = true },
                            onDelete = { vm.deleteLyrics() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (ui.npStyle != 2) {
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val size = minOf(maxWidth, maxHeight) * ui.artScale.coerceIn(0.5f, 1f)
                            val swipe = Modifier.pointerInput(Unit) {
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragEnd = {
                                        if (total > 150f) vm.previousTrack()
                                        else if (total < -150f) vm.next()
                                    }
                                ) { change, dx ->
                                    total += dx
                                    change.consume()
                                }
                            }
                            if (ui.npStyle == 1) {
                                VinylArt(meta?.artworkUri, p.isPlaying, Modifier.size(size).scale(1f + pulse * 0.05f).then(swipe))
                            } else {
                                Artwork(
                                    meta?.artworkUri,
                                    Modifier.size(size).scale(1f + pulse * 0.05f).then(swipe),
                                    corner = 24.dp
                                )
                            }
                        }
                    } else {
                        // pełny ekran – okładka jest tłem, tu tylko gest przewijania
                        Box(Modifier.fillMaxSize().pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total > 150f) vm.previousTrack()
                                    else if (total < -150f) vm.next()
                                }
                            ) { change, dx ->
                                total += dx
                                change.consume()
                            }
                        })
                    }
                }

                // ---- Wizualizacja ----
                if (ui.visualizer) {
                    if (!micGranted) {
                        TextButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text("Zezwól na wizualizację muzyki")
                        }
                    } else if (ui.visualizerStyle == 0) {
                        SpectrumBars(bars, accent, Modifier.fillMaxWidth().height(56.dp))
                    } else {
                        WaveLine(wave, accent, Modifier.fillMaxWidth().height(56.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ---- Tytuł ----
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            meta?.title?.toString().orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            meta?.artist?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (currentSong != null) {
                        IconButton(onClick = { vm.toggleFavorite(currentSong.id) }) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (isFav) "Usuń z ulubionych" else "Dodaj do ulubionych",
                                tint = if (isFav) scheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }

                // ---- Format (Hi-Res) ----
                p.format?.let { fmt ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (p.hiRes) {
                            Text(
                                "Hi-Res",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(scheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            fmt,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .border(BorderStroke(1.dp, scheme.outlineVariant), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ---- Pasek postępu ----
                val duration = p.duration.coerceAtLeast(1L).toFloat()
                Slider(
                    value = (dragPosition ?: position.toFloat()).coerceIn(0f, duration),
                    onValueChange = { dragPosition = it },
                    onValueChangeFinished = {
                        dragPosition?.let { vm.seekTo(it.toLong()) }
                        dragPosition = null
                    },
                    valueRange = 0f..duration,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDuration((dragPosition ?: position.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("-" + formatDuration((p.duration - (dragPosition?.toLong() ?: position)).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium)
                }

                // ---- Sterowanie ----
                val inactive = scheme.onSurfaceVariant
                val active = scheme.primary
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.toggleShuffle() }) {
                        Icon(Icons.Default.Shuffle, "Losowo", tint = if (p.shuffle) active else inactive)
                    }
                    IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Poprzedni", Modifier.size(38.dp))
                    }
                    FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(76.dp)) {
                        Icon(
                            if (p.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (p.isPlaying) "Pauza" else "Odtwórz",
                            Modifier.size(42.dp)
                        )
                    }
                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipNext, "Następny", Modifier.size(38.dp))
                    }
                    IconButton(onClick = { vm.cycleRepeat() }) {
                        Icon(
                            if (p.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            "Powtarzanie",
                            tint = if (p.repeat != Player.REPEAT_MODE_OFF) active else inactive
                        )
                    }
                }

                // ---- Głośność ----
                VolumeRow()

                // ---- Dolny pasek ----
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SleepChip(sleepEndsAt, sleepEndOfTrack) { showTimer = true }
                    Spacer(Modifier.weight(1f))
                    if (p.speed != 1f) {
                        AssistChip(
                            onClick = { showSpeed = true },
                            label = { Text("%.2fx".format(p.speed)) },
                            leadingIcon = { Icon(Icons.Default.Speed, null, Modifier.size(18.dp)) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Default.Notes, "Tekst piosenki",
                            tint = if (showLyrics) scheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Kolejka")
                    }
                }
            }
        }
    }

    if (showQueue) QueueSheet(vm) { showQueue = false }
    if (showTimer) SleepTimerDialog(vm, sleepEndsAt, sleepEndOfTrack) { showTimer = false }
    if (showSpeed) SpeedDialog(p.speed, onChange = { vm.setSpeed(it) }) { showSpeed = false }
    if (showAddToPlaylist && currentSong != null) AddToPlaylistDialog(vm, listOf(currentSong.id)) { showAddToPlaylist = false }
    if (editLyrics) LyricsEditDialog(lyrics?.raw.orEmpty(), onSave = { vm.saveLyricsText(it) }) { editLyrics = false }
}

/** Okładka jako obracająca się płyta winylowa. */
@Composable
private fun VinylArt(model: Any?, playing: Boolean, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                rotation.snapTo(rotation.value % 360f)
                rotation.animateTo(rotation.value + 360f, tween(14000, easing = LinearEasing))
            }
        }
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        Artwork(
            model,
            Modifier.fillMaxSize(0.94f).rotate(rotation.value),
            corner = 1000.dp
        )
        Box(
            Modifier
                .fillMaxSize(0.12f)
                .clip(CircleShape)
                .background(Color(0xFF111111))
                .border(2.dp, Color(0x55FFFFFF), CircleShape)
        )
    }
}

/** Regulacja głośności multimediów (systemowej). */
@Composable
private fun VolumeRow() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!touching) volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            delay(700)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = volume,
            onValueChange = {
                touching = true
                volume = it
                runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) }
            },
            onValueChangeFinished = { touching = false },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepChip(endsAt: Long?, endOfTrack: Boolean, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    when {
        endsAt != null -> AssistChip(
            onClick = onClick,
            label = { Text(formatDuration(endsAt - now)) },
            leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) }
        )
        endOfTrack -> AssistChip(
            onClick = onClick,
            label = { Text("Po utworze") },
            leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) }
        )
        else -> IconButton(onClick = onClick) { Icon(Icons.Default.Timer, "Wyłącznik czasowy") }
    }
}

@Composable
private fun QueueSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val p by vm.player.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val i = queue.indexOfFirst { it.index == p.index }
        if (i > 0) listState.scrollToItem(i)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kolejka (${queue.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearQueue(); onDismiss() }) { Text("Wyczyść") }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            itemsIndexed(queue, key = { _, e -> e.index }) { pos, e ->
                val current = e.index == p.index
                val m = e.item.mediaMetadata
                ListItem(
                    modifier = Modifier.clickable { vm.playQueueIndex(e.index) },
                    leadingContent = {
                        Box(contentAlignment = Alignment.Center) {
                            Artwork(m.artworkUri, Modifier.size(44.dp), corner = 6.dp)
                            if (current) Icon(Icons.Default.GraphicEq, null, tint = Color.White)
                        }
                    },
                    headlineContent = {
                        Text(
                            m.title?.toString().orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            fontWeight = if (current) FontWeight.SemiBold else null
                        )
                    },
                    supportingContent = { Text(m.artist?.toString().orEmpty(), maxLines = 1) },
                    trailingContent = {
                        Row {
                            // Zmiana kolejności (bez trybu losowego kolejność listy = kolejność odtwarzania)
                            if (!p.shuffle) {
                                IconButton(
                                    onClick = { vm.moveInQueue(e.index, e.index - 1) },
                                    enabled = pos > 0
                                ) { Icon(Icons.Default.KeyboardArrowUp, "Wyżej") }
                                IconButton(
                                    onClick = { vm.moveInQueue(e.index, e.index + 1) },
                                    enabled = pos < queue.lastIndex
                                ) { Icon(Icons.Default.KeyboardArrowDown, "Niżej") }
                            }
                            IconButton(onClick = { vm.removeFromQueue(e.index) }) {
                                Icon(Icons.Default.Close, "Usuń z kolejki")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(vm: MusicViewModel, endsAt: Long?, endOfTrack: Boolean, onDismiss: () -> Unit) {
    val active = endsAt != null || endOfTrack
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wyłącznik czasowy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) {
                    Text(
                        if (endOfTrack || endsAt == null) "Muzyka zatrzyma się po bieżącym utworze."
                        else "Muzyka zatrzyma się o ${
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(endsAt))
                        }.",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                listOf(5, 10, 15, 20, 30, 45, 60, 90).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { min ->
                            OutlinedButton(
                                onClick = { vm.startSleepTimer(min); onDismiss() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("$min min") }
                        }
                    }
                }
                FilledTonalButton(
                    onClick = { vm.sleepAfterTrack(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Po zakończeniu utworu") }
            }
        },
        confirmButton = {
            if (active) TextButton(onClick = { vm.cancelSleepTimer(); onDismiss() }) { Text("Wyłącz timer") }
            else TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
    )
}

@Composable
fun SpeedDialog(speed: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prędkość odtwarzania") },
        text = {
            Column {
                Text("%.2fx".format(value), style = MaterialTheme.typography.headlineMedium)
                Slider(
                    value = value,
                    onValueChange = { value = (it * 20).roundToInt() / 20f },
                    onValueChangeFinished = { onChange(value) },
                    valueRange = 0.5f..2f,
                    steps = 29
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        FilterChip(
                            selected = value == s,
                            onClick = { value = s; onChange(s) },
                            label = { Text(if (s == 1f) "1x" else "${s}x") }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } }
    )
}
