@file:OptIn(ExperimentalMaterial3Api::class)

package com.melodia.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import com.melodia.player.MusicViewModel
import com.melodia.player.data.Video
import com.melodia.player.data.VideoSort
import com.melodia.player.video.VideoActivity

private fun videoPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun VideosScreen(vm: MusicViewModel) {
    val ctx = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(ctx, videoPermission()) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = granted() }

    val videos by vm.videos.collectAsStateWithLifecycle()
    val loading by vm.videosLoading.collectAsStateWithLifecycle()
    val positions by vm.videoPositions.collectAsStateWithLifecycle()
    val sort by vm.videoSort.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOpen by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) { if (hasPermission) vm.loadVideos() }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Filmy", actions = {
            if (hasPermission) {
                Box {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sortuj") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        VideoSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = { vm.setVideoSort(s); sortOpen = false },
                                trailingIcon = { if (s == sort) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
                IconButton(onClick = { vm.loadVideos() }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    else Icon(Icons.Default.Refresh, "Odśwież")
                }
            }
        })

        if (!hasPermission) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Movie, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Zezwól na dostęp do filmów, żeby zobaczyć je tutaj.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(videoPermission()) }) { Text("Zezwól") }
            }
            return@Column
        }

        val folders = remember(videos) {
            videos.groupBy { it.folder }.map { (path, list) -> Triple(path, list.first().folderName, list.size) }
                .sortedByDescending { it.third }
        }
        val shown = remember(videos, folder) { if (folder == null) videos else videos.filter { it.folder == folder } }

        if (videos.isEmpty()) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else EmptyState("Nie znaleziono filmów na urządzeniu.")
            return@Column
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("Wszystkie (${videos.size})") })
            folders.forEach { (path, name, count) ->
                FilterChip(selected = folder == path, onClick = { folder = path }, label = { Text("$name ($count)") })
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(shown, key = { _, v -> v.id }) { i, v ->
                VideoCard(v, positions[v.mediaId]) {
                    VideoActivity.open(ctx, shown.map { it.id }.toLongArray(), i)
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: Video, resumeAt: Long?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Default.Movie, null,
                Modifier.align(Alignment.Center).size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            AsyncImage(
                model = video.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                formatDuration(video.duration),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
            video.quality?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (resumeAt != null && video.duration > 0) {
                Box(Modifier.align(Alignment.Center).size(40.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                }
                LinearProgressIndicator(
                    progress = { (resumeAt.toFloat() / video.duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                video.folderName,
                video.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(ctx, it) }
            ).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
