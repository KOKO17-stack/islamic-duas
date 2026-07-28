package islamic.duas.calendar

import islamic.duas.HijriCalendar
import java.util.Locale

data class CalendarConfig(
    val showHijriHeader: Boolean = true,
    val showHijriPerDay: Boolean = false,
    val urduMonthNames: Array<String> = arrayOf(
        "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
        "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
    ),
    val urduDayNames: Array<String> = arrayOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ"),
    val locale: Locale = Locale("ur")
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CalendarConfig
        if (showHijriHeader != other.showHijriHeader) return false
        if (showHijriPerDay != other.showHijriPerDay) return false
        if (!urduMonthNames.contentEquals(other.urduMonthNames)) return false
        if (!urduDayNames.contentEquals(other.urduDayNames)) return false
        if (locale != other.locale) return false
        return true
    }

    override fun hashCode(): Int {
        var result = showHijriHeader.hashCode()
        result = 31 * result + showHijriPerDay.hashCode()
        result = 31 * result + urduMonthNames.contentHashCode()
        result = 31 * result + urduDayNames.contentHashCode()
        result = 31 * result + locale.hashCode()
        return result
    }
}