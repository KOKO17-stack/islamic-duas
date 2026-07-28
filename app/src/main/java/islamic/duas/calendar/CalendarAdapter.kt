package islamic.duas.calendar

import islamic.duas.haidh.IstihadaType
import islamic.duas.haidh.MenstrualStatus
import java.util.Calendar

interface CalendarAdapter {
    suspend fun getDayData(year: Int, month: Int, day: Int): DayData? // suspend for background fetching
    suspend fun getMonthMeta(year: Int, month: Int): MonthMeta // suspend for background fetching
    fun onDayClick(year: Int, month: Int, day: Int)
    fun onDayLongClick(year: Int, month: Int, day: Int) // for Haidh quick-edit
}

data class DayData(
    val year: Int, val month: Int, val day: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val hijriDay: Int? = null,
    // Exercise-specific
    val exerciseMinutes: Int? = null,
    val steps: Int? = null,
    val isInStreak: Boolean = false,
    // Haidh-specific
    val status: MenstrualStatus? = null,
    val flowIntensity: Int? = null,  // 0=none, 1=light, 2=med, 3=heavy
    val cycleDay: Int? = null,
    val istihadaType: IstihadaType? = null,
    val hasSymptoms: Boolean = false,
    val isPredictedHaidh: Boolean = false, // For Haidh prediction
    val isDimmed: Boolean = false // For filter chips (dimmed = filtered out)
)

data class MonthMeta(
    val currentYear: Int, val currentMonth: Int, val todayDay: Int,
    val hasPrevMonth: Boolean = true, val hasNextMonth: Boolean = true,
    val hijriMonthName: String? = null, // Hijri month name for header
    val hijriYear: Int? = null // Hijri year for header
)