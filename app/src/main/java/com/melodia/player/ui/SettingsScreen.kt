@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.melodia.player.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.melodia.player.MusicViewModel
import com.melodia.player.ui.theme.LocalUiSettings
import com.melodia.player.ui.theme.THEME_PRESETS
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MusicViewModel, nav: NavController) {
    val ui = LocalUiSettings.current
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateUi { it.copy(visualizer = granted) }
        if (!granted) toast(ctx, "Bez tego uprawnienia wizualizacja nie zadziała")
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Ustawienia", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ================= Wygląd =================
            Section("Wygląd") {
                Label("Motyw")
                Choices(listOf("Jak system", "Jasny", "Ciemny"), ui.themeMode) { i -> vm.updateUi { it.copy(themeMode = i) } }
                SwitchRow("Czarne tło (AMOLED)", "Oszczędza baterię na ekranach OLED", ui.amoled) { v ->
                    vm.updateUi { it.copy(amoled = v) }
                }
                Label("Kolory aplikacji")
                val sources = buildList {
                    add("Z tapety")
                    add("Z okładki albumu")
                    add("Wybrany motyw")
                }
                Choices(sources, ui.colorSource, disabled = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) setOf(0) else emptySet()) { i ->
                    vm.updateUi { it.copy(colorSource = i) }
                }
                Hint(
                    when (ui.colorSource) {
                        0 -> "Kolory Material You z tapety telefonu (Android 12+)."
                        1 -> "Cała aplikacja zmienia kolor pod okładkę aktualnego utworu."
                        else -> "Wybierz kolor poniżej."
                    }
                )
                Label(if (ui.colorSource == 1) "Kolor zapasowy (gdy brak okładki)" else "Motyw kolorystyczny")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    THEME_PRESETS.forEachIndexed { i, (name, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        3.dp,
                                        if (ui.themePreset == i) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable {
                                        vm.updateUi {
                                            it.copy(themePreset = i, colorSource = if (it.colorSource == 0) 2 else it.colorSource)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (ui.themePreset == i) Icon(Icons.Default.Check, null, tint = Color.White)
                            }
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ================= Ekran odtwarzania =================
            Section("Ekran odtwarzania") {
                Label("Styl")
                Choices(listOf("Klasyczny", "Winyl", "Pełny ekran"), ui.npStyle) { i -> vm.updateUi { it.copy(npStyle = i) } }
                if (ui.npStyle != 2) {
                    Label("Rozmiar okładki: ${(ui.artScale * 100).roundToInt()}%")
                    var scale by remember(ui.artScale) { mutableFloatStateOf(ui.artScale) }
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        onValueChangeFinished = { vm.updateUi { it.copy(artScale = scale) } },
                        valueRange = 0.6f..1f
                    )
                }
                SwitchRow("Animowane tło", "Płynące kolory, pulsują z basem przy wizualizacji", ui.animatedBg) { v ->
                    vm.updateUi { it.copy(animatedBg = v) }
                }
                SwitchRow(
                    "Wizualizacja muzyki",
                    "Spectrum analyzer / fala dźwiękowa. Android wymaga zgody na „mikrofon” – nic nie jest nagrywane.",
                    ui.visualizer
                ) { v ->
                    if (v) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else vm.updateUi { it.copy(visualizer = false) }
                }
                if (ui.visualizer) {
                    Choices(listOf("Słupki (spectrum)", "Fala"), ui.visualizerStyle) { i ->
                        vm.updateUi { it.copy(visualizerStyle = i) }
                    }
                }
            }

            // ================= Listy =================
            Section("Listy utworów") {
                Choices(listOf("Z okładkami", "Kompaktowe"), ui.listStyle) { i -> vm.updateUi { it.copy(listStyle = i) } }
            }

            // ================= Dźwięk =================
            Section("Dźwięk") {
                var balance by remember(ui.balance) { mutableFloatStateOf(ui.balance) }
                Label(
                    "Balans: " + when {
                        abs(balance) < 0.02f -> "środek"
                        balance < 0 -> "L ${(-balance * 100).roundToInt()}%"
                        else -> "P ${(balance * 100).roundToInt()}%"
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L")
                    Slider(
                        value = balance,
                        onValueChange = { balance = if (abs(it) < 0.04f) 0f else it },
                        onValueChangeFinished = { vm.updateUi { it.copy(balance = balance) } },
                        valueRange = -1f..1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("P")
                }

                Label("ReplayGain (wyrównanie głośności)")
                Choices(listOf("Wyłączony", "Utwór", "Album"), ui.replayGain) { i -> vm.updateUi { it.copy(replayGain = i) } }
                Hint("Korzysta z tagów REPLAYGAIN w plikach. Utwory bez tagów grają bez zmian.")

                var fade by remember(ui.crossfadeSec) { mutableFloatStateOf(ui.crossfadeSec.toFloat()) }
                Label("Płynne przejścia: " + if (fade.roundToInt() == 0) "wyłączone" else "${fade.roundToInt()} s")
                Slider(
                    value = fade,
                    onValueChange = { fade = it },
                    onValueChangeFinished = { vm.updateUi { it.copy(crossfadeSec = fade.roundToInt()) } },
                    valueRange = 0f..10f,
                    steps = 9
                )
                Hint("Wycisza końcówkę utworu i łagodnie wprowadza następny.")

                NavRow(Icons.Default.Equalizer, "Equalizer, basy, soprany, presety") { nav.navigate("eq") }
            }

            // ================= Inne =================
            Section("Statystyki") {
                NavRow(Icons.Default.BarChart, "Statystyki słuchania") { nav.navigate("stats") }
                NavRow(Icons.Default.DeleteSweep, "Wyczyść statystyki i historię") { confirmClear = true }
            }

            Text(
                "SztosiQ 1.2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            "Wyczyścić statystyki?",
            "Licznik odtworzeń, historia i czas słuchania zostaną usunięte. Playlisty i ulubione zostają.",
            "Wyczyść",
            onConfirm = { vm.clearStats() }
        ) { confirmClear = false }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Choices(
    options: List<String>,
    selected: Int,
    disabled: Set<Int> = emptySet(),
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = { Text(label) },
                enabled = i !in disabled,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}
