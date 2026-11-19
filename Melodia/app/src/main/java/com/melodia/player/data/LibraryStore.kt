package com.melodia.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Playlisty, ulubione, licznik odtworzeń i historia – zapisywane w pliku JSON w pamięci aplikacji.
 * Wszystkie metody wywoływane są z głównego wątku.
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
            val pc = o.optJSONObject("playCounts")
            if (pc != null) {
                val map = HashMap<Long, Int>()
                pc.keys().forEach { key -> key.toLongOrNull()?.let { map[it] = pc.optInt(key) } }
                _playCounts.value = map
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
}
