package com.melodia.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import android.widget.RemoteViews
import com.melodia.player.MainActivity
import com.melodia.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Widżet na ekran główny: okładka, tytuł, poprzedni / play-pauza / następny. */
class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        push(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private var title: String? = null
        private var artist: String? = null
        private var playing = false
        private var art: Bitmap? = null
        private var artKey: Uri? = null
        private var artJob: Job? = null
        private val scope = MainScope()

        fun update(context: Context, title: String?, artist: String?, playing: Boolean, artUri: Uri?) {
            val ctx = context.applicationContext
            this.title = title
            this.artist = artist
            this.playing = playing
            if (artUri != artKey) {
                artKey = artUri
                art = null
                artJob?.cancel()
                if (artUri != null) {
                    artJob = scope.launch {
                        art = withContext(Dispatchers.IO) { loadArt(ctx, artUri) }
                        pushAll(ctx)
                    }
                }
            }
            pushAll(ctx)
        }

        private fun pushAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, MusicWidget::class.java))
            if (ids.isNotEmpty()) push(ctx, manager, ids)
        }

        private fun push(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.w_title, title ?: "SztosiQ")
            views.setTextViewText(R.id.w_artist, artist ?: "Dotknij, aby otworzyć")
            val bmp = art
            if (bmp != null) views.setImageViewBitmap(R.id.w_art, bmp)
            else views.setImageViewResource(R.id.w_art, R.drawable.ic_w_note)
            views.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)

            views.setOnClickPendingIntent(R.id.w_prev, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.w_play, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.w_next, mediaButton(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(
                    ctx, 0,
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            runCatching { manager.updateAppWidget(ids, views) }
        }

        /** Przyciski działają jak przyciski słuchawek – obsługuje je Media3 (także wznowienie). */
        private fun mediaButton(ctx: Context, keyCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(ctx.packageName, "androidx.media3.session.MediaButtonReceiver"))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            return PendingIntent.getBroadcast(
                ctx, keyCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun loadArt(ctx: Context, uri: Uri): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
}
