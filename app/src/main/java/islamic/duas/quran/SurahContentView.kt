package islamic.duas.quran

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import islamic.duas.R

class SurahContentView(
    private val container: ViewGroup,
    private val surahNumber: Int,
    private val onClose: () -> Unit
) {
    private lateinit var root: View
    private val surah = QuranData.getSurah(surahNumber) ?: QuranData.surahs.first()
    private val audioManager = QuranAudioManager(container.context)
    private val bookmarkManager = QuranBookmarkManager(container.context)

    private var currentViewMode = QuranAdapter.ViewMode.BOTH
    private var currentTranslation = QuranAdapter.TranslationType.JALANDHARI
    private var textSizeArabic = 20f
    private var textSizeUrdu = 15f
    private var spinnerUpdating = false

    fun show() {
        root = LayoutInflater.from(container.context).inflate(R.layout.fragment_surah_content, container, false)
        container.removeAllViews()
        container.addView(root)
        container.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(container.context, android.R.anim.slide_in_left)
        root.startAnimation(anim)
        setupViews()
    }

    private fun setupViews() {
        val backBtn = root.findViewById<TextView>(R.id.backBtn)
        val surahTitle = root.findViewById<TextView>(R.id.surahTitle)
        val surahNumberView = root.findViewById<TextView>(R.id.surahNumber)
        val viewModeArabic = root.findViewById<TextView>(R.id.expViewModeArabic)
        val viewModeTranslation = root.findViewById<TextView>(R.id.expViewModeTranslation)
        val viewModeBoth = root.findViewById<TextView>(R.id.expViewModeBoth)
        val transJalandhari = root.findViewById<TextView>(R.id.expTransJalandhari)
        val transMaududi = root.findViewById<TextView>(R.id.expTransMaududi)
        val textSizeMinus = root.findViewById<TextView>(R.id.expTextSizeMinus)
        val textSizePlus = root.findViewById<TextView>(R.id.expTextSizePlus)
        val textSizeLabel = root.findViewById<TextView>(R.id.expTextSizeLabel)
        val reciterLabel = root.findViewById<TextView>(R.id.reciterLabel)
        val reciterSpinner = root.findViewById<Spinner>(R.id.reciterSpinner)
        val reciterPrev = root.findViewById<TextView>(R.id.reciterPrev)
        val reciterNext = root.findViewById<TextView>(R.id.reciterNext)
        val audioPlayBtn = root.findViewById<TextView>(R.id.audioPlayBtn)
        val audioProgress = root.findViewById<TextView>(R.id.audioProgress)
        val textContainer = root.findViewById<LinearLayout>(R.id.verseContainer)
        val tafsirToggle = root.findViewById<TextView>(R.id.tafsirToggle)
        val tafsirScroll = root.findViewById<ScrollView>(R.id.tafsirScroll)
        val tafsirText = root.findViewById<TextView>(R.id.tafsirText)

        surahTitle.text = surah.arabicName
        surahNumberView.text = surah.number.toString()

        backBtn.setOnClickListener { close() }

        setupReciterSpinner(reciterSpinner, reciterLabel)
        reciterPrev.setOnClickListener {
            val idx = if (audioManager.getReciterIndex() <= 0)
                QuranAudioManager.reciters.size - 1
            else
                audioManager.getReciterIndex() - 1
            val reciter = QuranAudioManager.reciters[idx]
            audioManager.setReciter(reciter.id)
            reciterLabel.text = "قاری: ${reciter.name}"
            spinnerUpdating = true
            reciterSpinner.setSelection(idx)
            spinnerUpdating = false
        }
        reciterNext.setOnClickListener {
            val idx = (audioManager.getReciterIndex() + 1) % QuranAudioManager.reciters.size
            val reciter = QuranAudioManager.reciters[idx]
            audioManager.setReciter(reciter.id)
            reciterLabel.text = "قاری: ${reciter.name}"
            spinnerUpdating = true
            reciterSpinner.setSelection(idx)
            spinnerUpdating = false
        }

        val PAGE_SIZE = 50
        var renderedCount = 0

        fun renderPage(startIdx: Int) {
            val translations = when (currentTranslation) {
                QuranAdapter.TranslationType.JALANDHARI -> surah.urduJalandhari
                QuranAdapter.TranslationType.MAUDUDI -> surah.urduMaududi
            }
            val showArabic = currentViewMode == QuranAdapter.ViewMode.ARABIC || currentViewMode == QuranAdapter.ViewMode.BOTH
            val showTr = currentViewMode == QuranAdapter.ViewMode.TRANSLATION || currentViewMode == QuranAdapter.ViewMode.BOTH
            val endIdx = minOf(startIdx + PAGE_SIZE, surah.arabicVerses.size)

            for (idx in startIdx until endIdx) {
                val verse = surah.arabicVerses[idx]
                val isSajdah = surah.sajdahAyahs.contains(idx + 1)

                if (showArabic) {
                    val ayahView = TextView(container.context).apply {
                        text = "$verse (${idx + 1})"
                        textSize = textSizeArabic
                        setTextColor(0xFFE8C547.toInt())
                        gravity = Gravity.END
                        typeface = try {
                            ResourcesCompat.getFont(context, R.font.scheherazade_new)
                        } catch (_: Exception) { Typeface.DEFAULT }
                        setLineSpacing(0f, 1.4f)
                        setPadding(0, 6, 0, 2)
                        if (isSajdah) setBackgroundColor(0x332A6A3A.toInt())
                    }
                    textContainer.addView(ayahView)

                    if (isSajdah) {
                        val sajdahLabel = TextView(container.context).apply {
                            text = "🕌 سجدہ (آیت ${idx + 1})"
                            textSize = 11f
                            setTextColor(0xFF8FBF8A.toInt())
                            gravity = Gravity.END
                            setPadding(0, 0, 0, 4)
                        }
                        textContainer.addView(sajdahLabel)
                    }
                }

                if (showTr) {
                    val trView = TextView(container.context).apply {
                        text = translations.getOrElse(idx) { "" }
                        textSize = textSizeUrdu
                        setTextColor(0xFFEDE8E0.toInt())
                        typeface = try {
                            ResourcesCompat.getFont(context, R.font.noto_nastaliq_urdu)
                        } catch (_: Exception) { Typeface.DEFAULT }
                        setLineSpacing(0f, 1.3f)
                        setPadding(0, 2, 0, 8)
                        gravity = Gravity.START
                    }
                    textContainer.addView(trView)
                }
                renderedCount++
            }

            if (endIdx < surah.arabicVerses.size) {
                val loadMore = TextView(container.context).apply {
                    text = "➕ مزید آیات ($renderedCount/${surah.arabicVerses.size})"
                    textSize = 13f
                    setTextColor(0xFFD4AF37.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 10)
                    setBackgroundResource(R.drawable.rounded_bg)
                    setOnClickListener { renderPage(endIdx) }
                }
                textContainer.addView(loadMore)
            }
        }

        fun renderText() {
            textContainer.removeAllViews()
            textSizeLabel.text = "عربی $textSizeArabic | اردو $textSizeUrdu"
            renderedCount = 0
            renderPage(0)
        }

        renderText()

        viewModeArabic.setOnClickListener {
            currentViewMode = QuranAdapter.ViewMode.ARABIC
            updateViewModeChips(viewModeArabic, viewModeTranslation, viewModeBoth)
            renderText()
        }
        viewModeTranslation.setOnClickListener {
            currentViewMode = QuranAdapter.ViewMode.TRANSLATION
            updateViewModeChips(viewModeArabic, viewModeTranslation, viewModeBoth)
            renderText()
        }
        viewModeBoth.setOnClickListener {
            currentViewMode = QuranAdapter.ViewMode.BOTH
            updateViewModeChips(viewModeArabic, viewModeTranslation, viewModeBoth)
            renderText()
        }

        transJalandhari.setOnClickListener {
            currentTranslation = QuranAdapter.TranslationType.JALANDHARI
            updateTranslationChips(transJalandhari, transMaududi)
            renderText()
        }
        transMaududi.setOnClickListener {
            currentTranslation = QuranAdapter.TranslationType.MAUDUDI
            updateTranslationChips(transJalandhari, transMaududi)
            renderText()
        }

        textSizeMinus.setOnClickListener {
            if (textSizeArabic > 14) textSizeArabic -= 1
            if (textSizeUrdu > 11) textSizeUrdu -= 1
            renderText()
        }
        textSizePlus.setOnClickListener {
            if (textSizeArabic < 32) textSizeArabic += 1
            if (textSizeUrdu < 24) textSizeUrdu += 1
            renderText()
        }

        audioPlayBtn.setOnClickListener {
            if (audioManager.getCurrentSurah() == surah.number && audioManager.isPlaying()) {
                audioManager.pause()
                audioPlayBtn.text = "▶ چلائیں"
            } else if (audioManager.isPaused() && audioManager.getCurrentSurah() == surah.number) {
                audioManager.resume()
                audioPlayBtn.text = "⏸ روکیں"
            } else {
                audioManager.playSurah(surah.number,
                    onProgress = { pos, dur ->
                        audioProgress.text = "${pos / 60000}:${String.format("%02d", (pos % 60000) / 1000)} / ${dur / 60000}:${String.format("%02d", (dur % 60000) / 1000)}"
                        audioPlayBtn.text = "⏸ روکیں"
                    },
                    onComplete = {
                        audioPlayBtn.text = "▶ چلائیں"
                        audioProgress.text = "--:-- / --:--"
                    },
                    onError = {
                        audioPlayBtn.text = "▶ چلائیں"
                        audioProgress.text = "خطا"
                    }
                )
                audioPlayBtn.text = "⏺ شروع"
            }
        }

        tafsirToggle.setOnClickListener {
            val isVisible = tafsirScroll.visibility == View.VISIBLE
            tafsirScroll.visibility = if (isVisible) View.GONE else View.VISIBLE
            tafsirToggle.text = if (isVisible) "📖 تفسیر دیکھیں" else "📖 تفسیر چھپائیں"
        }
        tafsirText.text = surah.tafsirBrief
    }

    private fun setupReciterSpinner(spinner: Spinner, label: TextView) {
        val names = QuranAudioManager.reciters.map { it.name }
        spinner.adapter = ArrayAdapter(container.context, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(audioManager.getReciterIndex())
        label.text = "قاری: ${QuranAudioManager.reciters[audioManager.getReciterIndex()].name}"

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (spinnerUpdating) return
                if (pos in QuranAudioManager.reciters.indices) {
                    val reciter = QuranAudioManager.reciters[pos]
                    audioManager.setReciter(reciter.id)
                    label.text = "قاری: ${reciter.name}"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateViewModeChips(arabic: TextView, trans: TextView, both: TextView) {
        val s = 0xFFD4AF37.toInt()
        val u = 0xFF5A6A7A.toInt()
        arabic.setTextColor(if (currentViewMode == QuranAdapter.ViewMode.ARABIC) s else u)
        arabic.setBackgroundResource(if (currentViewMode == QuranAdapter.ViewMode.ARABIC) R.drawable.chip_selected else R.drawable.chip_unselected)
        trans.setTextColor(if (currentViewMode == QuranAdapter.ViewMode.TRANSLATION) s else u)
        trans.setBackgroundResource(if (currentViewMode == QuranAdapter.ViewMode.TRANSLATION) R.drawable.chip_selected else R.drawable.chip_unselected)
        both.setTextColor(if (currentViewMode == QuranAdapter.ViewMode.BOTH) s else u)
        both.setBackgroundResource(if (currentViewMode == QuranAdapter.ViewMode.BOTH) R.drawable.chip_selected else R.drawable.chip_unselected)
    }

    private fun updateTranslationChips(jalandhari: TextView, maududi: TextView) {
        val s = 0xFFD4AF37.toInt()
        val u = 0xFF5A6A7A.toInt()
        jalandhari.setTextColor(if (currentTranslation == QuranAdapter.TranslationType.JALANDHARI) s else u)
        jalandhari.setBackgroundResource(if (currentTranslation == QuranAdapter.TranslationType.JALANDHARI) R.drawable.chip_selected else R.drawable.chip_unselected)
        maududi.setTextColor(if (currentTranslation == QuranAdapter.TranslationType.MAUDUDI) s else u)
        maududi.setBackgroundResource(if (currentTranslation == QuranAdapter.TranslationType.MAUDUDI) R.drawable.chip_selected else R.drawable.chip_unselected)
    }

    fun close() {
        audioManager.stop()
        container.visibility = View.GONE
        container.removeAllViews()
        onClose()
    }
}