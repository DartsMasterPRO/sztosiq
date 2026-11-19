package com.melodia.player.data

import android.content.Context
import androidx.core.content.edit

/** Proste ustawienia w SharedPreferences: equalizer, sortowanie, zapisana kolejka. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("melodia", Context.MODE_PRIVATE)

    var eqEnabled: Boolean
        get() = p.getBoolean("eqEnabled", false)
        set(v) = p.edit { putBoolean("eqEnabled", v) }

    /** -1 = ustawienia własne */
    var eqPreset: Int
        get() = p.getInt("eqPreset", -1)
        set(v) = p.edit { putInt("eqPreset", v) }

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
}
