package islamic.duas.calendar

import islamic.duas.haidh.HealthEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExerciseCalendarAdapter(
    private val healthEngine: HealthEngine,
    private val onDayClickCallback: (year: Int, month: Int, day: Int) -> Unit
) : CalendarAdapter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null
    private var selectedDay: Int? = null

    fun setSelectedDay(year: Int?, month: Int?, day: Int?) {
        selectedYear = year
        selectedMonth = month
        selectedDay = day
    }

    override suspend fun getDayData(year: Int, month: Int, day: Int): DayData? = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day)
        val dateStr = dateFormat.format(cal.time)

        val minutes = healthEngine.getExerciseMinutesForDate(dateStr)
        val steps = healthEngine.getStepsForDate(dateStr)

        // Streak check: part of a streak if they exercised today AND (yesterday or tomorrow)
        val yesterdayCal = Calendar.getInstance().apply {
            time = cal.time
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val tomorrowCal = Calendar.getInstance().apply {
            time = cal.time
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val yesterdayMins = healthEngine.getExerciseMinutesForDate(dateFormat.format(yesterdayCal.time))
        val tomorrowMins = healthEngine.getExerciseMinutesForDate(dateFormat.format(tomorrowCal.time))

        val isExercised = minutes > 0
        val isInStreak = isExercised && (yesterdayMins > 0 || tomorrowMins > 0)

        DayData(
            year = year,
            month = month,
            day = day,
            isToday = MonthNavigator.isToday(year, month, day),
            isFuture = MonthNavigator.isFuture(year, month, day),
            exerciseMinutes = if (isExercised) minutes else null,
            steps = if (steps > 0) steps else null,
            isInStreak = isInStreak,
            isSelected = year == selectedYear && month == selectedMonth && day == selectedDay
        )
    }

    override suspend fun getMonthMeta(year: Int, month: Int): MonthMeta = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val hijriMonthYear = MonthNavigator.getHijriMonthYearString(year, month, Locale("ur"))
        val parts = hijriMonthYear.split(" ")
        val hMonthName = if (parts.isNotEmpty()) parts.subList(0, parts.size - 1).joinToString(" ") else ""
        val hYear = if (parts.isNotEmpty()) parts.last().replace("ھ", "").toIntOrNull() ?: 1447 else 1447

        MonthMeta(
            currentYear = year,
            currentMonth = month,
            todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
            hijriMonthName = hMonthName,
            hijriYear = hYear
        )
    }

    override fun onDayClick(year: Int, month: Int, day: Int) {
        onDayClickCallback(year, month, day)
    }

    override fun onDayLongClick(year: Int, month: Int, day: Int) {
        // No-op for exercise calendar
    }
}