package islamic.duas.quran

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

class QuranAudioManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSurahNumber: Int = -1
    private var currentReciter: String = RECITER_SUDAIS
    private var isPrepared = false
    private val handler = Handler(Looper.getMainLooper())
    private var progressCallback: ((Int, Int) -> Unit)? = null
    private var completionCallback: (() -> Unit)? = null

    companion object {
        const val RECITER_SUDAIS = "sudais"
        const val RECITER_AFASY = "afasy"
        const val RECITER_ABDUL_BASIT = "abdul_baset"

        val reciters = listOf(
            ReciterInfo("السدیس", RECITER_SUDAIS),
            ReciterInfo("العفاسی", RECITER_AFASY),
            ReciterInfo("عبدالباسط", RECITER_ABDUL_BASIT)
        )

        data class ReciterInfo(val name: String, val id: String)

        private val AUDIO_BASE = "https://download.quranicaudio.com/qdc"
        private val reciterPaths = mapOf(
            RECITER_SUDAIS to "abdurrahmaan_as_sudais/murattal",
            RECITER_AFASY to "mishari_al_afasy/murattal",
            RECITER_ABDUL_BASIT to "abdul_baset/murattal",
        )
    }

    fun getReciterName(): String = reciters.find { it.id == currentReciter }?.name ?: "السدیس"

    fun getReciterIndex(): Int {
        val idx = reciters.indexOfFirst { it.id == currentReciter }
        return if (idx < 0) 0 else idx
    }

    fun setReciter(reciterId: String): Boolean {
        if (reciters.any { it.id == reciterId }) {
            currentReciter = reciterId
            return true
        }
        return false
    }

    fun cycleReciter(): String {
        val idx = getReciterIndex()
        val next = (idx + 1) % reciters.size
        currentReciter = reciters[next].id
        return reciters[next].name
    }

    fun cycleReciterBackward(): String {
        val idx = getReciterIndex()
        val prev = if (idx - 1 < 0) reciters.size - 1 else idx - 1
        currentReciter = reciters[prev].id
        return reciters[prev].name
    }

    fun playSurah(surahNumber: Int, onProgress: ((Int, Int) -> Unit)? = null, onComplete: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        stop()
        currentSurahNumber = surahNumber
        progressCallback = onProgress
        completionCallback = onComplete
        isPrepared = false

        val path = reciterPaths[currentReciter] ?: reciterPaths[RECITER_SUDAIS] ?: "abdurrahmaan_as_sudais/murattal"
        val url = "$AUDIO_BASE/$path/$surahNumber.mp3"
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build())
                setOnPreparedListener { mp ->
                    isPrepared = true
                    mp.start()
                    startProgressUpdates()
                }
                setOnCompletionListener {
                    val cb = completionCallback
                    stop()
                    cb?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    stop()
                    onError?.invoke("MediaPlayer error: what=$what extra=$extra")
                    true
                }
                setDataSource(url)
                prepareAsync()
            }
        } catch (e: Exception) {
            stop()
            onError?.invoke("Failed to start playback: ${e.message}")
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressUpdates()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying && isPrepared) {
                it.start()
                startProgressUpdates()
            }
        }
    }

    fun stop() {
        stopProgressUpdates()
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                reset()
                release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        isPrepared = false
        currentSurahNumber = -1
        progressCallback = null
        completionCallback = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun isPaused(): Boolean = mediaPlayer != null && !mediaPlayer!!.isPlaying && isPrepared

    fun getCurrentSurah(): Int = currentSurahNumber

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    private fun startProgressUpdates() {
        progressRunnable.run()
    }

    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    progressCallback?.invoke(mp.currentPosition, mp.duration)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    fun getCacheDir(): File = File(context.cacheDir, "quran_audio").also { it.mkdirs() }
}
