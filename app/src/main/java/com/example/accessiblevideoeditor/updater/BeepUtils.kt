package com.example.accessiblevideoeditor.updater

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

object BeepUtils {
    fun playProgressBeep(percent: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val durationMs = 150
                val sampleRate = 44100
                val numSamples = durationMs * sampleRate / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ByteArray(2 * numSamples)

                val freqOfTone = 400.0 + (percent / 100.0) * 1600.0

                for (i in 0 until numSamples) {
                    sample[i] = sin(2 * Math.PI * i / (sampleRate / freqOfTone))
                }
                var idx = 0
                for (dVal in sample) {
                    val val16 = (dVal * 32767).toInt().toShort()
                    generatedSnd[idx++] = (val16.toInt() and 0x00ff).toByte()
                    generatedSnd[idx++] = ((val16.toInt() and 0xff00) ushr 8).toByte()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    generatedSnd.size,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()
                delay(durationMs.toLong() + 50)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
