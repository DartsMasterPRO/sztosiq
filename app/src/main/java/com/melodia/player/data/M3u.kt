package com.melodia.player.data

import android.net.Uri

/** Import / eksport playlist w formacie M3U (zgodny z większością odtwarzaczy). */
object M3u {
    fun export(name: String, songs: List<Song>): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(name).append('\n')
        songs.forEach { s ->
            append("#EXTINF:").append(s.duration / 1000).append(',')
                .append(s.artist).append(" - ").append(s.title).append('\n')
            append(s.path).append('\n')
        }
    }

    /** Zwraca id utworów z biblioteki pasujących do wpisów playlisty. */
    fun import(text: String, library: List<Song>): List<Long> {
        val byPath = library.associateBy { it.path.lowercase() }
        val byFile = library.groupBy { it.path.substringAfterLast('/').lowercase() }
        val byLabel = library.groupBy { "${it.artist} - ${it.title}".lowercase() }
        val result = ArrayList<Long>()
        var lastLabel: String? = null
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                lastLabel = line.substringAfter(',', "").trim().lowercase().ifEmpty { null }
                continue
            }
            if (line.startsWith("#")) continue
            val path = Uri.decode(line.removePrefix("file://")).replace('\\', '/')
            val song = byPath[path.lowercase()]
                ?: byFile[path.substringAfterLast('/').lowercase()]?.firstOrNull()
                ?: lastLabel?.let { byLabel[it]?.firstOrNull() }
            if (song != null) result += song.id
            lastLabel = null
        }
        return result
    }
}
