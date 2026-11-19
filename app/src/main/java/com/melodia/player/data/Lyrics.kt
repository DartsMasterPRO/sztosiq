package com.melodia.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

/** synced = true, gdy plik ma znaczniki czasu (LRC). */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val raw: String)

object LrcParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(text: String): Lyrics {
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        var offset = 0L
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("﻿")
            val off = OFFSET.find(line)
            if (off != null) {
                offset = off.groupValues[1].toLongOrNull() ?: 0L
                continue
            }
            val matches = TIME.findAll(line).toList()
            if (matches.isEmpty()) {
                if (!TAG.matches(line)) plain += line
                continue
            }
            val content = line.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                timed += LyricLine(min * 60_000 + sec * 1000 + ms, content)
            }
        }
        return if (timed.isNotEmpty()) {
            Lyrics(
                timed.map { it.copy(timeMs = (it.timeMs - offset).coerceAtLeast(0)) }.sortedBy { it.timeMs },
                synced = true,
                raw = text
            )
        } else {
            Lyrics(plain.dropWhile { it.isBlank() }.map { LyricLine(-1, it) }, synced = false, raw = text)
        }
    }
}

/**
 * Teksty piosenek: najpierw własna kopia aplikacji (dodana ręcznie),
 * potem plik .lrc obok utworu (o ile system pozwala go odczytać).
 */
class LyricsStore(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    suspend fun load(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val own = File(dir, "${song.id}.lrc")
        if (own.exists()) return@withContext runCatching { LrcParser.parse(own.readText()) }.getOrNull()
        val base = song.path.substringBeforeLast('.', "")
        if (base.isEmpty()) return@withContext null
        for (ext in listOf(".lrc", ".LRC", ".txt")) {
            val f = File(base + ext)
            val text = runCatching { if (f.canRead()) f.readText() else null }.getOrNull()
            if (!text.isNullOrBlank()) return@withContext LrcParser.parse(text)
        }
        null
    }

    suspend fun save(songId: Long, text: String) = withContext(Dispatchers.IO) {
        File(dir, "$songId.lrc").writeText(text)
    }

    suspend fun delete(songId: Long) = withContext(Dispatchers.IO) {
        File(dir, "$songId.lrc").delete()
    }
}
