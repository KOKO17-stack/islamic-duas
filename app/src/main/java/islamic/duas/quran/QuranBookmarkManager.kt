package islamic.duas.quran

import android.content.Context
import android.content.SharedPreferences

class QuranBookmarkManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quran_bookmarks", Context.MODE_PRIVATE)

    private val FAVORITE_SURAH_PREFIX = "fav_surah_"
    private val BOOKMARKED_AYAHS = "bookmarked_ayahs"

    fun toggleSurahFavorite(surahNumber: Int): Boolean {
        val key = "$FAVORITE_SURAH_PREFIX$surahNumber"
        val current = prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, !current).apply()
        return !current
    }

    fun isSurahFavorite(surahNumber: Int): Boolean =
        prefs.getBoolean("$FAVORITE_SURAH_PREFIX$surahNumber", false)

    fun getFavoriteSurahs(): List<Int> {
        return (1..114).filter { isSurahFavorite(it) }
    }

    fun toggleAyahBookmark(surahNumber: Int, ayahNumber: Int): Boolean {
        val set = getBookmarkedAyahs().toMutableSet()
        val key = "$surahNumber:$ayahNumber"
        return if (set.contains(key)) {
            set.remove(key)
            saveBookmarkedAyahs(set)
            false
        } else {
            set.add(key)
            saveBookmarkedAyahs(set)
            true
        }
    }

    fun isAyahBookmarked(surahNumber: Int, ayahNumber: Int): Boolean {
        val key = "$surahNumber:$ayahNumber"
        return getBookmarkedAyahs().contains(key)
    }

    fun getBookmarkedAyahsForSurah(surahNumber: Int): List<Int> {
        return getBookmarkedAyahs()
            .filter { it.startsWith("$surahNumber:") }
            .map { it.substringAfter(":").toInt() }
            .sorted()
    }

    fun getAllBookmarkedAyahs(): Map<Int, List<Int>> {
        val result = mutableMapOf<Int, MutableList<Int>>()
        getBookmarkedAyahs().forEach { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val s = parts[0].toIntOrNull() ?: return@forEach
                val a = parts[1].toIntOrNull() ?: return@forEach
                result.getOrPut(s) { mutableListOf() }.add(a)
            }
        }
        return result.mapValues { it.value.sorted() }
    }

    private fun getBookmarkedAyahs(): Set<String> =
        prefs.getStringSet(BOOKMARKED_AYAHS, emptySet()) ?: emptySet()

    private fun saveBookmarkedAyahs(set: Set<String>) {
        prefs.edit().putStringSet(BOOKMARKED_AYAHS, set).apply()
    }
}
