@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.melodia.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.melodia.player.MusicViewModel
import com.melodia.player.playback.EqPresets
import com.melodia.player.ui.theme.LocalUiSettings
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(vm: MusicViewModel, nav: NavController) {
    val st by vm.eqState.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val ui = LocalUiSettings.current
    val ctx = LocalContext.current
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Equalizer", actions = {
            if (st.available) {
                IconButton(onClick = { saving = true }) { Icon(Icons.Default.Save, "Zapisz jako własny preset") }
                IconButton(onClick = { vm.resetEq() }) { Icon(Icons.Default.RestartAlt, "Resetuj") }
            }
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Ustawienia dźwięku") }
        })

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!st.available) {
                EmptyState("Equalizer jest niedostępny.\nUruchom dowolny utwór – jeśli dalej nie działa, telefon nie udostępnia efektów audio.")
            } else {
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Efekty dźwięku", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (st.enabled) "Włączone – ${st.bands.size} pasm" else "Wyłączone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = st.enabled, onCheckedChange = { vm.setEqEnabled(it) })
                    }
                }

                // ---- Presety ----
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Presety", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = st.presetName.isEmpty(),
                            onClick = {},
                            label = { Text("Własne ustawienia") },
                            enabled = st.enabled
                        )
                        st.customPresets.forEach { c ->
                            FilterChip(
                                selected = st.presetName == c.name,
                                onClick = { vm.applyCustomPreset(c) },
                                label = { Text(c.name) },
                                leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) },
                                enabled = st.enabled
                            )
                        }
                        EqPresets.builtIn.forEach { (name, _) ->
                            FilterChip(
                                selected = st.presetName == name,
                                onClick = { vm.applyBuiltInPreset(name) },
                                label = { Text(name) },
                                enabled = st.enabled
                            )
                        }
                    }
                    st.customPresets.firstOrNull { it.name == st.presetName }?.let { c ->
                        TextButton(onClick = { vm.deleteCustomPreset(c.name) }) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Usuń preset „${c.name}”")
                        }
                    }
                }

                // ---- Pasma ----
                Card {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 4.dp)) {
                            st.bands.forEach { band ->
                                Column(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("%+.0f".format(band.level / 100f), style = MaterialTheme.typography.labelSmall)
                                    VerticalSlider(
                                        value = band.level.toFloat(),
                                        onValueChange = { vm.setEqBand(band.index, it.roundToInt()) },
                                        valueRange = st.minLevel.toFloat()..st.maxLevel.toFloat(),
                                        enabled = st.enabled,
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )
                                    Text(freqLabel(band.centerHz), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text(
                            "dB  (zakres ${st.minLevel / 100} … +${st.maxLevel / 100})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                }

                // ---- Tony / efekty ----
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Barwa i efekty", style = MaterialTheme.typography.titleSmall)
                        if (st.bassSupported) {
                            EffectSlider("Basy (bass boost)", st.bassBoost, 0, 1000, st.enabled) { vm.setBassBoost(it) }
                        }
                        // Soprany – własny filtr aplikacji, działa na każdym telefonie
                        var treble by remember(ui.trebleDb) { mutableFloatStateOf(ui.trebleDb) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Soprany (wysokie tony)", modifier = Modifier.weight(1f))
                            Text("%+.1f dB".format(treble), style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = treble,
                            onValueChange = { treble = (it * 2).roundToInt() / 2f },
                            onValueChangeFinished = { vm.setTreble(treble) },
                            valueRange = -12f..12f,
                            enabled = st.enabled
                        )
                        if (st.virtSupported) {
                            EffectSlider("Dźwięk przestrzenny", st.virtualizer, 0, 1000, st.enabled) { vm.setVirtualizer(it) }
                        }
                        if (st.loudSupported) {
                            EffectSlider(
                                "Wzmocnienie głośności", st.loudness, 0, 1500, st.enabled,
                                suffix = { "+%.1f dB".format(it / 100f) }
                            ) { vm.setLoudness(it) }
                        }
                    }
                }
            }

            // ---- Prędkość (działa niezależnie od equalizera) ----
            Card {
                Column(Modifier.padding(16.dp)) {
                    var speed by remember(player.speed) { mutableFloatStateOf(player.speed) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Prędkość odtwarzania", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("%.2fx".format(speed))
                    }
                    Slider(
                        value = speed,
                        onValueChange = { speed = (it * 20).roundToInt() / 20f },
                        onValueChangeFinished = { vm.setSpeed(speed) },
                        valueRange = 0.5f..2f,
                        steps = 29
                    )
                    TextButton(onClick = { vm.setSpeed(1f) }, enabled = speed != 1f) { Text("Przywróć 1x") }
                }
            }
            Text(
                "Balans L/P, ReplayGain i płynne przejścia znajdziesz w Ustawieniach (ikona zębatki u góry).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (saving) {
        TextInputDialog("Zapisz preset", "", "Zapisz", onConfirm = {
            vm.saveCustomPreset(it)
            toast(ctx, "Zapisano preset „$it”")
        }) { saving = false }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    suffix: (Int) -> String = { "${((it - min) * 100f / (max - min)).roundToInt()}%" },
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(suffix(value), style = MaterialTheme.typography.labelMedium)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        enabled = enabled
    )
}

/** Slider obrócony o 90° (minimum na dole). */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

private fun freqLabel(hz: Int): String = when {
    hz >= 1000 -> {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else "%.1fk".format(k)
    }
    else -> "$hz"
}
