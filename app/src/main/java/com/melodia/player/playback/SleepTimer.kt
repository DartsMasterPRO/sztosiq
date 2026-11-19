package com.melodia.player.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Wyłącznik czasowy: po X minutach (z płynnym wyciszeniem) albo po zakończeniu bieżącego utworu. */
object SleepTimer {
    private val scope = MainScope()
    private var job: Job? = null
    var player: ExoPlayer? = null

    /** true podczas wyciszania – wtedy serwis nie rusza głośności (crossfade). */
    @Volatile var fading = false
        private set

    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    private val _endOfTrack = MutableStateFlow(false)
    val endOfTrack: StateFlow<Boolean> = _endOfTrack.asStateFlow()

    fun start(minutes: Int) {
        cancel()
        val ms = minutes * 60_000L
        _endsAt.value = System.currentTimeMillis() + ms
        job = scope.launch {
            delay(ms)
            fadeOutAndPause()
            _endsAt.value = null
        }
    }

    private suspend fun fadeOutAndPause() {
        val p = player ?: return
        val startVolume = p.volume
        fading = true
        try {
            val steps = 20
            for (i in 1..steps) {
                p.volume = startVolume * (1f - i / steps.toFloat())
                delay(150)
            }
            p.pause()
        } finally {
            p.volume = startVolume
            fading = false
        }
    }

    fun startEndOfTrack() {
        cancel()
        player?.pauseAtEndOfMediaItems = true
        _endOfTrack.value = true
    }

    /** Wywoływane przez serwis, gdy odtwarzacz zatrzymał się na końcu utworu. */
    fun onEndOfTrackReached() {
        player?.pauseAtEndOfMediaItems = false
        _endOfTrack.value = false
    }

    fun cancel() {
        job?.cancel()
        job = null
        _endsAt.value = null
        if (_endOfTrack.value) onEndOfTrackReached()
    }
}
