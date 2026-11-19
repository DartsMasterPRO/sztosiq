package com.melodia.player.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.melodia.player.playback.PlaybackService

/**
 * Przyciski widżetu: łączy się na chwilę z odtwarzaczem i wykonuje polecenie.
 * (Systemowy odbiornik przycisków multimedialnych na Androidzie 8+ obsługuje tylko „play”).
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val future = MediaController.Builder(
            app, SessionToken(app, ComponentName(app, PlaybackService::class.java))
        ).buildAsync()

        fun finish() {
            handler.postDelayed({
                MediaController.releaseFuture(future)
                runCatching { pending.finish() }
            }, 800)
        }

        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                runCatching { pending.finish() }
                return@addListener
            }
            // Po świeżym starcie usługa przywraca kolejkę – chwilę na nią czekamy.
            fun exec(attempt: Int) {
                if (c.mediaItemCount == 0 && attempt < 15) {
                    handler.postDelayed({ exec(attempt + 1) }, 200)
                    return
                }
                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        if (c.isPlaying) c.pause() else {
                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
                            c.play()
                        }
                    }
                    ACTION_NEXT -> c.seekToNext()
                    ACTION_PREVIOUS -> c.seekToPrevious()
                }
                finish()
            }
            exec(0)
        }, ContextCompat.getMainExecutor(app))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.melodia.player.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.melodia.player.widget.NEXT"
        const val ACTION_PREVIOUS = "com.melodia.player.widget.PREVIOUS"
    }
}
