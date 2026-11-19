package com.melodia.player.playback

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * Analiza widma (FFT) i kształtu fali odtwarzanego dźwięku – dla wizualizacji.
 * Wymaga uprawnienia RECORD_AUDIO (Android tak nazywa dostęp do analizy dźwięku).
 */
object VisualizerController {
    const val BARS = 40

    private var viz: Visualizer? = null
    private var attachedSession = 0
    private val smooth = FloatArray(BARS)

    private val _bars = MutableStateFlow(FloatArray(BARS))
    val bars: StateFlow<FloatArray> = _bars.asStateFlow()

    private val _wave = MutableStateFlow(FloatArray(0))
    val wave: StateFlow<FloatArray> = _wave.asStateFlow()

    /** Energia basu 0..1 – do „pulsowania” okładki. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    fun start(sessionId: Int): Boolean {
        if (sessionId == 0) return false
        if (viz != null && attachedSession == sessionId) return true
        stop()
        return try {
            val v = Visualizer(sessionId)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(range[1].coerceAtMost(1024))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform != null) onWave(waveform)
                }

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) onFft(fft, samplingRate)
                }
            }, (Visualizer.getMaxCaptureRate() * 3 / 4), true, true)
            v.setEnabled(true)
            viz = v
            attachedSession = sessionId
            true
        } catch (_: Throwable) {
            viz = null
            false
        }
    }

    fun stop() {
        runCatching { viz?.setEnabled(false) }
        runCatching { viz?.release() }
        viz = null
        attachedSession = 0
        smooth.fill(0f)
        _bars.value = FloatArray(BARS)
        _level.value = 0f
    }

    private fun onFft(fft: ByteArray, samplingRateMilliHz: Int) {
        val n = fft.size / 2
        if (n < 4) return
        val sampleRate = samplingRateMilliHz / 1000f
        val binHz = sampleRate / fft.size
        val out = FloatArray(BARS)
        val fMin = 40f
        val fMax = (sampleRate / 2f).coerceAtMost(16000f)
        for (b in 0 until BARS) {
            val lo = fMin * (fMax / fMin).pow(b / BARS.toFloat())
            val hi = fMin * (fMax / fMin).pow((b + 1) / BARS.toFloat())
            var k0 = (lo / binHz).toInt().coerceIn(1, n - 1)
            val k1 = (hi / binHz).toInt().coerceIn(k0, n - 1)
            var peak = 0f
            while (k0 <= k1) {
                val re = fft[2 * k0].toFloat()
                val im = fft[2 * k0 + 1].toFloat()
                val mag = hypot(re, im)
                if (mag > peak) peak = mag
                k0++
            }
            val v = (ln(1f + peak) / ln(1f + 128f)).coerceIn(0f, 1f)
            smooth[b] = if (v > smooth[b]) v else smooth[b] * 0.82f + v * 0.18f
            out[b] = smooth[b]
        }
        _bars.value = out
        _level.value = (out[0] + out[1] + out[2] + out[3]) / 4f
    }

    private fun onWave(wave: ByteArray) {
        val points = 128
        val step = (wave.size / points).coerceAtLeast(1)
        val out = FloatArray(minOf(points, wave.size)) { i ->
            ((wave[i * step].toInt() and 0xFF) - 128) / 128f
        }
        _wave.value = out
    }
}
