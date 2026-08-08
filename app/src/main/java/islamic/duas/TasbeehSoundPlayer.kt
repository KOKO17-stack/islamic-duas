package islamic.duas

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator

class TasbeehSoundPlayer {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {}
    }

    fun playSound(sound: Int) {
        try {
            when (sound) {
                SOUND_TAP -> toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                SOUND_MILESTONE -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                }
                SOUND_TARGET_REACHED -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 120)
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
    }

    companion object {
        const val SOUND_TAP = 1
        const val SOUND_MILESTONE = 2
        const val SOUND_TARGET_REACHED = 3
    }
}
