package islamic.duas.quran

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import islamic.duas.R

class QuranAdapter(
    private val context: Context,
    private var surahList: List<QuranSurah>,
    private val audioManager: QuranAudioManager,
    private val bookmarkManager: QuranBookmarkManager,
    private val onSurahClick: (Int) -> Unit
) : RecyclerView.Adapter<QuranAdapter.SurahViewHolder>() {

    private var useRevelationOrder = false

    enum class TranslationType { JALANDHARI, MAUDUDI }
    enum class ViewMode { ARABIC, TRANSLATION, BOTH }

    private val revelationOrder = listOf(
        96,68,73,74,1,111,81,87,92,89,93,94,103,100,108,102,107,109,105,113,114,112,
        53,80,97,91,85,95,106,101,75,104,77,50,90,86,54,38,70,79,82,84,30,29,83,
        52,56,69,31,32,55,70,72,36,37,25,23,76,65,71,17,18,19,20,21,22,23,
        24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,
        48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72
    )

    fun getOrderedList(): List<QuranSurah> {
        return if (useRevelationOrder) {
            revelationOrder.mapNotNull { num -> QuranData.getSurah(num) }
        } else {
            QuranData.surahs
        }
    }

    fun setRevelationOrder(enabled: Boolean) {
        useRevelationOrder = enabled
        surahList = getOrderedList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        surahList = if (query.isBlank()) {
            getOrderedList()
        } else {
            getOrderedList().filter {
                it.transliteration.contains(query, true) ||
                it.urduName.contains(query, true) ||
                it.arabicName.contains(query)
            }
        }
        notifyDataSetChanged()
    }

    fun getGroupColor(group: Int): Int = QuranData.getGroupColor(group)

    override fun getItemCount(): Int = surahList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_surah, parent, false)
        return SurahViewHolder(view)
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
        val surah = surahList[position]
        holder.bind(surah)
    }

    inner class SurahViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val surahNumberBadge: TextView = itemView.findViewById(R.id.surahNumberBadge)
        private val surahArabicName: TextView = itemView.findViewById(R.id.surahArabicName)
        private val surahUrduName: TextView = itemView.findViewById(R.id.surahUrduName)
        private val surahRevelationBadge: TextView = itemView.findViewById(R.id.surahRevelationBadge)
        private val surahAyahCount: TextView = itemView.findViewById(R.id.surahAyahCount)
        private val surahDownloadBtn: TextView = itemView.findViewById(R.id.surahPlayBtn)
        private val surahBookmarkBtn: TextView = itemView.findViewById(R.id.surahBookmarkBtn)
        private val surahCard: View = itemView.findViewById(R.id.surahCard)

        fun bind(surah: QuranSurah) {
            val groupColor = getGroupColor(surah.groupNumber)
            try { surahCard.setBackgroundColor(groupColor) } catch (_: Exception) {}

            surahNumberBadge.text = surah.number.toString()
            surahArabicName.text = surah.arabicName
            surahUrduName.text = surah.urduName
            surahRevelationBadge.text = surah.revelationType
            surahAyahCount.text = "${surah.ayahCount} آیات"

            val isFav = bookmarkManager.isSurahFavorite(surah.number)
            surahBookmarkBtn.text = if (isFav) "★" else "☆"
            surahBookmarkBtn.setTextColor(
                if (isFav) 0xFFD4AF37.toInt() else 0xFF8B7355.toInt()
            )

            surahDownloadBtn.text = "⬇"

            itemView.setOnClickListener {
                onSurahClick(surah.number)
            }

            surahBookmarkBtn.setOnClickListener {
                val nowFav = bookmarkManager.toggleSurahFavorite(surah.number)
                surahBookmarkBtn.text = if (nowFav) "★" else "☆"
                surahBookmarkBtn.setTextColor(
                    if (nowFav) 0xFFD4AF37.toInt() else 0xFF8B7355.toInt()
                )
            }
        }
    }

    fun cleanup() {
        audioManager.stop()
    }
}