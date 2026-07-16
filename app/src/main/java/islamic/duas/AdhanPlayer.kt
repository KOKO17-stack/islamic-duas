package islamic.duas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import android.os.Handler
import android.os.Looper

enum class AdhanMode { FULL, FIRST_TWO }

class AdhanPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var stopHandler: Handler? = null

    fun play(mode: AdhanMode = AdhanMode.FULL) {
        if (mode == AdhanMode.FIRST_TWO) {
            playWithCutoff()
        } else {
            playFull()
        }
    }

    private fun playFull() {
        stop()
        try {
            val resId = context.resources.getIdentifier("adhan", "raw", context.packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
                    prepare()
                    start()
                    setOnCompletionListener { stop() }
                    setOnErrorListener { _, _, _ -> fallbackBeep(); true }
                }
            } else {
                fallbackBeep()
            }
        } catch (_: Exception) {
            fallbackBeep()
        }
    }

    private fun playWithCutoff() {
        stop()
        try {
            val resId = context.resources.getIdentifier("adhan", "raw", context.packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
                    prepare()
                    start()
                    setOnCompletionListener { stop() }
                    setOnErrorListener { _, _, _ -> fallbackBeep(); true }
                }
                // Stop after ~18 seconds (first two verses: takbir + first shahadah)
                stopHandler = Handler(Looper.getMainLooper())
                stopHandler?.postDelayed({ stop() }, 18000)
            } else {
                fallbackBeep()
            }
        } catch (_: Exception) {
            fallbackBeep()
        }
    }

    private fun fallbackBeep() {
        Thread {
            var tg: ToneGenerator? = null
            try {
                tg = ToneGenerator(AudioManager.STREAM_ALARM, 85)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
                Thread.sleep(200)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
                Thread.sleep(200)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 600)
                Thread.sleep(200)
            } catch (_: Exception) {
            } finally {
                tg?.release()
            }
            vibratePattern()
        }.start()
    }

    private fun vibratePattern() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 400, 200, 400, 200, 800),
                intArrayOf(0, 255, 0, 255, 0, 255),
                -1
            ))
        } catch (_: Exception) {}
    }

    fun stop() {
        stopHandler?.removeCallbacksAndMessages(null)
        stopHandler = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
