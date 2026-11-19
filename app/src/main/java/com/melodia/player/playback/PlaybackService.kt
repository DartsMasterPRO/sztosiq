package com.melodia.player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.melodia.player.MainActivity
import com.melodia.player.MelodiaApp
import com.melodia.player.data.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serwis odtwarzania w tle. Media3 sam obsługuje powiadomienie z kontrolkami,
 * ekran blokady, słuchawki Bluetooth, Android Auto/Wear (przyciski multimedialne)
 * i fokus audio (np. pauza przy połączeniu).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as MelodiaApp
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var lastCountedId: String? = null

    override fun onCreate() {
        super.onCreate()
        val settings = app.settings

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pauza po wyjęciu słuchawek
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
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .build()

        restoreQueue()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        SleepTimer.cancel()
        SleepTimer.player = null
        AudioEffects.release()
        broadcastEffectSession(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------- Zdarzenia odtwarzacza ----------

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) lastCountedId = null
            countPlayIfNeeded()
            saveQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) countPlayIfNeeded() else saveQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
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
    }

    private fun countPlayIfNeeded() {
        if (!player.playWhenReady) return
        val id = player.currentMediaItem?.mediaId ?: return
        if (id == lastCountedId) return
        lastCountedId = id
        id.toLongOrNull()?.let { app.library.recordPlay(it) }
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
        val songs = app.repository.loadSongs().associateBy { it.id }
        val items = ids.mapNotNull { songs[it]?.toMediaItem() }
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

    // ---------- Callback sesji ----------

    private inner class SessionCallback : MediaSession.Callback {

        /** Kontroler przesyła tylko mediaId – tu odtwarzamy z niego adres pliku. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                item.buildUpon().setUri(resolveUri(item)).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /** Wznowienie odtwarzania z powiadomienia systemowego / słuchawek po restarcie. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val saved = buildSavedQueue()
                if (saved != null) future.set(saved)
                else future.setException(UnsupportedOperationException("Brak zapisanej kolejki"))
            }
            return future
        }
    }

    private fun resolveUri(item: MediaItem): Uri? {
        item.localConfiguration?.uri?.let { return it }
        val id = item.mediaId
        id.toLongOrNull()?.let {
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        }
        if (id.startsWith("uri:")) return Uri.parse(id.removePrefix("uri:"))
        return item.requestMetadata.mediaUri
    }
}
