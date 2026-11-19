package com.melodia.player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.melodia.player.MainActivity
import com.melodia.player.MelodiaApp
import com.melodia.player.data.Song
import com.melodia.player.data.SongSort
import com.melodia.player.data.UiSettings
import com.melodia.player.data.groupAlbums
import com.melodia.player.data.groupArtists
import com.melodia.player.data.groupGenres
import com.melodia.player.data.songsLabel
import com.melodia.player.data.toMediaItem
import com.melodia.player.widget.MusicWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serwis odtwarzania w tle + biblioteka dla Android Auto.
 * Media3 obsługuje powiadomienie, ekran blokady, Bluetooth, przyciski multimedialne
 * i fokus audio. Tu dodatkowo: balans, soprany, ReplayGain, płynne przejścia,
 * statystyki słuchania i widżet.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val processor = SoundProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as MelodiaApp
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var lastCountedId: String? = null

    private var trackGainDb = Float.NaN
    private var albumGainDb = Float.NaN
    private var fadeJob: Job? = null
    private var fadeSec = -1

    private var listenSongId: Long? = null
    private var listenAccum = 0L
    private var playingSince = 0L

    private var songsCache: List<Song>? = null

    override fun onCreate() {
        super.onCreate()
        val settings = app.settings

        // Własny tor audio: dekoder systemowy + nasz procesor (balans / soprany / ReplayGain).
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            val sink = DefaultAudioSink.Builder(this)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                .build()
            arrayOf<Renderer>(
                MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink)
            )
        }

        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        audioSessionId = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        player.setAudioSessionId(audioSessionId)
        AudioEffects.attach(audioSessionId, settings)
        broadcastEffectSession(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)

        player.shuffleModeEnabled = settings.shuffle
        player.repeatMode = settings.repeatMode
        player.setPlaybackSpeed(settings.speed)
        player.addListener(listener)
        SleepTimer.player = player

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()

        scope.launch {
            combine(settings.ui, AudioEffects.state) { ui, eq -> ui to eq.enabled }.collect { (ui, eqOn) ->
                processor.balance = ui.balance
                processor.trebleDb = if (eqOn) ui.trebleDb else 0f
                applyReplayGain(ui)
                updateFadeLoop(ui.crossfadeSec)
            }
        }

        restoreQueue()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        flushListening()
        saveQueue()
        SleepTimer.cancel()
        SleepTimer.player = null
        VisualizerController.stop()
        AudioEffects.release()
        broadcastEffectSession(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        MusicWidget.update(this, null, null, false, null)
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Zdarzenia odtwarzacza ----------

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushListening()
            listenSongId = mediaItem?.mediaId?.toLongOrNull()
            playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else 0L
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) lastCountedId = null
            countPlayIfNeeded()
            saveQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                if (listenSongId == null) listenSongId = player.currentMediaItem?.mediaId?.toLongOrNull()
                playingSince = SystemClock.elapsedRealtime()
                countPlayIfNeeded()
            } else {
                flushListening()
                saveQueue()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
        }

        override fun onTracksChanged(tracks: Tracks) {
            readReplayGain(tracks)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            app.settings.shuffle = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            app.settings.repeatMode = repeatMode
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            app.settings.speed = playbackParameters.speed
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                SleepTimer.onEndOfTrackReached()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION
                )
            ) updateWidget()
        }
    }

    private fun updateWidget() {
        val m = player.mediaMetadata
        MusicWidget.update(this, m.title?.toString(), m.artist?.toString(), player.isPlaying, m.artworkUri)
    }

    private fun countPlayIfNeeded() {
        if (!player.playWhenReady) return
        val id = player.currentMediaItem?.mediaId ?: return
        if (id == lastCountedId) return
        lastCountedId = id
        id.toLongOrNull()?.let { app.library.recordPlay(it) }
    }

    /** Dolicza czas słuchania bieżącego utworu do statystyk. */
    private fun flushListening() {
        val id = listenSongId ?: return
        if (playingSince > 0L) {
            val now = SystemClock.elapsedRealtime()
            listenAccum += now - playingSince
            playingSince = if (::player.isInitialized && player.isPlaying) now else 0L
        }
        if (listenAccum > 0L) app.library.addListening(id, listenAccum)
        listenAccum = 0L
    }

    // ---------- ReplayGain ----------

    private val replayGainRegex =
        Regex("""REPLAYGAIN_(TRACK|ALBUM)_GAIN\D*?([-+]?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)

    private fun readReplayGain(tracks: Tracks) {
        var track = Float.NaN
        var album = Float.NaN
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    val text = metadata.get(j).toString()
                    for (m in replayGainRegex.findAll(text)) {
                        val v = m.groupValues[2].replace(',', '.').toFloatOrNull() ?: continue
                        if (m.groupValues[1].equals("TRACK", ignoreCase = true)) track = v else album = v
                    }
                }
            }
        }
        trackGainDb = track
        albumGainDb = album
        applyReplayGain(app.settings.ui.value)
    }

    private fun applyReplayGain(ui: UiSettings) {
        val gain = when (ui.replayGain) {
            1 -> trackGainDb.takeUnless { it.isNaN() } ?: albumGainDb
            2 -> albumGainDb.takeUnless { it.isNaN() } ?: trackGainDb
            else -> 0f
        }
        processor.gainDb = if (gain.isNaN()) 0f else gain.coerceIn(-15f, 10f)
    }

    // ---------- Płynne przejścia (wyciszenie końca / narastanie początku) ----------

    private fun updateFadeLoop(seconds: Int) {
        if (seconds == fadeSec) return
        fadeSec = seconds
        fadeJob?.cancel()
        if (seconds <= 0) {
            if (!SleepTimer.fading) player.volume = 1f
            return
        }
        val fadeMs = seconds * 1000L
        fadeJob = scope.launch {
            while (isActive) {
                if (player.isPlaying && !SleepTimer.fading) {
                    val dur = player.duration
                    val pos = player.currentPosition
                    var v = 1f
                    if (dur != C.TIME_UNSET && dur > fadeMs * 2) {
                        val remaining = dur - pos
                        if (remaining < fadeMs && (player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF)) {
                            v = remaining.toFloat() / fadeMs
                        }
                        if (pos < fadeMs) v = minOf(v, pos.toFloat() / fadeMs)
                    }
                    player.volume = v.coerceIn(0f, 1f)
                }
                delay(60)
            }
        }
    }

    // ---------- Zapis / przywracanie kolejki ----------

    private fun saveQueue() {
        if (!::player.isInitialized) return
        val s = app.settings
        s.queueIds = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        s.queueCurrentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        s.queuePosition = player.currentPosition
    }

    private fun restoreQueue() {
        if (app.settings.queueIds.isEmpty()) return
        scope.launch {
            val saved = buildSavedQueue() ?: return@launch
            if (player.mediaItemCount == 0) {
                player.setMediaItems(saved.mediaItems, saved.startIndex, saved.startPositionMs)
                player.prepare()
            }
        }
    }

    private suspend fun buildSavedQueue(): MediaSession.MediaItemsWithStartPosition? {
        val s = app.settings
        val ids = s.queueIds
        if (ids.isEmpty()) return null
        val byId = songs().associateBy { it.id }
        val items = ids.mapNotNull { byId[it]?.toMediaItem() }
        if (items.isEmpty()) return null
        val currentId = s.queueCurrentId.toString()
        val found = items.indexOfFirst { it.mediaId == currentId }
        return MediaSession.MediaItemsWithStartPosition(
            items,
            found.coerceAtLeast(0),
            if (found >= 0) s.queuePosition else 0L
        )
    }

    private fun broadcastEffectSession(action: String) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            sendBroadcast(
                Intent(action)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            )
        }
    }

    // ---------- Biblioteka (Android Auto) ----------

    private suspend fun songs(): List<Song> =
        songsCache ?: app.repository.loadSongs().also { songsCache = it }

    private fun folder(id: String, title: String, subtitle: String? = null, art: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    /** Utwór w kontekście listy – "rodzic|id", żeby po kliknięciu zagrała cała lista. */
    private fun playable(parent: String, s: Song): MediaItem =
        s.toMediaItem().buildUpon().setMediaId("$parent|${s.id}").build()

    private suspend fun children(parentId: String): List<MediaItem> {
        val all = songs()
        return when (parentId) {
            ROOT -> listOf(
                folder("songs", "Wszystkie utwory"),
                folder("albums", "Albumy"),
                folder("artists", "Wykonawcy"),
                folder("genres", "Gatunki"),
                folder("playlists", "Playlisty"),
                folder("favorites", "Ulubione"),
                folder("recent", "Ostatnio odtwarzane"),
            )
            "albums" -> groupAlbums(all).map { folder("album:${it.id}", it.title, it.artist, it.artUri) }
            "artists" -> groupArtists(all).map { folder("artist:${it.name}", it.name, songsLabel(it.songs.size), it.artUri) }
            "genres" -> groupGenres(all).map { folder("genre:${it.name}", it.name, songsLabel(it.songs.size)) }
            "playlists" -> app.library.playlists.value.map { folder("playlist:${it.id}", it.name, songsLabel(it.songIds.size)) }
            else -> songsFor(parentId, all).map { playable(parentId, it) }
        }
    }

    private fun songsFor(parentId: String, all: List<Song>): List<Song> {
        val byId by lazy { all.associateBy { it.id } }
        return when {
            parentId == "songs" -> SongSort.TITLE.sort(all)
            parentId == "favorites" -> app.library.favorites.value.mapNotNull { byId[it] }
            parentId == "recent" -> app.library.recent.value.mapNotNull { byId[it] }
            parentId.startsWith("album:") -> {
                val id = parentId.removePrefix("album:").toLongOrNull()
                all.filter { it.albumId == id }.sortedBy { it.track }
            }
            parentId.startsWith("artist:") -> {
                val name = parentId.removePrefix("artist:")
                SongSort.ALBUM.sort(all.filter { it.artist == name })
            }
            parentId.startsWith("genre:") -> {
                val name = parentId.removePrefix("genre:")
                SongSort.ARTIST.sort(all.filter { it.genre.trim() == name })
            }
            parentId.startsWith("playlist:") -> {
                val id = parentId.removePrefix("playlist:").toLongOrNull()
                app.library.playlists.value.firstOrNull { it.id == id }?.songIds?.mapNotNull { byId[it] }.orEmpty()
            }
            else -> emptyList()
        }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch {
            try {
                f.set(block())
            } catch (e: Throwable) {
                f.setException(e)
            }
        }
        return f
    }

    private fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val id = item.mediaId.substringAfterLast('|')
        val uri: Uri? = id.toLongOrNull()?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        } ?: if (id.startsWith("uri:")) Uri.parse(id.removePrefix("uri:")) else item.requestMetadata.mediaUri
        return item.buildUpon().setMediaId(id).setUri(uri).build()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            songsCache = null // odśwież przy każdym wejściu do biblioteki
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Melodia"), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val list = children(parentId)
            val paged = if (pageSize in 1 until Int.MAX_VALUE) list.drop(page * pageSize).take(pageSize) else list
            LibraryResult.ofItemList(paged, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val id = mediaId.substringAfterLast('|').toLongOrNull()
            val song = songs().firstOrNull { it.id == id }
            if (song != null) LibraryResult.ofItem(song.toMediaItem(), null)
            else LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { resolve(it) }.toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val single = mediaItems.singleOrNull()
            if (single != null && single.mediaId.contains('|')) {
                val parent = single.mediaId.substringBeforeLast('|')
                val id = single.mediaId.substringAfterLast('|').toLongOrNull()
                val list = songsFor(parent, songs())
                val index = list.indexOfFirst { it.id == id }
                if (index >= 0) {
                    return@future MediaSession.MediaItemsWithStartPosition(
                        list.map { it.toMediaItem() }, index, 0L
                    )
                }
            }
            MediaSession.MediaItemsWithStartPosition(mediaItems.map { resolve(it) }, startIndex, startPositionMs)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            buildSavedQueue() ?: throw UnsupportedOperationException("Brak zapisanej kolejki")
        }
    }

    companion object {
        private const val ROOT = "root"
    }
}
