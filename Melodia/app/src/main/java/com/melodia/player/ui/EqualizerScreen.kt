@file:OptIn(ExperimentalMaterial3Api::class)

package com.melodia.player.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melodia.player.MusicViewModel
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(vm: MusicViewModel) {
    val st by vm.eqState.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Equalizer", actions = {
            if (st.available) {
                IconButton(onClick = { vm.resetEq() }) { Icon(Icons.Default.RestartAlt, "Resetuj") }
            }
        })

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!st.available) {
                EmptyState("Equalizer jest niedostępny.\nUruchom dowolny utwór – jeśli dalej nie działa, telefon nie udostępnia efektów audio.")
            } else {
                // Włącznik
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Efekty dźwięku", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (st.enabled) "Włączone" else "Wyłączone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = st.enabled, onCheckedChange = { vm.setEqEnabled(it) })
                    }
                }

                // Presety
                if (st.presets.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = st.currentPreset == -1, onClick = {}, label = { Text("Własny") }, enabled = st.enabled)
                        st.presets.forEachIndexed { i, name ->
                            FilterChip(
                                selected = st.currentPreset == i,
                                onClick = { vm.useEqPreset(i) },
                                label = { Text(name) },
                                enabled = st.enabled
                            )
                        }
                    }
                }

                // Pasma
                Card {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 4.dp)) {
                            st.bands.forEach { band ->
                                Column(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "%+.0f".format(band.level / 100f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
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

                // Efekty dodatkowe
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (st.bassSupported) {
                            EffectSlider("Wzmocnienie basu", st.bassBoost, 1000, st.enabled) { vm.setBassBoost(it) }
                        }
                        if (st.virtSupported) {
                            EffectSlider("Dźwięk przestrzenny", st.virtualizer, 1000, st.enabled) { vm.setVirtualizer(it) }
                        }
                        if (st.loudSupported) {
                            EffectSlider("Wzmocnienie głośności", st.loudness, 1500, st.enabled, suffix = { "+%.1f dB".format(it / 100f) }) {
                                vm.setLoudness(it)
                            }
                        }
                        if (!st.bassSupported && !st.virtSupported && !st.loudSupported) {
                            Text("Telefon nie obsługuje dodatkowych efektów.")
                        }
                    }
                }
            }

            // Prędkość – działa niezależnie od equalizera
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
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    max: Int,
    enabled: Boolean,
    suffix: (Int) -> String = { "${(it * 100f / max).roundToInt()}%" },
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(suffix(value), style = MaterialTheme.typography.labelMedium)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..max.toFloat(),
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
