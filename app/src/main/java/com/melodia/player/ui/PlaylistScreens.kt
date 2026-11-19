@file:OptIn(ExperimentalMaterial3Api::class)

package com.melodia.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.melodia.player.MusicViewModel
import com.melodia.player.data.Playlist
import com.melodia.player.data.songsLabel

private data class Smart(val kind: String, val title: String, val icon: ImageVector)

private val SMART_LISTS = listOf(
    Smart(MusicViewModel.SMART_FAVORITES, "Ulubione", Icons.Default.Favorite),
    Smart(MusicViewModel.SMART_ADDED, "Ostatnio dodane", Icons.Default.NewReleases),
    Smart(MusicViewModel.SMART_MOST, "Najczęściej odtwarzane", Icons.Default.TrendingUp),
    Smart(MusicViewModel.SMART_RECENT, "Ostatnio odtwarzane", Icons.Default.History),
    Smart(MusicViewModel.SMART_UNPLAYED, "Jeszcze nie słuchane", Icons.Default.MusicOff),
)

@Composable
fun PlaylistsScreen(vm: MusicViewModel, nav: NavController) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    val ctx = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlaylist(uri) { name, count ->
            toast(ctx, if (count > 0) "Zaimportowano „$name” ($count utw.)" else "Nie znaleziono pasujących utworów")
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Playlisty", actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FileDownload, "Importuj playlistę (M3U)")
            }
            IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Nowa playlista") }
        })
        LazyColumn(Modifier.fillMaxSize()) {
            items(SMART_LISTS, key = { "smart-${it.kind}" }) { s ->
                val songs by vm.smartList(s.kind).collectAsStateWithLifecycle()
                ListItem(
                    modifier = Modifier.clickable { nav.navigate("smart/${s.kind}") },
                    leadingContent = { CircleIcon(s.icon) },
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(songsLabel(songs.size)) }
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Twoje playlisty")
            }
            if (playlists.isEmpty()) {
                item {
                    EmptyState("Nie masz jeszcze playlist.\nUtwórz pierwszą przyciskiem + u góry albo z menu utworu.")
                }
            }
            items(playlists, key = { it.id }) { p ->
                var menuOpen by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier.clickable { nav.navigate("playlist/${p.id}") },
                    leadingContent = { CircleIcon(Icons.AutoMirrored.Filled.PlaylistPlay) },
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(songsLabel(p.songIds.size)) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Opcje") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Zmień nazwę") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuOpen = false; renaming = p }
                                )
                                DropdownMenuItem(
                                    text = { Text("Usuń") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { menuOpen = false; deleting = p }
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    if (creating) {
        TextInputDialog("Nowa playlista", "", "Utwórz", onConfirm = { vm.createPlaylist(it) }) { creating = false }
    }
    renaming?.let { p ->
        TextInputDialog("Zmień nazwę", p.name, "Zapisz", onConfirm = { vm.renamePlaylist(p.id, it) }) { renaming = null }
    }
    deleting?.let { p ->
        ConfirmDialog(
            "Usunąć playlistę?",
            "Playlista „${p.name}” zostanie usunięta. Pliki muzyczne zostają na telefonie.",
            "Usuń",
            onConfirm = { vm.deletePlaylist(p.id) }
        ) { deleting = null }
    }
}

@Composable
fun PlaylistScreen(vm: MusicViewModel, nav: NavController, playlistId: Long) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val songMap by vm.songMap.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // Pary (pozycja w playliście, utwór) – pomijamy pliki usunięte z telefonu.
    val entries = remember(playlist, songMap) {
        playlist?.songIds?.mapIndexedNotNull { i, id -> songMap[id]?.let { i to it } } ?: emptyList()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null && playlist != null) {
            vm.exportPlaylist(entries.map { it.second }, playlist.name, uri) { ok ->
                toast(ctx, if (ok) "Zapisano playlistę" else "Nie udało się zapisać pliku")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(playlist?.name ?: "Playlista", onBack = { nav.popBackStack() }, actions = {
            if (playlist != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Opcje") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Zmień nazwę") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuOpen = false; renaming = true })
                        DropdownMenuItem(
                            text = { Text("Eksportuj (M3U)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = { menuOpen = false; exportLauncher.launch("${playlist.name}.m3u") })
                        DropdownMenuItem(
                            text = { Text("Usuń playlistę") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuOpen = false; deleting = true })
                    }
                }
            }
        })
        if (playlist == null) return@Column
        SongList(
            songs = entries.map { it.second },
            vm = vm,
            nav = nav,
            extras = { i, _ ->
                val pos = entries[i].first
                listOf(
                    ExtraAction("Usuń z playlisty", Icons.Default.RemoveCircleOutline) {
                        vm.removeFromPlaylist(playlist.id, pos)
                    },
                    ExtraAction("Przesuń wyżej", Icons.Default.ArrowUpward) {
                        if (i > 0) vm.movePlaylistItem(playlist.id, pos, entries[i - 1].first)
                    },
                    ExtraAction("Przesuń niżej", Icons.Default.ArrowDownward) {
                        if (i < entries.lastIndex) vm.movePlaylistItem(playlist.id, pos, entries[i + 1].first)
                    },
                )
            }
        )
    }

    if (renaming && playlist != null) {
        TextInputDialog("Zmień nazwę", playlist.name, "Zapisz", onConfirm = { vm.renamePlaylist(playlist.id, it) }) {
            renaming = false
        }
    }
    if (deleting && playlist != null) {
        ConfirmDialog("Usunąć playlistę?", "Playlista „${playlist.name}” zostanie usunięta.", "Usuń", onConfirm = {
            vm.deletePlaylist(playlist.id)
            nav.popBackStack()
        }) { deleting = false }
    }
}

@Composable
fun SmartPlaylistScreen(vm: MusicViewModel, nav: NavController, kind: String) {
    val smart = SMART_LISTS.firstOrNull { it.kind == kind } ?: SMART_LISTS.last()
    val songs by vm.smartList(smart.kind).collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(smart.title, onBack = { nav.popBackStack() })
        if (songs.isEmpty()) {
            EmptyState(
                when (smart.kind) {
                    MusicViewModel.SMART_FAVORITES -> "Dodaj utwory do ulubionych serduszkiem w odtwarzaczu albo z menu utworu."
                    else -> "Na razie pusto — posłuchaj czegoś!"
                }
            )
        } else {
            SongList(songs, vm, nav)
        }
    }
}
