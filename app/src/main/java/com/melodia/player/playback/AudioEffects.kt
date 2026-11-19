package com.melodia.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.melodia.player.data.CustomPreset
import com.melodia.player.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.roundToInt

data class EqBand(val index: Int, val centerHz: Int, val level: Int)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val bands: List<EqBand> = emptyList(),
    val presetName: String = "",
    val customPresets: List<CustomPreset> = emptyList(),
    val bassSupported: Boolean = false,
    val bassBoost: Int = 0,
    val virtSupported: Boolean = false,
    val virtualizer: Int = 0,
    val loudSupported: Boolean = false,
    val loudness: Int = 0,
)

/** Wbudowane presety jako krzywe dB w punktach 60 / 230 / 910 / 3600 / 14000 Hz. */
object EqPresets {
    private val FREQS = listOf(60f, 230f, 910f, 3600f, 14000f)

    val builtIn: List<Pair<String, List<Float>>> = listOf(
        "Płaski" to listOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(5f, 3f, -1f, 3f, 5f),
        "Pop" to listOf(-1f, 2f, 4f, 2f, -1f),
        "Jazz" to listOf(4f, 2f, -1f, 2f, 4f),
        "Klasyczna" to listOf(5f, 3f, -2f, 4f, 4f),
        "Elektroniczna" to listOf(6f, 3f, 0f, 2f, 5f),
        "Hip-Hop" to listOf(6f, 4f, 0f, 1f, 3f),
        "Taneczna" to listOf(6f, 1f, 2f, 4f, 1f),
        "Metal" to listOf(4f, 1f, 4f, 2f, 0f),
        "Akustyczna" to listOf(4f, 2f, 1f, 3f, 3f),
        "Wokal" to listOf(-2f, 0f, 4f, 3f, 0f),
        "Więcej basu" to listOf(8f, 5f, 0f, 0f, 0f),
        "Mniej basu" to listOf(-6f, -3f, 0f, 0f, 0f),
        "Więcej sopranów" to listOf(0f, 0f, 0f, 3f, 7f),
    )

    /** Wartość krzywej (dB) dla danej częstotliwości – interpolacja logarytmiczna. */
    fun gainAt(curve: List<Float>, hz: Float): Float {
        if (hz <= FREQS.first()) return curve.first()
        if (hz >= FREQS.last()) return curve.last()
        for (i in 0 until FREQS.lastIndex) {
            val f0 = FREQS[i]
            val f1 = FREQS[i + 1]
            if (hz in f0..f1) {
                val t = (ln(hz / f0) / ln(f1 / f0))
                return curve[i] + (curve[i + 1] - curve[i]) * t
            }
        }
        return 0f
    }
}

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

    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    private var users = 0

    /** Muzyka i wideo dzielą jedną sesję audio – efekty żyją, dopóki używa ich którykolwiek odtwarzacz. */
    fun acquire(sessionId: Int, settings: Settings) {
        users++
        if (eq != null && _sessionId.value == sessionId) return
        attach(sessionId, settings)
    }

    fun releaseUser() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) release()
    }

    private fun attach(sessionId: Int, settings: Settings) {
        release()
        this.settings = settings
        _sessionId.value = sessionId
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
        val bands = s.eqBands
        eq?.let { e ->
            safe {
                if (bands.size == e.numberOfBands.toInt()) {
                    bands.forEachIndexed { i, level -> e.setBandLevel(i.toShort(), level.toShort()) }
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
                presetName = s.eqPresetName,
                customPresets = s.customPresets,
                bassSupported = bass != null,
                bassBoost = s.bassBoost,
                virtSupported = virt != null,
                virtualizer = s.virtualizer,
                loudSupported = loud != null,
                loudness = s.loudness,
            )
        } ?: EqState(available = false)
    }

    private fun setLevels(levels: List<Int>) {
        val e = eq ?: return
        safe {
            levels.forEachIndexed { i, l ->
                if (i < e.numberOfBands) e.setBandLevel(i.toShort(), l.toShort())
            }
            Unit
        }
        settings?.eqBands = currentBands()
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
            it.eqPresetName = ""
            it.eqBands = currentBands()
        }
        publish()
    }

    fun applyBuiltIn(name: String) {
        val curve = EqPresets.builtIn.firstOrNull { it.first == name }?.second ?: return
        val st = _state.value
        val levels = st.bands.map { b ->
            (EqPresets.gainAt(curve, b.centerHz.toFloat()) * 100f).roundToInt().coerceIn(st.minLevel, st.maxLevel)
        }
        setLevels(levels)
        settings?.eqPresetName = name
        publish()
    }

    /** Zwraca soprany zapisane w presecie – ustawia je wywołujący (są w UiSettings). */
    fun applyCustom(preset: CustomPreset): Float {
        setLevels(preset.bands)
        settings?.let {
            it.eqPresetName = preset.name
            it.bassBoost = preset.bass
            it.virtualizer = preset.virt
        }
        safe { bass?.setStrength(preset.bass.toShort()) }
        safe { virt?.setStrength(preset.virt.toShort()) }
        applyEnabled()
        publish()
        return preset.treble
    }

    fun saveCustom(name: String, treble: Float) {
        val s = settings ?: return
        val preset = CustomPreset(name, currentBands(), s.bassBoost, s.virtualizer, treble)
        s.customPresets = s.customPresets.filterNot { it.name == name } + preset
        s.eqPresetName = name
        publish()
    }

    fun deleteCustom(name: String) {
        val s = settings ?: return
        s.customPresets = s.customPresets.filterNot { it.name == name }
        if (s.eqPresetName == name) s.eqPresetName = ""
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
        setLevels(List(currentBands().size) { 0 })
        settings?.let {
            it.eqPresetName = ""
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
        _sessionId.value = 0
        _state.value = EqState(available = false)
    }
}
