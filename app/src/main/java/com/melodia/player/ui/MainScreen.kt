package com.melodia.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.melodia.player.MusicViewModel

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun MelodiaRoot(vm: MusicViewModel) {
    val ctx = LocalContext.current
    fun hasPermission() =
        ContextCompat.checkSelfPermission(ctx, audioPermission()) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermission()
    }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasPermission() }

    LaunchedEffect(granted) {
        if (granted) vm.refreshLibrary() else launcher.launch(permissions)
    }

    if (granted) {
        MainScaffold(vm)
    } else {
        PermissionScreen(
            onRequest = { launcher.launch(permissions) },
            onOpenSettings = {
                ctx.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", ctx.packageName, null)
                    )
                )
            }
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.LibraryMusic, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(
                "SztosiQ potrzebuje dostępu do Twojej muzyki",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Zezwól na dostęp do plików audio, żeby aplikacja mogła znaleźć utwory na telefonie.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) { Text("Zezwól") }
            TextButton(onClick = onOpenSettings) { Text("Otwórz ustawienia aplikacji") }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(vm: MusicViewModel) {
    val nav = rememberNavController()
    val player by vm.player.collectAsStateWithLifecycle()
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(player.hasItems) { if (!player.hasItems) showNowPlaying = false }

    val tabs = remember {
        listOf(
            Tab("library", "Biblioteka", Icons.Default.LibraryMusic),
            Tab("playlists", "Playlisty", Icons.AutoMirrored.Filled.QueueMusic),
            Tab("search", "Szukaj", Icons.Default.Search),
            Tab("eq", "Equalizer", Icons.Default.Equalizer),
        )
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column {
                    if (player.hasItems) MiniPlayer(vm) { showNowPlaying = true }
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = "library", modifier = Modifier.padding(padding)) {
                composable("library") { LibraryScreen(vm, nav) }
                composable("playlists") { PlaylistsScreen(vm, nav) }
                composable("search") { SearchScreen(vm, nav) }
                composable("eq") { EqualizerScreen(vm, nav) }
                composable(
                    "album/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { e -> AlbumScreen(vm, nav, e.arguments?.getLong("id") ?: 0L) }
                composable("artist/{name}") { e ->
                    ArtistScreen(vm, nav, e.arguments?.getString("name").orEmpty())
                }
                composable("folder/{path}") { e ->
                    FolderScreen(vm, nav, e.arguments?.getString("path").orEmpty())
                }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { e -> PlaylistScreen(vm, nav, e.arguments?.getLong("id") ?: 0L) }
                composable("genre/{name}") { e ->
                    GenreScreen(vm, nav, e.arguments?.getString("name").orEmpty())
                }
                composable("settings") { SettingsScreen(vm, nav) }
                composable("stats") { StatsScreen(vm, nav) }
                composable("smart/{kind}") { e ->
                    SmartPlaylistScreen(vm, nav, e.arguments?.getString("kind").orEmpty())
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(
                vm = vm,
                nav = nav,
                onClose = { showNowPlaying = false },
            )
        }
    }
}

@Composable
private fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit) {
    val p by vm.player.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val meta = p.item?.mediaMetadata

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (p.duration > 0) (pos.toFloat() / p.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(meta?.artworkUri, Modifier.size(44.dp), corner = 6.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        meta?.title?.toString().orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        meta?.artist?.toString().orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.togglePlay() }) {
                    Icon(
                        if (p.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (p.isPlaying) "Pauza" else "Odtwórz"
                    )
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Następny") }
            }
        }
    }
}
