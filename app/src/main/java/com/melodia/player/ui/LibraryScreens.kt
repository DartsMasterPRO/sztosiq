@file:OptIn(ExperimentalMaterial3Api::class)

package com.melodia.player.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.melodia.player.MusicViewModel
import com.melodia.player.data.Album
import com.melodia.player.data.Song
import com.melodia.player.data.SongSort
import com.melodia.player.data.normalizeForSearch
import com.melodia.player.data.songsLabel

@Composable
fun LibraryScreen(vm: MusicViewModel, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Utwory", "Albumy", "Wykonawcy", "Gatunki", "Foldery")

    val ctx = LocalContext.current
    val loading by vm.loading.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Biblioteka", actions = {
            IconButton(enabled = !loading, onClick = {
                vm.refreshLibrary { total, diff ->
                    toast(
                        ctx,
                        when {
                            diff > 0 -> "Znaleziono ${songsLabel(total)} (nowe: $diff)"
                            diff < 0 -> "Znaleziono ${songsLabel(total)} (usunięte: ${-diff})"
                            else -> "Biblioteka aktualna – ${songsLabel(total)}"
                        }
                    )
                }
            }) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                else Icon(Icons.Default.Refresh, "Odśwież bibliotekę")
            }
            IconButton(onClick = { nav.navigate("stats") }) { Icon(Icons.Default.BarChart, "Statystyki") }
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Ustawienia") }
        })
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        when (tab) {
            0 -> SongsTab(vm, nav)
            1 -> AlbumsTab(vm, nav)
            2 -> ArtistsTab(vm, nav)
            3 -> GenresTab(vm, nav)
            else -> FoldersTab(vm, nav)
        }
    }
}

@Composable
private fun GenresTab(vm: MusicViewModel, nav: NavController) {
    val genres by vm.genres.collectAsStateWithLifecycle()
    if (genres.isEmpty()) {
        EmptyState("Brak informacji o gatunkach.\nGatunek jest odczytywany z tagów plików muzycznych.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(genres, key = { it.name }) { g ->
            ListItem(
                modifier = Modifier.clickable { nav.navigate("genre/${Uri.encode(g.name)}") },
                leadingContent = { CircleIcon(Icons.Default.Category) },
                headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${songsLabel(g.songs.size)} • ${g.songs.map { it.artist }.distinct().size} wyk.")
                }
            )
        }
    }
}

@Composable
fun GenreScreen(vm: MusicViewModel, nav: NavController, name: String) {
    val genres by vm.genres.collectAsStateWithLifecycle()
    val genre = genres.firstOrNull { it.name == name }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(name, onBack = { nav.popBackStack() })
        if (genre == null) return@Column
        SongList(genre.songs, vm, nav)
    }
}

@Composable
private fun SongsTab(vm: MusicViewModel, nav: NavController) {
    val songs by vm.sortedSongs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sort by vm.songSort.collectAsStateWithLifecycle()
    val current by vm.currentId.collectAsStateWithLifecycle()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var sortOpen by remember { mutableStateOf(false) }

    if (loading && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (songs.isEmpty()) {
        EmptyState("Nie znaleziono muzyki na urządzeniu.\nWgraj pliki MP3/FLAC do folderu Music i odśwież.")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = { vm.playSongs(songs, shuffle = true) }) {
                    Icon(Icons.Default.Shuffle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Losowo (${songs.size})")
                }
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, null)
                        Spacer(Modifier.width(6.dp))
                        Text(sort.label)
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        SongSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = { vm.setSort(s); sortOpen = false },
                                trailingIcon = { if (s == sort) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                isCurrent = s.id.toString() == current,
                onClick = { vm.playSongs(songs, i) },
                onMore = { menuSong = s }
            )
        }
    }
    menuSong?.let { SongMenuSheet(it, vm, nav, onDismiss = { menuSong = null }) }
}

