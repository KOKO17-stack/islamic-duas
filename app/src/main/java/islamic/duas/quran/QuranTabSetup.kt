package islamic.duas.quran

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import islamic.duas.R

class QuranTabSetup(private val activity: Activity) {

    private var audioManager: QuranAudioManager? = null
    private var bookmarkManager: QuranBookmarkManager? = null
    private var adapter: QuranAdapter? = null
    private var currentSurahView: SurahContentView? = null

    fun setup(root: View) {
        audioManager = QuranAudioManager(activity)
        bookmarkManager = QuranBookmarkManager(activity)

        QuranData.loadFromAssets(activity)

        val searchInput = root.findViewById<EditText>(R.id.quranSearchInput)
        val recyclerView = root.findViewById<RecyclerView>(R.id.quranRecyclerView)
        val mushafTab = root.findViewById<TextView>(R.id.mushafTab)
        val revelationTab = root.findViewById<TextView>(R.id.revelationTab)
        val juzPrev = root.findViewById<TextView>(R.id.juzPrev)
        val juzNext = root.findViewById<TextView>(R.id.juzNext)
        val juzLabel = root.findViewById<TextView>(R.id.juzLabel)
        val emptyView = root.findViewById<TextView>(R.id.quranEmptyView)
        val fragmentContainer = activity.findViewById<View>(R.id.surahFragmentContainer)

        adapter = QuranAdapter(activity, QuranData.surahs, audioManager!!, bookmarkManager!!) { surahNumber ->
            currentSurahView?.close()
            currentSurahView = SurahContentView(fragmentContainer as android.view.ViewGroup, surahNumber) {
                currentSurahView = null
            }
            currentSurahView?.show()
        }
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter?.filter(s?.toString() ?: "")
                emptyView.visibility = if (adapter?.itemCount == 0) View.VISIBLE else View.GONE
            }
        })

        var useRevelation = false
        fun updateOrderTabs() {
            mushafTab.setBackgroundResource(if (!useRevelation) R.drawable.chip_selected else R.drawable.chip_unselected)
            mushafTab.setTextColor(if (!useRevelation) 0xFFD4AF37.toInt() else 0xFF5A6A7A.toInt())
            revelationTab.setBackgroundResource(if (useRevelation) R.drawable.chip_selected else R.drawable.chip_unselected)
            revelationTab.setTextColor(if (useRevelation) 0xFFD4AF37.toInt() else 0xFF5A6A7A.toInt())
        }
        updateOrderTabs()

        mushafTab.setOnClickListener {
            if (useRevelation) {
                useRevelation = false
                adapter?.setRevelationOrder(false)
                updateOrderTabs()
            }
        }
        revelationTab.setOnClickListener {
            if (!useRevelation) {
                useRevelation = true
                adapter?.setRevelationOrder(true)
                updateOrderTabs()
            }
        }

        var currentJuz = 1
        fun updateJuzLabel() { juzLabel.text = "الجزء $currentJuz" }
        updateJuzLabel()

        juzPrev.setOnClickListener {
            if (currentJuz > 1) {
                currentJuz--
                updateJuzLabel()
                val target = QuranData.surahs.find { s ->
                    s.juzAyahStarts.containsKey(currentJuz) || s.number >= (currentJuz * 2 + 14)
                }
                target?.let { s ->
                    val idx = adapter?.getOrderedList()?.indexOfFirst { it.number == s.number } ?: -1
                    if (idx >= 0) recyclerView.smoothScrollToPosition(idx)
                }
            }
        }
        juzNext.setOnClickListener {
            if (currentJuz < 30) {
                currentJuz++
                updateJuzLabel()
                val target = QuranData.surahs.find { s ->
                    s.juzAyahStarts.containsKey(currentJuz) || s.number >= (currentJuz * 2 + 14)
                }
                target?.let { s ->
                    val idx = adapter?.getOrderedList()?.indexOfFirst { it.number == s.number } ?: -1
                    if (idx >= 0) recyclerView.smoothScrollToPosition(idx)
                }
            }
        }
    }

    fun onBackPressed(): Boolean {
        return if (currentSurahView != null) {
            currentSurahView?.close()
            currentSurahView = null
            true
        } else false
    }

    fun onDestroy() {
        currentSurahView?.close()
        currentSurahView = null
        adapter?.cleanup()
        audioManager?.stop()
    }
}