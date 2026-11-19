package com.melodia.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer

/** Buduje ExoPlayera z naszym torem audio (balans / soprany / ReplayGain). */
@OptIn(UnstableApi::class)
object PlayerFactory {

    fun create(context: Context, processor: SoundProcessor, video: Boolean, audioSessionId: Int): ExoPlayer {
        val renderers = RenderersFactory { handler, videoListener, audioListener, textOutput, _ ->
            val sink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                .build()
            val list = ArrayList<Renderer>()
            if (video) {
                list += MediaCodecVideoRenderer(
                    context, MediaCodecSelector.DEFAULT, 5_000L, handler, videoListener, 50
                )
            }
            list += MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, audioListener, sink)
            if (video) list += TextRenderer(textOutput, handler.looper)
            list.toTypedArray()
        }

        val player = ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(if (video) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(if (video) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.setAudioSessionId(audioSessionId)
        return player
    }
}
