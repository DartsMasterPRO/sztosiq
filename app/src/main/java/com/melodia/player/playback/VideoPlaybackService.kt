package com.melodia.player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.melodia.player.MelodiaApp
import com.melodia.player.video.VideoActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Osobny odtwarzacz do filmów – nie miesza się z kolejką muzyki.
 * Dzięki usłudze dźwięk filmu może grać w tle, a sterowanie jest w powiadomieniu.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val processor = SoundProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as MelodiaApp

    override fun onCreate() {
        super.onCreate()
        val sessionId = app.audioSessionId
        player = PlayerFactory.create(this, processor, video = true, audioSessionId = sessionId)
        AudioEffects.acquire(sessionId, app.settings)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = Unit

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    oldPosition.mediaItem?.mediaId?.let { app.settings.saveVideoPosition(it, 0L, 0L) }
                }
            }
        })

        scope.launch {
            kotlinx.coroutines.flow.combine(app.settings.ui, AudioEffects.state) { ui, eq -> ui to eq.enabled }
                .collect { (ui, eqOn) ->
                    processor.balance = ui.balance
                    processor.trebleDb = if (eqOn) ui.trebleDb else 0f
                }
        }

        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, VideoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setId("sztosiq-video")
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.map { resolve(it) }.toMutableList())
            })
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        player.pause()
        stopSelf()
    }

    override fun onDestroy() {
        savePosition()
        AudioEffects.releaseUser()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private fun savePosition() {
        if (!::player.isInitialized) return
        val id = player.currentMediaItem?.mediaId ?: return
        val dur = player.duration
        app.settings.saveVideoPosition(id, player.currentPosition, if (dur == C.TIME_UNSET) 0L else dur)
    }

    /** mediaId "v:<id>" (z biblioteki) albo "uri:<adres>" (z innej aplikacji); napisy w extras. */
    private fun resolve(item: MediaItem): MediaItem {
        val id = item.mediaId
        val uri: Uri? = item.localConfiguration?.uri ?: when {
            id.startsWith("v:") -> id.removePrefix("v:").toLongOrNull()?.let {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it)
            }
            id.startsWith("uri:") -> Uri.parse(id.removePrefix("uri:"))
            else -> item.requestMetadata.mediaUri
        }
        val builder = item.buildUpon().setUri(uri)
        val subs = item.requestMetadata.extras?.getString(EXTRA_SUBTITLES)
        if (!subs.isNullOrBlank()) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subs))
                        .setMimeType(
                            if (subs.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT
                            else MimeTypes.APPLICATION_SUBRIP
                        )
                        .setLanguage("pl")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        return builder.build()
    }

    companion object {
        const val EXTRA_SUBTITLES = "subs"
    }
}
