@file:OptIn(ExperimentalMaterial3Api::class)

package com.melodia.player.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.melodia.player.MusicViewModel
import com.melodia.player.data.Song
import com.melodia.player.data.songsLabel

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun toast(ctx: android.content.Context, text: String) =
    Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.45f)
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SimpleTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
            }
        },
        actions = actions,
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    leadingNumber: Int? = null,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (leadingNumber != null) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (isCurrent) Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                    else Text(
                        if (leadingNumber > 0) "$leadingNumber" else "–",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Artwork(song.artUri, Modifier.size(48.dp))
            }
        },
        headlineContent = {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            )
        },
        supportingContent = {
            Text(
                "${song.artist} • ${formatDuration(song.duration)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Więcej") }
        },
    )
}

@Composable
fun PlayButtonsRow(count: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Odtwórz")
        }
        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Shuffle, null)
            Spacer(Modifier.width(8.dp))
            Text("Losowo")
        }
    }
    Text(
        songsLabel(count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

data class ExtraAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Lista utworów z przyciskami odtwarzania i menu kontekstowym. */
@Composable
fun SongList(
    songs: List<Song>,
    vm: MusicViewModel,
    nav: NavController,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
    showPlayButtons: Boolean = true,
    trackNumbers: Boolean = false,
    extras: (Int, Song) -> List<ExtraAction> = { _, _ -> emptyList() },
) {
    val current by vm.currentId.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<Pair<Int, Song>?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        header?.invoke(this)
        if (showPlayButtons && songs.isNotEmpty()) {
            item {
                PlayButtonsRow(
                    count = songs.size,
                    onPlay = { vm.playSongs(songs, 0, shuffle = false) },
                    onShuffle = { vm.playSongs(songs, shuffle = true) }
                )
            }
        }
        if (songs.isEmpty()) item { EmptyState("Brak utworów") }
        itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
            SongRow(
                song = s,
                isCurrent = s.id.toString() == current,
                onClick = { vm.playSongs(songs, i) },
                onMore = { menu = i to s },
                leadingNumber = if (trackNumbers) s.track else null,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    menu?.let { (i, s) ->
        SongMenuSheet(s, vm, nav, onDismiss = { menu = null }, extras = extras(i, s))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SongMenuSheet(
    song: Song,
    vm: MusicViewModel,
    nav: NavController,
    onDismiss: () -> Unit,
    extras: List<ExtraAction> = emptyList(),
    beforeNavigate: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val favorites by vm.favoriteSet.collectAsStateWithLifecycle()
    val isFav = song.id in favorites
    var showAddToPlaylist by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            leadingContent = { Artwork(song.artUri, Modifier.size(52.dp)) },
            headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("${song.artist} — ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider()
        Column(Modifier.navigationBarsPadding()) {
            MenuRow(Icons.Default.PlayArrow, "Odtwórz") {
                vm.playSongs(listOf(song)); onDismiss()
            }
            MenuRow(Icons.Default.SkipNext, "Odtwórz jako następny") {
                vm.playNext(listOf(song)); toast(ctx, "Zagra jako następny"); onDismiss()
            }
            MenuRow(Icons.AutoMirrored.Filled.QueueMusic, "Dodaj do kolejki") {
                vm.addToQueue(listOf(song)); toast(ctx, "Dodano do kolejki"); onDismiss()
            }
            MenuRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Dodaj do playlisty") {
                showAddToPlaylist = true
            }
            MenuRow(
                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (isFav) "Usuń z ulubionych" else "Dodaj do ulubionych"
            ) {
                vm.toggleFavorite(song.id); onDismiss()
            }
            MenuRow(Icons.Default.Album, "Przejdź do albumu") {
                onDismiss(); beforeNavigate(); nav.navigate("album/${song.albumId}")
            }
            MenuRow(Icons.Default.Person, "Przejdź do wykonawcy") {
                onDismiss(); beforeNavigate(); nav.navigate("artist/${Uri.encode(song.artist)}")
            }
            extras.forEach { a ->
                MenuRow(a.icon, a.label) { a.onClick(); onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(vm, listOf(song.id)) {
            showAddToPlaylist = false
            onDismiss()
        }
    }
}

@Composable
fun AddToPlaylistDialog(vm: MusicViewModel, songIds: List<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj do playlisty") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nowa playlista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                vm.createPlaylist(newName.trim(), songIds)
                                toast(ctx, "Utworzono „${newName.trim()}”")
                                onDismiss()
                            }
                        ) { Icon(Icons.Default.Add, "Utwórz") }
                    }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            modifier = Modifier.clickable {
                                vm.addToPlaylist(p.id, songIds)
                                toast(ctx, "Dodano do „${p.name}”")
                                onDismiss()
                            },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(songsLabel(p.songIds.size)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nazwa") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()); onDismiss() }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
fun CircleIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
