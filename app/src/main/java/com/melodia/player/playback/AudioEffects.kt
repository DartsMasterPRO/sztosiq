package com.melodia.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.melodia.player.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerHz: Int, val level: Int)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val bands: List<EqBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val currentPreset: Int = -1,
    val bassSupported: Boolean = false,
    val bassBoost: Int = 0,
    val virtSupported: Boolean = false,
    val virtualizer: Int = 0,
    val loudSupported: Boolean = false,
    val loudness: Int = 0,
)

/**
 * Systemowe efekty audio (Equalizer, BassBoost, Virtualizer, LoudnessEnhancer)
 * podpięte do sesji audio odtwarzacza. Ustawienia są zapamiętywane.
 */
object AudioEffects {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var settings: Settings? = null

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    fun attach(sessionId: Int, settings: Settings) {
        release()
        this.settings = settings
        eq = safe { Equalizer(0, sessionId) }
        bass = safe { BassBoost(0, sessionId) }?.let { b ->
            if (safe { b.strengthSupported } == true) b else { safe { b.release() }; null }
        }
        virt = safe { Virtualizer(0, sessionId) }?.let { v ->
            if (safe { v.strengthSupported } == true) v else { safe { v.release() }; null }
        }
        loud = safe { LoudnessEnhancer(sessionId) }
        applySaved()
    }

    private fun applySaved() {
        val s = settings ?: return
        eq?.let { e ->
            safe {
                val preset = s.eqPreset
                if (preset >= 0 && preset < e.numberOfPresets.toInt()) {
                    e.usePreset(preset.toShort())
                } else {
                    val bands = s.eqBands
                    if (bands.size == e.numberOfBands.toInt()) {
                        bands.forEachIndexed { i, level -> e.setBandLevel(i.toShort(), level.toShort()) }
                    }
                }
                Unit
            }
        }
        safe { bass?.setStrength(s.bassBoost.toShort()) }
        safe { virt?.setStrength(s.virtualizer.toShort()) }
        safe { loud?.setTargetGain(s.loudness) }
        applyEnabled()
        publish()
    }

    private fun applyEnabled() {
        val s = settings ?: return
        val on = s.eqEnabled
        safe { eq?.setEnabled(on) }
        safe { bass?.setEnabled(on && s.bassBoost > 0) }
        safe { virt?.setEnabled(on && s.virtualizer > 0) }
        safe { loud?.setEnabled(on && s.loudness > 0) }
    }

    private fun currentBands(): List<Int> {
        val e = eq ?: return emptyList()
        return safe { (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() } }
            ?: emptyList()
    }

    private fun publish() {
        val s = settings
        val e = eq
        if (s == null || e == null) {
            _state.value = EqState(available = false)
            return
        }
        _state.value = safe {
            val range = e.bandLevelRange
            EqState(
                available = true,
                enabled = s.eqEnabled,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                bands = (0 until e.numberOfBands.toInt()).map { i ->
                    EqBand(i, e.getCenterFreq(i.toShort()) / 1000, e.getBandLevel(i.toShort()).toInt())
                },
                presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) },
                currentPreset = s.eqPreset,
                bassSupported = bass != null,
                bassBoost = s.bassBoost,
                virtSupported = virt != null,
                virtualizer = s.virtualizer,
                loudSupported = loud != null,
                loudness = s.loudness,
            )
        } ?: EqState(available = false)
    }

    // ---------- API dla UI ----------

    fun setEnabled(on: Boolean) {
        settings?.eqEnabled = on
        applyEnabled()
        publish()
    }

    fun setBand(band: Int, level: Int) {
        val e = eq ?: return
        safe { e.setBandLevel(band.toShort(), level.toShort()) }
        settings?.let {
            it.eqPreset = -1
            it.eqBands = currentBands()
        }
        publish()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        safe { e.usePreset(index.toShort()) }
        settings?.let {
            it.eqPreset = index
            it.eqBands = currentBands()
        }
        publish()
    }

    fun setBassBoost(value: Int) {
        settings?.bassBoost = value
        safe { bass?.setStrength(value.toShort()) }
        applyEnabled()
        publish()
    }

    fun setVirtualizer(value: Int) {
        settings?.virtualizer = value
        safe { virt?.setStrength(value.toShort()) }
        applyEnabled()
        publish()
    }

    fun setLoudness(value: Int) {
        settings?.loudness = value
        safe { loud?.setTargetGain(value) }
        applyEnabled()
        publish()
    }

    fun reset() {
        eq?.let { e ->
            safe { for (i in 0 until e.numberOfBands.toInt()) e.setBandLevel(i.toShort(), 0.toShort()) }
        }
        settings?.let {
            it.eqPreset = -1
            it.eqBands = currentBands()
            it.bassBoost = 0
            it.virtualizer = 0
            it.loudness = 0
        }
        safe { bass?.setStrength(0.toShort()) }
        safe { virt?.setStrength(0.toShort()) }
        safe { loud?.setTargetGain(0) }
        applyEnabled()
        publish()
    }

    fun release() {
        safe { eq?.release() }
        safe { bass?.release() }
        safe { virt?.release() }
        safe { loud?.release() }
        eq = null; bass = null; virt = null; loud = null
        _state.value = EqState(available = false)
    }
}
