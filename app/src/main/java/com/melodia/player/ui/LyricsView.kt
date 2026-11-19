package com.melodia.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.melodia.player.data.Lyrics

/** Tekst piosenki – zsynchronizowany (LRC) przewija się sam, kliknięcie linii przewija utwór. */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    positionMs: Long,
    hasSong: Boolean,
    onSeek: (Long) -> Unit,
    onImport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Brak tekstu dla tego utworu", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dodaj plik .lrc (tekst z czasami – przewija się sam) albo wklej zwykły tekst.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                if (hasSong) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onImport) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Plik LRC")
                        }
                        OutlinedButton(onClick = onEdit) {
                            Icon(Icons.Default.ContentPaste, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Wklej tekst")
                        }
                    }
                }
            }
            return@Box
        }

        val lines = lyrics.lines
        val current = if (lyrics.synced) lines.indexOfLast { it.timeMs <= positionMs } else -1
        val listState = rememberLazyListState()
        LaunchedEffect(current) {
            if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp)
        ) {
            itemsIndexed(lines) { i, line ->
                val active = i == current
                val past = lyrics.synced && i < current
                Text(
                    line.text.ifBlank { "♪" },
                    style = if (lyrics.synced) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        active -> MaterialTheme.colorScheme.primary
                        past -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        lyrics.synced -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = lyrics.synced) { onSeek(line.timeMs) }
                        .padding(vertical = 6.dp)
                )
            }
        }

        var menu by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opcje tekstu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Wczytaj plik LRC") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    onClick = { menu = false; onImport() })
                DropdownMenuItem(
                    text = { Text("Edytuj tekst") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("Usuń tekst") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun LyricsEditDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tekst piosenki") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                placeholder = { Text("Wklej tekst. Format LRC [mm:ss.xx] będzie zsynchronizowany z muzyką.") },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text); onDismiss() }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