@Composable
private fun AlbumsTab(vm: MusicViewModel, nav: NavController) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    if (albums.isEmpty()) { EmptyState("Brak albumów"); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.id }) { a -> AlbumCard(a) { nav.navigate("album/${a.id}") } }
    }
}

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Artwork(album.artUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
        Spacer(Modifier.height(6.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistsTab(vm: MusicViewModel, nav: NavController) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    if (artists.isEmpty()) { EmptyState("Brak wykonawców"); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { a ->
            ListItem(
                modifier = Modifier.clickable { nav.navigate("artist/${Uri.encode(a.name)}") },
                leadingContent = { Artwork(a.artUri, Modifier.size(48.dp), corner = 24.dp) },
                headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${a.albumCount} alb. • ${songsLabel(a.songs.size)}") }
            )
        }
    }
}

@Composable
private fun FoldersTab(vm: MusicViewModel, nav: NavController) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    if (folders.isEmpty()) { EmptyState("Brak folderów"); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { f ->
            ListItem(
                modifier = Modifier.clickable { nav.navigate("folder/${Uri.encode(f.path)}") },
                leadingContent = { CircleIcon(Icons.Default.Folder) },
                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${songsLabel(f.songs.size)} • ${f.path}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}

// ================= Ekrany szczegółów =================

@Composable
fun AlbumScreen(vm: MusicViewModel, nav: NavController, albumId: Long) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.id == albumId }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(album?.title ?: "Album", onBack = { nav.popBackStack() })
        if (album == null) return@Column
        SongList(
            songs = album.songs,
            vm = vm,
            nav = nav,
            trackNumbers = true,
            header = {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(album.artUri, Modifier.size(140.dp), corner = 16.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(album.title, style = MaterialTheme.typography.titleLarge, maxLines = 3)
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { nav.navigate("artist/${Uri.encode(album.artist)}") }
                            )
                            val year = if (album.year > 0) "${album.year} • " else ""
                            Text(
                                "$year${formatDuration(album.duration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ArtistScreen(vm: MusicViewModel, nav: NavController, name: String) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.name == name }
    val artistAlbums = remember(albums, name) { albums.filter { a -> a.songs.any { it.artist == name } } }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(name, onBack = { nav.popBackStack() })
        if (artist == null) return@Column
        SongList(
            songs = artist.songs,
            vm = vm,
            nav = nav,
            header = {
                if (artistAlbums.isNotEmpty()) {
                    item {
                        Text(
                            "Albumy",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(artistAlbums, key = { it.id }) { a ->
                                AlbumCard(a, Modifier.width(130.dp)) { nav.navigate("album/${a.id}") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        )
    }
}

@Composable
fun FolderScreen(vm: MusicViewModel, nav: NavController, path: String) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folder = folders.firstOrNull { it.path == path }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(folder?.name ?: "Folder", onBack = { nav.popBackStack() })
        if (folder == null) return@Column
        SongList(folder.songs, vm, nav)
    }
}

// ================= Wyszukiwanie =================

@Composable
fun SearchScreen(vm: MusicViewModel, nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()

    val q = query.trim()
    val songResults = remember(q, songs) { vm.searchSongs(q) }
    val albumResults = remember(q, albums) {
        val n = q.normalizeForSearch()
        if (n.isEmpty()) emptyList() else albums.filter { it.title.normalizeForSearch().contains(n) || it.artist.normalizeForSearch().contains(n) }
    }
    val artistResults = remember(q, artists) {
        val n = q.normalizeForSearch()
        if (n.isEmpty()) emptyList() else artists.filter { it.name.normalizeForSearch().contains(n) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Utwory, albumy, wykonawcy…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Wyczyść") }
            },
            singleLine = true,
            shape = CircleShape,
        )
        if (q.isEmpty()) {
            EmptyState("Wpisz frazę, żeby wyszukać.\nPolskie znaki nie są wymagane.")
            return@Column
        }
        if (songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty()) {
            EmptyState("Brak wyników dla „$q”")
            return@Column
        }
        SongList(
            songs = songResults,
            vm = vm,
            nav = nav,
            showPlayButtons = false,
            header = {
                if (artistResults.isNotEmpty()) {
                    item { SectionTitle("Wykonawcy") }
                    items(artistResults.take(5), key = { "artist-${it.name}" }) { a ->
                        ListItem(
                            modifier = Modifier.clickable { nav.navigate("artist/${Uri.encode(a.name)}") },
                            leadingContent = { Artwork(a.artUri, Modifier.size(48.dp), corner = 24.dp) },
                            headlineContent = { Text(a.name) },
                            supportingContent = { Text(songsLabel(a.songs.size)) }
                        )
                    }
                }
                if (albumResults.isNotEmpty()) {
                    item {
                        SectionTitle("Albumy")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albumResults, key = { it.id }) { a ->
                                AlbumCard(a, Modifier.width(130.dp)) { nav.navigate("album/${a.id}") }
                            }
                        }
                    }
                }
                if (songResults.isNotEmpty()) item { SectionTitle("Utwory (${songResults.size})") }
            }
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}
