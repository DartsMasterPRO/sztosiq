package com.melodia.player.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Ustawienia wyglądu i dźwięku obserwowane przez UI i serwis. */
data class UiSettings(
    /** 0 = jak system, 1 = jasny, 2 = ciemny */
    val themeMode: Int = 0,
    val amoled: Boolean = false,
    /** 0 = z tapety (Android 12+), 1 = z okładki albumu, 2 = wybrany motyw */
    val colorSource: Int = 0,
    val themePreset: Int = 0,
    /** 0 = klasyczny, 1 = winyl, 2 = pełny ekran */
    val npStyle: Int = 0,
    val artScale: Float = 1f,
    val animatedBg: Boolean = true,
    /** 0 = z okładkami, 1 = kompaktowa */
    val listStyle: Int = 0,
    val visualizer: Boolean = false,
    /** 0 = słupki, 1 = fala */
    val visualizerStyle: Int = 0,
    /** -1 (lewo) … 1 (prawo) */
    val balance: Float = 0f,
    /** 0 = wył., 1 = utwór, 2 = album */
    val replayGain: Int = 0,
    val crossfadeSec: Int = 0,
    val trebleDb: Float = 0f,
)

data class CustomPreset(
    val name: String,
    val bands: List<Int>,
    val bass: Int,
    val virt: Int,
    val treble: Float,
)

