package islamic.duas.calendar

import islamic.duas.HijriCalendar
import java.util.Calendar
import java.util.Locale

object MonthNavigator {
    fun nextMonth(year: Int, month: Int): Pair<Int, Int> {
        var newMonth = month + 1
        var newYear = year
        if (newMonth > 12) { newMonth = 1; newYear++ }
        return newYear to newMonth
    }

    fun previousMonth(year: Int, month: Int): Pair<Int, Int> {
        var newMonth = month - 1
        var newYear = year
        if (newMonth < 1) { newMonth = 12; newYear-- }
        return newYear to newMonth
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun getFirstDayOfWeek(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
    }

    fun isToday(year: Int, month: Int, day: Int): Boolean {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == year &&
               cal.get(Calendar.MONTH) + 1 == month &&
               cal.get(Calendar.DAY_OF_MONTH) == day
    }

    fun isFuture(year: Int, month: Int, day: Int): Boolean {
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        cal.set(year, month - 1, day)
        return cal.after(today)
    }

    fun getHijriMonthYearString(gregorianYear: Int, gregorianMonth: Int, locale: Locale = Locale("ur")): String {
        val cal = Calendar.getInstance(locale)
        cal.set(gregorianYear, gregorianMonth - 1, 1)
        val hijri = HijriCalendar.toHijri(cal)
        val hYear = hijri.first
        val hMonth = hijri.second
        val hijriMonthNames = when (locale.language) {
            "ur" -> arrayOf(
                "محرم", "صفر", "ربیع الأول", "ربیع الآخر",
                "جمادی الأول", "جمادی الآخر", "رجب", "شعبان",
                "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
            )
            else -> arrayOf(
                "Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani",
                "Jumada al-awwal", "Jumada al-thani", "Rajab", "Sha'ban",
                "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
            )
        }
        val hMonthIndex: Int = hMonth - 1
        return "${hijriMonthNames[hMonthIndex]} ${hYear}ھ"
    }
}