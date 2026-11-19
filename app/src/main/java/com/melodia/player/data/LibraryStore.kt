package com.melodia.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Wpis historii: utwór, kiedy skończono słuchać, ile ms słuchano. */
data class HistoryEntry(val songId: Long, val time: Long, val ms: Long)

/**
 * Playlisty, ulubione, licznik odtworzeń, historia i czas słuchania –
 * zapisywane w pliku JSON w pamięci aplikacji. Wywoływane z głównego wątku.
 */
class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val io = Executors.newSingleThreadExecutor()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** Najnowsze na początku. */
    private val _favorites = MutableStateFlow<List<Long>>(emptyList())
    val favorites: StateFlow<List<Long>> = _favorites.asStateFlow()

    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()

    /** Ostatnio odtwarzane – najnowsze na początku. */
    private val _recent = MutableStateFlow<List<Long>>(emptyList())
    val recent: StateFlow<List<Long>> = _recent.asStateFlow()

    /** Historia słuchania – najnowsze na początku. */
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    /** Czas słuchania w miesiącach: "2026-11" -> ms. */
    private val _monthly = MutableStateFlow<Map<String, Long>>(emptyMap())
    val monthly: StateFlow<Map<String, Long>> = _monthly.asStateFlow()

    init {
        load()
    }

    // ---------- Playlisty ----------

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        _playlists.update { it + Playlist(id, name, songIds) }
        save()
        return id
    }

    fun renamePlaylist(id: Long, name: String) = editPlaylist(id) { it.copy(name = name) }

    fun deletePlaylist(id: Long) {
        _playlists.update { list -> list.filterNot { it.id == id } }
        save()
    }

    fun addToPlaylist(id: Long, songIds: List<Long>) =
        editPlaylist(id) { it.copy(songIds = it.songIds + songIds) }

    fun removeFromPlaylist(id: Long, index: Int) = editPlaylist(id) { p ->
        p.copy(songIds = p.songIds.toMutableList().apply { if (index in indices) removeAt(index) })
    }

    fun movePlaylistItem(id: Long, from: Int, to: Int) = editPlaylist(id) { p ->
        p.copy(songIds = p.songIds.toMutableList().apply {
            if (from in indices && to in indices) add(to, removeAt(from))
        })
    }

    private fun editPlaylist(id: Long, block: (Playlist) -> Playlist) {
        _playlists.update { list -> list.map { if (it.id == id) block(it) else it } }
        save()
    }

    // ---------- Ulubione / statystyki ----------

    fun toggleFavorite(songId: Long) {
        _favorites.update { if (songId in it) it - songId else listOf(songId) + it }
        save()
    }

    fun recordPlay(songId: Long) {
        _playCounts.update { it + (songId to (it[songId] ?: 0) + 1) }
        _recent.update { (listOf(songId) + (it - songId)).take(100) }
        save()
    }

    fun addListening(songId: Long, ms: Long) {
        if (ms < 3000) return
        val now = System.currentTimeMillis()
        _history.update { list ->
            val first = list.firstOrNull()
            if (first != null && first.songId == songId && now - first.time < 30 * 60_000L) {
                listOf(first.copy(time = now, ms = first.ms + ms)) + list.drop(1)
            } else {
                (listOf(HistoryEntry(songId, now, ms)) + list).take(5000)
            }
        }
        val key = monthKey(now)
        _monthly.update { it + (key to (it[key] ?: 0L) + ms) }
        save()
    }

    fun clearStats() {
        _playCounts.value = emptyMap()
        _recent.value = emptyList()
        _history.value = emptyList()
        _monthly.value = emptyMap()
        save()
    }

    // ---------- Zapis / odczyt ----------

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            val pa = o.optJSONArray("playlists") ?: JSONArray()
            _playlists.value = (0 until pa.length()).map { i ->
                val p = pa.getJSONObject(i)
                Playlist(p.getLong("id"), p.getString("name"), p.optJSONArray("songs").toLongList())
            }
            _favorites.value = o.optJSONArray("favorites").toLongList()
            _recent.value = o.optJSONArray("recent").toLongList()
            o.optJSONObject("playCounts")?.let { pc ->
                val map = HashMap<Long, Int>()
                pc.keys().forEach { key -> key.toLongOrNull()?.let { map[it] = pc.optInt(key) } }
                _playCounts.value = map
            }
            o.optJSONArray("history")?.let { h ->
                _history.value = (0 until h.length()).mapNotNull { i ->
                    h.optJSONArray(i)?.let { e -> HistoryEntry(e.getLong(0), e.getLong(1), e.getLong(2)) }
                }
            }
            o.optJSONObject("monthly")?.let { m ->
                val map = HashMap<String, Long>()
                m.keys().forEach { key -> map[key] = m.optLong(key) }
                _monthly.value = map
            }
        }
    }

    private fun JSONArray?.toLongList(): List<Long> =
        if (this == null) emptyList() else (0 until length()).map { getLong(it) }

    private fun save() {
        val o = JSONObject()
        o.put("playlists", JSONArray().apply {
            _playlists.value.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("songs", JSONArray(p.songIds))
                })
            }
        })
        o.put("favorites", JSONArray(_favorites.value))
        o.put("recent", JSONArray(_recent.value))
        o.put("playCounts", JSONObject().apply {
            _playCounts.value.forEach { (k, v) -> put(k.toString(), v) }
        })
        o.put("history", JSONArray().apply {
            _history.value.forEach { e -> put(JSONArray().put(e.songId).put(e.time).put(e.ms)) }
        })
        o.put("monthly", JSONObject().apply {
            _monthly.value.forEach { (k, v) -> put(k, v) }
        })
        val text = o.toString()
        io.execute {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
            }
        }
    }

    companion object {
        fun monthKey(time: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))
    }
}
