package islamic.duas.calendar

import islamic.duas.haidh.CycleDao
import islamic.duas.calendar.CyclePredictionEngine
import islamic.duas.haidh.IstihadaType
import islamic.duas.haidh.MenstrualStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HaidhCalendarAdapter(
    private val dao: CycleDao,
    private val onDayClickCallback: (year: Int, month: Int, day: Int) -> Unit,
    private val onDayLongClickCallback: (year: Int, month: Int, day: Int) -> Unit
) : CalendarAdapter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var currentFilter: String? = null

    fun setFilter(filter: String?) {
        currentFilter = filter
    }

    override suspend fun getDayData(year: Int, month: Int, day: Int): DayData? = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day)
        val dateStr = dateFormat.format(cal.time)

        val entry = dao.getDayStatus(dateStr)
        val phase = dao.getPhaseForDate(dateStr)

        val status = entry?.status
        val flowIntensity = entry?.flowIntensity
        val istihadaType = entry?.istihadaType
        val hasSymptoms = entry?.symptoms?.isNotBlank() == true
        val cycleDay = phase?.cycleDay

        // Determine if predicted Haidh
        val isPredictedHaidh = phase != null &&
            phase.status == MenstrualStatus.HAIDH &&
            entry == null &&
            !MonthNavigator.isFuture(year, month, day)

        // Filter check
        val filterActive = currentFilter != null
        val matchesFilter = !filterActive || when (currentFilter) {
            "had" -> status == MenstrualStatus.HAIDH
            "taharat" -> status == MenstrualStatus.TUHR && (istihadaType == null || istihadaType == IstihadaType.NONE)
            "istikhassa" -> istihadaType != null && istihadaType != IstihadaType.NONE
            else -> true
        }

        DayData(
            year = year,
            month = month,
            day = day,
            isToday = MonthNavigator.isToday(year, month, day),
            isFuture = MonthNavigator.isFuture(year, month, day),
            status = status,
            flowIntensity = flowIntensity,
            cycleDay = cycleDay,
            istihadaType = istihadaType,
            hasSymptoms = hasSymptoms,
            isPredictedHaidh = isPredictedHaidh,
            isDimmed = filterActive && !matchesFilter
        )
    }

    override suspend fun getMonthMeta(year: Int, month: Int): MonthMeta = withContext(Dispatchers.IO) {
        val avgCycleLength = dao.getAverageCycleLength()
        val avgHaidhLength = dao.getAverageHaidhLength()
        val lastHaidhEnd = dao.getLastHaidhEndDate()
        val predictedStart = if (avgCycleLength > 0 && avgHaidhLength > 0 && lastHaidhEnd != null) {
            CyclePredictionEngine.predictNextHaidhStart(
                lastHaidhEnd, avgCycleLength, avgHaidhLength
            )
        } else null

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
        onDayLongClickCallback(year, month, day)
    }
}