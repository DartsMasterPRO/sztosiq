package com.melodia.player

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.melodia.player.data.Album
import com.melodia.player.data.Artist
import com.melodia.player.data.Folder
import com.melodia.player.data.Playlist
import com.melodia.player.data.Song
import com.melodia.player.data.SongSort
import com.melodia.player.data.groupAlbums
import com.melodia.player.data.groupArtists
import com.melodia.player.data.groupFolders
import com.melodia.player.data.normalizeForSearch
import com.melodia.player.data.toMediaItem
import com.melodia.player.playback.AudioEffects
import com.melodia.player.playback.EqState
import com.melodia.player.playback.PlaybackService
import com.melodia.player.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class QueueEntry(val index: Int, val item: MediaItem)

data class PlayerUi(
    val item: MediaItem? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val index: Int = -1,
    val hasItems: Boolean = false,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MelodiaApp
    private val store = app.library
    private val settings = app.settings

    // ================= Biblioteka =================

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _songSort = MutableStateFlow(
        runCatching { SongSort.valueOf(settings.songSort) }.getOrDefault(SongSort.TITLE)
    )
    val songSort: StateFlow<SongSort> = _songSort.asStateFlow()

    val sortedSongs: StateFlow<List<Song>> = combine(_songs, _songSort) { s, o -> o.sort(s) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = _songs.map { groupAlbums(it) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = _songs.map { groupArtists(it) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<Folder>> = _songs.map { groupFolders(it) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val songMap: StateFlow<Map<Long, Song>> = _songs.map { l -> l.associateBy { it.id } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val searchIndex: StateFlow<List<Pair<Song, String>>> = _songs
        .map { l -> l.map { it to "${it.title} ${it.artist} ${it.album}".normalizeForSearch() } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun searchSongs(query: String): List<Song> {
        val words = query.normalizeForSearch().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return searchIndex.value.filter { (_, text) -> words.all { text.contains(it) } }.map { it.first }
    }

    fun setSort(sort: SongSort) {
        _songSort.value = sort
        settings.songSort = sort.name
    }

    fun refreshLibrary() = loadLibrary(showSpinner = true)

    private fun loadLibrary(showSpinner: Boolean) {
        viewModelScope.launch {
            if (showSpinner) _loading.value = true
            _songs.value = app.repository.loadSongs()
            _loading.value = false
        }
    }

    // ================= Playlisty / ulubione =================

    val playlists: StateFlow<List<Playlist>> = store.playlists

    val favoriteSet: StateFlow<Set<Long>> = store.favorites.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val favoriteSongs = combine(store.favorites, songMap) { ids, m -> ids.mapNotNull { m[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recentlyAdded = _songs.map { l -> l.sortedByDescending { it.dateAdded }.take(100) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val mostPlayed = combine(store.playCounts, songMap) { counts, m ->
        counts.entries.sortedByDescending { it.value }.mapNotNull { m[it.key] }.take(100)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recentlyPlayed = combine(store.recent, songMap) { ids, m -> ids.mapNotNull { m[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun smartList(kind: String): StateFlow<List<Song>> = when (kind) {
        SMART_FAVORITES -> favoriteSongs
        SMART_ADDED -> recentlyAdded
        SMART_MOST -> mostPlayed
        else -> recentlyPlayed
    }

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()) = store.createPlaylist(name, songIds)
    fun renamePlaylist(id: Long, name: String) = store.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) = store.deletePlaylist(id)
    fun addToPlaylist(id: Long, songIds: List<Long>) = store.addToPlaylist(id, songIds)
    fun removeFromPlaylist(id: Long, index: Int) = store.removeFromPlaylist(id, index)
    fun movePlaylistItem(id: Long, from: Int, to: Int) = store.movePlaylistItem(id, from, to)
    fun toggleFavorite(songId: Long) = store.toggleFavorite(songId)

    // ================= Odtwarzacz =================

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    val currentId: StateFlow<String?> = _player.map { it.item?.mediaId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val eqState: StateFlow<EqState> = AudioEffects.state
    val sleepEndsAt: StateFlow<Long?> = SleepTimer.endsAt
    val sleepEndOfTrack: StateFlow<Boolean> = SleepTimer.endOfTrack

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshPlayer(
                events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
            )
        }
    }

    private var refreshJob: Job? = null
    private val libraryObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                delay(1500)
                loadLibrary(showSpinner = false)
            }
        }
    }

    init {
        connect()
        viewModelScope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(if (_player.value.isPlaying) 250L else 1000L)
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, libraryObserver
            )
        }
    }

    private fun connect() {
        if (controllerFuture != null) return
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                controllerFuture = null
                return@addListener
            }
            controller = c
            c.addListener(playerListener)
            refreshPlayer(true)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending.addLast(block)
            connect()
        }
    }

    private fun refreshPlayer(full: Boolean) {
        val c = controller ?: return
        val dur = c.duration
        _player.value = PlayerUi(
            item = c.currentMediaItem,
            isPlaying = c.isPlaying,
            duration = if (dur == C.TIME_UNSET || dur < 0) 0L else dur,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            speed = c.playbackParameters.speed,
            index = c.currentMediaItemIndex,
            hasItems = c.mediaItemCount > 0,
        )
        _position.value = c.currentPosition
        if (full) {
            val tl = c.currentTimeline
            val list = ArrayList<QueueEntry>(tl.windowCount)
            if (!tl.isEmpty) {
                // Kolejność odtwarzania (uwzględnia tryb losowy)
                var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
                while (i != C.INDEX_UNSET && list.size < tl.windowCount) {
                    list.add(QueueEntry(i, c.getMediaItemAt(i)))
                    i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
                }
            }
            _queue.value = list
        }
    }

    // ---------- Akcje ----------

    /** shuffle: null = zostaw obecny tryb, true/false = ustaw. */
    fun playSongs(list: List<Song>, start: Int = 0, shuffle: Boolean? = null) {
        if (list.isEmpty()) return
        withController { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            val startIndex = if (shuffle == true) Random.nextInt(list.size) else start.coerceIn(0, list.lastIndex)
            c.setMediaItems(list.map { it.toMediaItem() }, startIndex, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(list: List<Song>) {
        if (list.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) playSongs(list)
            else c.addMediaItems(c.currentMediaItemIndex + 1, list.map { it.toMediaItem() })
        }
    }

    fun addToQueue(list: List<Song>) {
        if (list.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) playSongs(list)
            else c.addMediaItems(list.map { it.toMediaItem() })
        }
    }

    fun playExternal(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
            } ?: uri.lastPathSegment ?: "Plik audio"
            val item = MediaItem.Builder()
                .setMediaId("uri:$uri")
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name.substringBeforeLast('.')).build())
                .build()
            withController { c ->
                c.setMediaItem(item)
                c.prepare()
                c.play()
            }
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun previousTrack() = withController { it.seekToPreviousMediaItem() }

    fun seekTo(ms: Long) = withController {
        it.seekTo(ms)
        _position.value = ms
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController {
        it.playbackParameters = PlaybackParameters(speed, it.playbackParameters.pitch)
    }

    fun playQueueIndex(index: Int) = withController {
        it.seekToDefaultPosition(index)
        it.play()
    }

    fun removeFromQueue(index: Int) = withController { it.removeMediaItem(index) }

    fun clearQueue() = withController {
        it.stop()
        it.clearMediaItems()
    }

    // ---------- Equalizer ----------

    fun setEqEnabled(on: Boolean) = AudioEffects.setEnabled(on)
    fun setEqBand(band: Int, level: Int) = AudioEffects.setBand(band, level)
    fun useEqPreset(index: Int) = AudioEffects.usePreset(index)
    fun setBassBoost(v: Int) = AudioEffects.setBassBoost(v)
    fun setVirtualizer(v: Int) = AudioEffects.setVirtualizer(v)
    fun setLoudness(v: Int) = AudioEffects.setLoudness(v)
    fun resetEq() = AudioEffects.reset()

    // ---------- Wyłącznik czasowy ----------

    fun startSleepTimer(minutes: Int) = SleepTimer.start(minutes)
    fun sleepAfterTrack() = SleepTimer.startEndOfTrack()
    fun cancelSleepTimer() = SleepTimer.cancel()

    override fun onCleared() {
        runCatching { app.contentResolver.unregisterContentObserver(libraryObserver) }
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }

    companion object {
        const val SMART_FAVORITES = "favorites"
        const val SMART_ADDED = "added"
        const val SMART_MOST = "most"
        const val SMART_RECENT = "recent"
    }
}
