package com.melodia.player.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.melodia.player.MusicViewModel

private fun hours(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h} h ${m} min" else "$m min"
}

@Composable
fun StatsScreen(vm: MusicViewModel, nav: NavController) {
    val stats by vm.stats.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Statystyki słuchania", onBack = { nav.popBackStack() })
        if (stats.totalPlays == 0 && stats.totalMs == 0L) {
            EmptyState("Jeszcze nic tu nie ma.\nPosłuchaj trochę muzyki – statystyki zbierają się same.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Łącznie", hours(stats.totalMs), Modifier.weight(1f))
                    StatCard("Ten miesiąc", hours(stats.monthMs), Modifier.weight(1f))
                    StatCard("Odtworzeń", "${stats.totalPlays}", Modifier.weight(1f))
                }
            }

            if (stats.topArtists.isNotEmpty()) {
                item { SectionTitle("Najczęściej słuchani wykonawcy") }
                val max = stats.topArtists.first().second.coerceAtLeast(1)
                itemsIndexed(stats.topArtists, key = { _, a -> "a-${a.first}" }) { i, (name, plays) ->
                    BarRow(
                        rank = i + 1,
                        title = name,
                        value = "$plays odtw.",
                        fraction = plays / max.toFloat(),
                        onClick = { nav.navigate("artist/${Uri.encode(name)}") }
                    )
                }
            }

            if (stats.topSongs.isNotEmpty()) {
                item { SectionTitle("Ulubione utwory") }
                val max = stats.topSongs.first().second.coerceAtLeast(1)
                itemsIndexed(stats.topSongs, key = { _, s -> "s-${s.first.id}" }) { i, (song, plays) ->
                    BarRow(
                        rank = i + 1,
                        title = song.title,
                        subtitle = song.artist,
                        value = "$plays×",
                        fraction = plays / max.toFloat(),
                        onClick = { vm.playSongs(listOf(song)) }
                    )
                }
            }

            if (stats.months.isNotEmpty()) {
                item { SectionTitle("Podsumowania miesięczne") }
                items(stats.months, key = { "m-${it.key}" }) { m ->
                    ListItem(
                        headlineContent = { Text(m.label, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    "${m.plays} sesji",
                                    m.topArtist?.let { "najczęściej: $it" }
                                ).joinToString(" • "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = { Text(hours(m.ms), style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }

            if (stats.history.isNotEmpty()) {
                item { SectionTitle("Historia") }
                items(stats.history, key = { "h-${it.second.time}-${it.first.id}" }) { (song, e) ->
                    ListItem(
                        modifier = Modifier.clickable { vm.playSongs(listOf(song)) },
                        leadingContent = { Artwork(song.artUri, Modifier.size(44.dp), corner = 6.dp) },
                        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                "${song.artist} • " + DateUtils.getRelativeTimeSpanString(
                                    e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = { Text(formatDuration(e.ms), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun BarRow(
    rank: Int,
    title: String,
    value: String,
    fraction: Float,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(value, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
