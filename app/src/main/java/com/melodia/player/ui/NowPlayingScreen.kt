@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.melodia.player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.melodia.player.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(vm: MusicViewModel, nav: NavController, onClose: () -> Unit) {
    val p by vm.player.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val favorites by vm.favoriteSet.collectAsStateWithLifecycle()
    val songMap by vm.songMap.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val sleepEndOfTrack by vm.sleepEndOfTrack.collectAsStateWithLifecycle()

    val meta = p.item?.mediaMetadata
    val song = p.item?.mediaId?.toLongOrNull()?.let { songMap[it] }
    val isFav = song != null && song.id in favorites

    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }

    BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // Rozmyta okładka w tle (Android 12+)
            AsyncImage(
                model = meta?.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.35f)
            )
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Górny pasek
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Zwiń", Modifier.size(32.dp)) }
                    Text(
                        "Teraz odtwarzane",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
                            if (song != null) {
                                DropdownMenuItem(
                                    text = { Text("Dodaj do playlisty") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                    onClick = { menuOpen = false; showAddToPlaylist = true })
                                DropdownMenuItem(
                                    text = { Text("Przejdź do albumu") },
                                    leadingIcon = { Icon(Icons.Default.Album, null) },
                                    onClick = { menuOpen = false; onClose(); nav.navigate("album/${song.albumId}") })
                                DropdownMenuItem(
                                    text = { Text("Przejdź do wykonawcy") },
                                    leadingIcon = { Icon(Icons.Default.Person, null) },
                                    onClick = {
                                        menuOpen = false; onClose(); nav.navigate("artist/${Uri.encode(song.artist)}")
                                    })
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(0.6f))

                // Okładka – przesuń w lewo/prawo, żeby zmienić utwór
                Artwork(
                    meta?.artworkUri,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
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
                        },
                    corner = 24.dp
                )

                Spacer(Modifier.weight(0.6f))

                // Tytuł + serduszko
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
                            listOfNotNull(meta?.artist?.toString(), meta?.albumTitle?.toString())
                                .joinToString(" — "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (song != null) {
                        IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (isFav) "Usuń z ulubionych" else "Dodaj do ulubionych",
                                tint = if (isFav) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Pasek postępu
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
                    Text(
                        formatDuration((dragPosition ?: position.toFloat()).toLong()),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(p.duration), style = MaterialTheme.typography.labelMedium)
                }

                Spacer(Modifier.height(8.dp))

                // Sterowanie
                val inactive = MaterialTheme.colorScheme.onSurfaceVariant
                val active = MaterialTheme.colorScheme.primary
                Row(
                    Modifier.fillMaxWidth(),
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

                Spacer(Modifier.weight(0.4f))

                // Dolny pasek: wyłącznik, prędkość, kolejka
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        Spacer(Modifier.width(8.dp))
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
    if (showAddToPlaylist && song != null) AddToPlaylistDialog(vm, listOf(song.id)) { showAddToPlaylist = false }
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
            itemsIndexed(queue, key = { _, e -> e.index }) { _, e ->
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
                        IconButton(onClick = { vm.removeFromQueue(e.index) }) {
                            Icon(Icons.Default.Close, "Usuń z kolejki")
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
                        if (endOfTrack) "Muzyka zatrzyma się po bieżącym utworze."
                        else "Muzyka zatrzyma się o ${
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(endsAt!!))
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
