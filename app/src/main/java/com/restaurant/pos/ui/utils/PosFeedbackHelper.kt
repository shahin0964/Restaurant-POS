package com.restaurant.pos.ui.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object PosFeedbackHelper {

    /**
     * Triggers a short vibration / haptic click on the device
     */
    fun triggerVibration(context: Context, durationMs: Long = 70) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Plays a realistic POS receipt printer sound effect asynchronously:
     * Motor stepping printing buzz pulses followed by mechanical cutter snip
     */
    fun playReceiptPrinterSound(scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val durationSeconds = 1.35
                val numSamples = (sampleRate * durationSeconds).toInt()
                val buffer = ShortArray(numSamples)
                val random = Random(1234)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    var sample = 0.0

                    if (t < 1.05) {
                        // Printing phase: periodic thermal line prints & stepper motor
                        val linePeriod = 0.075
                        val linePhase = t % linePeriod
                        if (linePhase < 0.055) {
                            val stepFreq = 1100.0 + (i % 5) * 120.0
                            val motor = sin(2.0 * Math.PI * stepFreq * t) * 0.35
                            val buzz = ((i % 16) - 8) / 8.0 * 0.2
                            val texture = (random.nextDouble() * 2.0 - 1.0) * 0.18
                            sample = (motor + buzz + texture) * 0.7
                        } else {
                            val feedFreq = 520.0
                            val feed = sin(2.0 * Math.PI * feedFreq * t) * 0.15
                            sample = feed
                        }
                    } else if (t in 1.08..1.28) {
                        // Cutter snip sound (sharp metallic snap)
                        val cutT = t - 1.08
                        val cutFreq = 2600.0 * exp(-cutT * 22.0)
                        val metallic = sin(2.0 * Math.PI * cutFreq * t) * exp(-cutT * 18.0) * 0.6
                        val clickNoise = (random.nextDouble() * 2.0 - 1.0) * exp(-cutT * 30.0) * 0.5
                        sample = metallic + clickNoise
                    }

                    buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(maxOf(minBufferSize, buffer.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Release after playback finishes
                kotlinx.coroutines.delay((durationSeconds * 1000).toLong() + 200)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
