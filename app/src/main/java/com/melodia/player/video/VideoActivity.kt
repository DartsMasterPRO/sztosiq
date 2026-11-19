package com.melodia.player.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melodia.player.MelodiaApp
import com.melodia.player.ui.theme.MelodiaTheme

/**
 * Pełnoekranowy odtwarzacz filmów. Otwiera się z zakładki Filmy
 * albo z innych aplikacji (galeria, menedżer plików) przez „Otwórz za pomocą”.
 */
class VideoActivity : ComponentActivity() {
    private val vm: VideoViewModel by viewModels()
    private val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        addOnPictureInPictureModeChangedListener { info -> inPip.value = info.isInPictureInPictureMode }
        if (savedInstanceState == null) handleIntent(intent)

        val settings = (application as MelodiaApp).settings
        setContent {
            val ui by settings.ui.collectAsStateWithLifecycle()
            MelodiaTheme(ui, null) {
                VideoPlayerScreen(
                    vm = vm,
                    inPip = inPip.value,
                    onBack = { finish() },
                    onPip = { enterPip() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val ids = intent.getLongArrayExtra(EXTRA_IDS)
        when {
            ids != null && ids.isNotEmpty() -> vm.playLibrary(ids, intent.getIntExtra(EXTRA_INDEX, 0))
            intent.action == Intent.ACTION_VIEW && intent.data != null -> vm.playUri(intent.data!!)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun pipSupported() =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        val st = vm.state.value
        val ratio = if (st.width > 0 && st.height > 0) {
            val r = st.width.toFloat() / st.height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 0.42f -> Rational(42, 100)
                else -> Rational(st.width, st.height)
            }
        } else Rational(16, 9)
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(vm.state.value.isPlaying && !vm.backgroundAudio.value)
        return b.build()
    }

    fun enterPip() {
        if (!pipSupported()) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    /** Wyjście przyciskiem Home podczas odtwarzania → okienko PiP. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && vm.state.value.isPlaying && !vm.backgroundAudio.value) {
            enterPip()
        }
    }

    /** Wywoływane z ekranu przy zmianie stanu – Android 12+ sam przejdzie w PiP po wyjściu. */
    fun refreshPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pipSupported()) {
            runCatching { setPictureInPictureParams(pipParams()) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPipParams()
    }

    override fun onStop() {
        super.onStop()
        // Bez trybu „dźwięk w tle” i bez PiP – pauza, gdy film znika z ekranu.
        if (!isInPictureInPictureMode && !vm.backgroundAudio.value && !isChangingConfigurations) {
            vm.pause()
        }
    }

    companion object {
        const val EXTRA_IDS = "video_ids"
        const val EXTRA_INDEX = "video_index"

        fun open(context: Context, ids: LongArray, index: Int) {
            context.startActivity(
                Intent(context, VideoActivity::class.java)
                    .putExtra(EXTRA_IDS, ids)
                    .putExtra(EXTRA_INDEX, index)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
