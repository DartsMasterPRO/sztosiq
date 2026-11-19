package com.melodia.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Własna obróbka dźwięku w ExoPlayerze (działa na każdym telefonie):
 *  - balans lewo/prawo,
 *  - wzmocnienie (ReplayGain / normalizacja),
 *  - soprany (filtr półkowy wysokich tonów, 3,5 kHz).
 */
@OptIn(UnstableApi::class)
class SoundProcessor : BaseAudioProcessor() {

    @Volatile var balance = 0f
    @Volatile var gainDb = 0f
    @Volatile var trebleDb = 0f

    private var coefTreble = Float.NaN
    private var coefRate = 0
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private val x1 = FloatArray(8)
    private val x2 = FloatArray(8)
    private val y1 = FloatArray(8)
    private val y2 = FloatArray(8)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount in 1..8) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        inputBuffer.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        val treble = trebleDb
        if (treble != coefTreble || rate != coefRate) computeTreble(treble, rate)
        val filter = abs(treble) > 0.05f

        val g = 10f.pow(gainDb / 20f)
        val bal = balance
        val gl = g * (if (bal > 0f) 1f - bal else 1f)
        val gr = g * (if (bal < 0f) 1f + bal else 1f)

        val out = replaceOutputBuffer(size)
        var ch = 0
        while (inputBuffer.remaining() >= 2) {
            var s = inputBuffer.short.toFloat()
            if (filter) {
                val y = b0 * s + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
                x2[ch] = x1[ch]; x1[ch] = s
                y2[ch] = y1[ch]; y1[ch] = y
                s = y
            }
            val gain = if (channels == 2) (if (ch == 0) gl else gr) else g
            out.putShort((s * gain).roundToInt().coerceIn(-32768, 32767).toShort())
            ch++
            if (ch == channels) ch = 0
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Współczynniki filtra półkowego (RBJ Audio EQ Cookbook, S = 1). */
    private fun computeTreble(db: Float, rate: Int) {
        coefTreble = db
        coefRate = rate
        if (rate <= 0) return
        val a = 10.0.pow(db / 40.0)
        val w0 = 2.0 * Math.PI * 3500.0 / rate
        val cosw = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val sq = 2.0 * sqrt(a) * alpha
        val nb0 = a * ((a + 1) + (a - 1) * cosw + sq)
        val nb1 = -2.0 * a * ((a - 1) + (a + 1) * cosw)
        val nb2 = a * ((a + 1) + (a - 1) * cosw - sq)
        val na0 = (a + 1) - (a - 1) * cosw + sq
        val na1 = 2.0 * ((a - 1) - (a + 1) * cosw)
        val na2 = (a + 1) - (a - 1) * cosw - sq
        b0 = (nb0 / na0).toFloat()
        b1 = (nb1 / na0).toFloat()
        b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat()
        a2 = (na2 / na0).toFloat()
    }

    private fun clearState() {
        x1.fill(0f); x2.fill(0f); y1.fill(0f); y2.fill(0f)
    }

    override fun onFlush() = clearState()

    override fun onReset() {
        clearState()
        coefTreble = Float.NaN
    }
}
