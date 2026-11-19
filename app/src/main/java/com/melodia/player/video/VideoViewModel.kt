package com.melodia.player.video

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
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
import com.melodia.player.MelodiaApp
import com.melodia.player.data.Video
import com.melodia.player.playback.VideoPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VideoUi(
    val title: String = "",
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val duration: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val speed: Float = 1f,
    val hasSubtitles: Boolean = false,
    val subtitlesOn: Boolean = true,
    val hasItems: Boolean = false,
)

class VideoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MelodiaApp

    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(VideoUi())
    val state: StateFlow<VideoUi> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _backgroundAudio = MutableStateFlow(app.settings.videoBackgroundAudio)
    val backgroundAudio: StateFlow<Boolean> = _backgroundAudio.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        connect()
        viewModelScope.launch {
            while (isActive) {
                _controller.value?.let { _position.value = it.currentPosition }
                delay(if (_state.value.isPlaying) 250L else 800L)
            }
        }
    }

    private fun connect() {
        if (future != null) return
        val ctx = getApplication<Application>()
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, VideoPlaybackService::class.java)))
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                return@addListener
            }
            c.addListener(listener)
            _controller.value = c
            refresh()
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun ctl(block: (MediaController) -> Unit) {
        val c = _controller.value
        if (c != null) block(c) else {
            pending.addLast(block)
            connect()
        }
    }

    private fun refresh() {
        val c = _controller.value ?: return
        val dur = c.duration
        val size = c.videoSize
        val textGroup = runCatching { c.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT } }.getOrDefault(false)
        val subsOn = C.TRACK_TYPE_TEXT !in c.trackSelectionParameters.disabledTrackTypes
        _state.value = VideoUi(
            title = c.mediaMetadata.title?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            ended = c.playbackState == Player.STATE_ENDED,
            duration = if (dur == C.TIME_UNSET || dur < 0) 0L else dur,
            width = (size.width * size.pixelWidthHeightRatio).toInt(),
            height = size.height,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            speed = c.playbackParameters.speed,
            hasSubtitles = textGroup,
            subtitlesOn = subsOn,
            hasItems = c.mediaItemCount > 0,
        )
        _position.value = c.currentPosition
    }

    // ---------- Ładowanie ----------

    /** Filmy z biblioteki (lista do przełączania następny / poprzedni). */
    fun playLibrary(ids: LongArray, index: Int) {
        viewModelScope.launch {
            val all = app.videos.loadVideos().associateBy { it.id }
            val videos = ids.mapNotNull { all[it] }
            if (videos.isEmpty()) return@launch
            val start = index.coerceIn(0, videos.lastIndex)
            val items = withContext(Dispatchers.IO) { videos.map { it.toItem() } }
            val startPos = app.settings.videoPositions.value[items[start].mediaId] ?: 0L
            ctl { c ->
                c.setMediaItems(items, start, startPos)
                c.prepare()
                c.play()
            }
        }
    }

    /** Film otwarty z galerii / menedżera plików. */
    fun playUri(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
            } ?: uri.lastPathSegment ?: "Film"
            val id = "uri:$uri"
            val item = MediaItem.Builder()
                .setMediaId(id)
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name.substringBeforeLast('.')).build())
                .build()
            val startPos = app.settings.videoPositions.value[id] ?: 0L
            ctl { c ->
                c.setMediaItem(item, startPos)
                c.prepare()
                c.play()
            }
        }
    }

    private fun Video.toItem(): MediaItem {
        // napisy o tej samej nazwie obok filmu (gdy Android pozwala je odczytać)
        val base = path.substringBeforeLast('.', "")
        val srt = if (base.isEmpty()) null else listOf(".srt", ".vtt").map { File(base + it) }
            .firstOrNull { runCatching { it.canRead() }.getOrDefault(false) }
        val extras = Bundle().apply { if (srt != null) putString(VideoPlaybackService.EXTRA_SUBTITLES, Uri.fromFile(srt).toString()) }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()
    }

    // ---------- Sterowanie ----------

    fun togglePlay() = ctl { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun pause() = ctl { it.pause() }
    fun seekTo(ms: Long) = ctl {
        it.seekTo(ms.coerceAtLeast(0))
        _position.value = ms
    }

    fun seekBy(deltaMs: Long) = ctl {
        val target = (it.currentPosition + deltaMs).coerceIn(0L, it.duration.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)
        it.seekTo(target)
        _position.value = target
    }

    fun next() = ctl { it.seekToNextMediaItem() }
    fun previous() = ctl { it.seekToPrevious() }
    fun setSpeed(speed: Float) = ctl { it.playbackParameters = PlaybackParameters(speed) }

    fun setSubtitlesOn(on: Boolean) = ctl {
        it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
            .build()
    }

    /** Wczytuje plik napisów wskazany przez użytkownika do bieżącego filmu. */
    fun loadSubtitles(uri: Uri) = ctl { c ->
        val current = c.currentMediaItem ?: return@ctl
        val index = c.currentMediaItemIndex
        val pos = c.currentPosition
        val wasPlaying = c.isPlaying
        val extras = Bundle().apply { putString(VideoPlaybackService.EXTRA_SUBTITLES, uri.toString()) }
        val item = current.buildUpon()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()
        c.replaceMediaItem(index, item)
        c.seekTo(index, pos)
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        c.prepare()
        if (wasPlaying) c.play()
    }

    fun setBackgroundAudio(on: Boolean) {
        _backgroundAudio.value = on
        app.settings.videoBackgroundAudio = on
    }

    override fun onCleared() {
        _controller.value?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        _controller.value = null
        super.onCleared()
    }
}