/** Ustawienia w SharedPreferences: equalizer, wygląd, zapisana kolejka. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("melodia", Context.MODE_PRIVATE)

    var eqEnabled: Boolean
        get() = p.getBoolean("eqEnabled", false)
        set(v) = p.edit { putBoolean("eqEnabled", v) }

    /** Nazwa wybranego presetu, "" = ustawienia ręczne. */
    var eqPresetName: String
        get() = p.getString("eqPresetName", "").orEmpty()
        set(v) = p.edit { putString("eqPresetName", v) }

    var eqBands: List<Int>
        get() = p.getString("eqBands", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        set(v) = p.edit { putString("eqBands", v.joinToString(",")) }

    var bassBoost: Int
        get() = p.getInt("bassBoost", 0)
        set(v) = p.edit { putInt("bassBoost", v) }

    var virtualizer: Int
        get() = p.getInt("virtualizer", 0)
        set(v) = p.edit { putInt("virtualizer", v) }

    var loudness: Int
        get() = p.getInt("loudness", 0)
        set(v) = p.edit { putInt("loudness", v) }

    var customPresets: List<CustomPreset>
        get() = runCatching {
            val a = JSONArray(p.getString("customPresets", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val b = o.getJSONArray("bands")
                CustomPreset(
                    name = o.getString("name"),
                    bands = (0 until b.length()).map { b.getInt(it) },
                    bass = o.optInt("bass"),
                    virt = o.optInt("virt"),
                    treble = o.optDouble("treble", 0.0).toFloat(),
                )
            }
        }.getOrDefault(emptyList())
        set(v) {
            val a = JSONArray()
            v.forEach { c ->
                a.put(JSONObject().apply {
                    put("name", c.name)
                    put("bands", JSONArray(c.bands))
                    put("bass", c.bass)
                    put("virt", c.virt)
                    put("treble", c.treble.toDouble())
                })
            }
            p.edit { putString("customPresets", a.toString()) }
        }

    var songSort: String
        get() = p.getString("songSort", "TITLE") ?: "TITLE"
        set(v) = p.edit { putString("songSort", v) }

    var queueIds: List<Long>
        get() = p.getString("queueIds", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        set(v) = p.edit { putString("queueIds", v.joinToString(",")) }

    var queueCurrentId: Long
        get() = p.getLong("queueCurrentId", -1L)
        set(v) = p.edit { putLong("queueCurrentId", v) }

    var queuePosition: Long
        get() = p.getLong("queuePosition", 0L)
        set(v) = p.edit { putLong("queuePosition", v) }

    var speed: Float
        get() = p.getFloat("speed", 1f)
        set(v) = p.edit { putFloat("speed", v) }

    var shuffle: Boolean
        get() = p.getBoolean("shuffle", false)
        set(v) = p.edit { putBoolean("shuffle", v) }

    var repeatMode: Int
        get() = p.getInt("repeatMode", 0)
        set(v) = p.edit { putInt("repeatMode", v) }

    // ---------- Filmy ----------

    /** Dźwięk filmu gra dalej po wyjściu z odtwarzacza. */
    var videoBackgroundAudio: Boolean
        get() = p.getBoolean("videoBgAudio", false)
        set(v) = p.edit { putBoolean("videoBgAudio", v) }

    var videoSort: String
        get() = p.getString("videoSort", "DATE") ?: "DATE"
        set(v) = p.edit { putString("videoSort", v) }

    /** Zapamiętane miejsca w filmach: mediaId -> ms. */
    private val _videoPositions = MutableStateFlow(readPositions())
    val videoPositions: StateFlow<Map<String, Long>> = _videoPositions.asStateFlow()

    fun saveVideoPosition(mediaId: String, positionMs: Long, durationMs: Long) {
        val map = _videoPositions.value.toMutableMap()
        if (positionMs < 5_000 || (durationMs > 0 && durationMs - positionMs < 10_000)) map.remove(mediaId)
        else map[mediaId] = positionMs
        // ogranicz do 300 ostatnich
        val trimmed = if (map.size > 300) map.entries.toList().takeLast(300).associate { it.key to it.value } else map
        _videoPositions.value = trimmed
        val o = JSONObject()
        trimmed.forEach { (k, v) -> o.put(k, v) }
        p.edit { putString("videoPositions", o.toString()) }
    }

    private fun readPositions(): Map<String, Long> = runCatching {
        val o = JSONObject(p.getString("videoPositions", "{}") ?: "{}")
        val map = LinkedHashMap<String, Long>()
        o.keys().forEach { k -> map[k] = o.optLong(k) }
        map as Map<String, Long>
    }.getOrDefault(emptyMap())

    // ---------- Ustawienia obserwowane ----------

    private val _ui = MutableStateFlow(readUi())
    val ui: StateFlow<UiSettings> = _ui.asStateFlow()

    fun updateUi(block: (UiSettings) -> UiSettings) {
        val n = block(_ui.value)
        if (n == _ui.value) return
        _ui.value = n
        p.edit {
            putInt("ui_themeMode", n.themeMode)
            putBoolean("ui_amoled", n.amoled)
            putInt("ui_colorSource", n.colorSource)
            putInt("ui_themePreset", n.themePreset)
            putInt("ui_npStyle", n.npStyle)
            putFloat("ui_artScale", n.artScale)
            putBoolean("ui_animatedBg", n.animatedBg)
            putInt("ui_listStyle", n.listStyle)
            putBoolean("ui_visualizer", n.visualizer)
            putInt("ui_visualizerStyle", n.visualizerStyle)
            putFloat("ui_balance", n.balance)
            putInt("ui_replayGain", n.replayGain)
            putInt("ui_crossfade", n.crossfadeSec)
            putFloat("ui_treble", n.trebleDb)
        }
    }

    private fun readUi() = UiSettings(
        themeMode = p.getInt("ui_themeMode", 0),
        amoled = p.getBoolean("ui_amoled", false),
        colorSource = p.getInt("ui_colorSource", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0 else 1),
        themePreset = p.getInt("ui_themePreset", 0),
        npStyle = p.getInt("ui_npStyle", 0),
        artScale = p.getFloat("ui_artScale", 1f),
        animatedBg = p.getBoolean("ui_animatedBg", true),
        listStyle = p.getInt("ui_listStyle", 0),
        visualizer = p.getBoolean("ui_visualizer", false),
        visualizerStyle = p.getInt("ui_visualizerStyle", 0),
        balance = p.getFloat("ui_balance", 0f),
        replayGain = p.getInt("ui_replayGain", 0),
        crossfadeSec = p.getInt("ui_crossfade", 0),
        trebleDb = p.getFloat("ui_treble", 0f),
    )
}
