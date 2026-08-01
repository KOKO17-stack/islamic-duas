package islamic.duas.calendar

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import islamic.duas.R
import islamic.duas.haidh.CycleDao
import islamic.duas.haidh.IstihadaType
import islamic.duas.haidh.MenstrualStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CELL_HEIGHT_DP = 34
private const val CELL_GAP_DP = 1
private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private const val TAG = "ContinuousTimeline"

class ContinuousTimelineBuilder(
    private val context: Context,
    private val daoProvider: () -> CycleDao,
    private val onDayClick: (year: Int, month: Int, day: Int) -> Unit,
    private val onDayLongClick: (year: Int, month: Int, day: Int) -> Unit
) {
    private val buildMutex = Mutex()

    private val urduDayNames = arrayOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ")
    private val urduMonthNames = arrayOf(
        "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
        "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
    )

    suspend fun build(
        container: LinearLayout,
        selectedYear: Int?,
        selectedMonth: Int?,
        selectedDay: Int?
    ) = buildMutex.withLock {
        try {
            val weeks = withContext(Dispatchers.IO) { generateData() }

            withContext(Dispatchers.Main) {
                container.removeAllViews()

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 22)
                    )
                }
                for (dayName in urduDayNames) {
                    headerRow.addView(TextView(context).apply {
                        text = dayName
                        textSize = 10f
                        setTextColor(ContextCompat.getColor(context, R.color.scoreNeutral))
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dp(context, 22), 1f)
                    })
                }
                container.addView(headerRow)

                var currentMonth = -1
                var currentYear = -1

                for (week in weeks) {
                    if (week.any { it != null && (it.year != currentYear || it.month != currentMonth) }) {
                        val firstNonNull = week.firstOrNull { it != null }
                        if (firstNonNull != null && (firstNonNull.year != currentYear || firstNonNull.month != currentMonth)) {
                            currentYear = firstNonNull.year
                            currentMonth = firstNonNull.month
                            val monthLabel = "${urduMonthNames.getOrElse(currentMonth - 1) { "$currentMonth" }} $currentYear"
                            container.addView(createMonthHeader(monthLabel))
                        }
                    }

                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    for (dayInfo in week) {
                        if (dayInfo == null) {
                            val emptyCell = View(context)
                            emptyCell.layoutParams = LinearLayout.LayoutParams(
                                0, dp(context, CELL_HEIGHT_DP), 1f
                            ).apply { setMargins(dp(context, CELL_GAP_DP), 0, dp(context, CELL_GAP_DP), 0) }
                            val bg = android.graphics.drawable.GradientDrawable()
                            bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            bg.setColor(0xFF12183A.toInt())
                            bg.setStroke(1, 0xFF1E293B.toInt())
                            bg.setCornerRadius(dp(context, 2).toFloat())
                            emptyCell.background = bg
                            rowLayout.addView(emptyCell)
                        } else {
                            val isThisSelected = selectedDay == dayInfo.day &&
                                    selectedYear == dayInfo.year &&
                                    selectedMonth == dayInfo.month
                            val cell = createDayCell(dayInfo, isThisSelected)
                            cell.setOnClickListener {
                                onDayClick(dayInfo.year, dayInfo.month, dayInfo.day)
                            }
                            cell.setOnLongClickListener {
                                onDayLongClick(dayInfo.year, dayInfo.month, dayInfo.day)
                                true
                            }
                            rowLayout.addView(cell)
                        }
                    }
                    container.addView(rowLayout)
                }

                container.requestLayout()
                container.invalidate()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "build failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "کیلنڈر لوڈ کرنے میں مسئلہ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private data class DayInfo(
        val year: Int,
        val month: Int,
        val day: Int,
        val isToday: Boolean,
        val isFuture: Boolean,
        val status: MenstrualStatus?,
        val flowIntensity: Int?,
        val istihadaType: IstihadaType?,
        val hasSymptoms: Boolean,
        val isPredictedHaidh: Boolean
    )

    private suspend fun generateData(): List<List<DayInfo?>> {
        val freshDao = daoProvider()
        val today = LocalDate.now()
        val startDate = today.minusDays(119)
        val gridStart = startDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))

        val startStr = gridStart.format(dateFormatter)
        val endStr = today.format(dateFormatter)

        val entriesMap = freshDao.getCycleRange(startStr, endStr)
            .associateBy { it.date }
        val phasesMap = freshDao.getPhasesInRange(startStr, endStr)
            .associateBy { it.startDate }

        val dayDataList = mutableListOf<DayInfo>()
        var scanDate = gridStart
        while (!scanDate.isAfter(today)) {
            val ds = scanDate.format(dateFormatter)
            val entry = entriesMap[ds]
            val phase = phasesMap[ds]

            dayDataList.add(DayInfo(
                year = scanDate.year,
                month = scanDate.monthValue,
                day = scanDate.dayOfMonth,
                isToday = scanDate == today,
                isFuture = scanDate.isAfter(today),
                status = entry?.status,
                flowIntensity = entry?.flowIntensity,
                istihadaType = entry?.istihadaType,
                hasSymptoms = entry?.symptoms?.isNotBlank() == true,
                isPredictedHaidh = phase != null && phase.status == MenstrualStatus.HAIDH && entry == null && !scanDate.isAfter(today)
            ))
            scanDate = scanDate.plusDays(1)
        }

        val weeks = mutableListOf<List<DayInfo?>>()
        val filteredDays = dayDataList.filter { !it.isFuture }
        var i = 0
        while (i < filteredDays.size) {
            val week = mutableListOf<DayInfo?>()
            for (w in 0 until 7) {
                if (i < filteredDays.size) {
                    week.add(filteredDays[i])
                    i++
                } else {
                    week.add(null)
                }
            }
            weeks.add(week)
        }

        return weeks
    }

    private fun createDayCell(dayInfo: DayInfo, isSelected: Boolean): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(context, CELL_HEIGHT_DP), 1f).apply {
                setMargins(dp(context, CELL_GAP_DP), 0, dp(context, CELL_GAP_DP), 0)
            }
            setPadding(dp(context, 1), dp(context, 1), dp(context, 1), dp(context, 1))
            background = createCellBg(dayInfo, isSelected)
        }

        val dayNum = TextView(context).apply {
            text = dayInfo.day.toString()
            textSize = if (dayInfo.isToday) 13f else 11f
            gravity = Gravity.CENTER
            setTypeface(null, if (dayInfo.isToday) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(getTextColor(dayInfo, isSelected))
        }
        cell.addView(dayNum)

        return cell
    }

    private fun createCellBg(dayInfo: DayInfo, isSelected: Boolean): android.graphics.drawable.Drawable {
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bg.setCornerRadius(dp(context, 3).toFloat())

        val isIstihada = dayInfo.istihadaType != null && dayInfo.istihadaType != IstihadaType.NONE

        when {
            isSelected -> {
                bg.setColor(getStatusColor(dayInfo, isIstihada))
                bg.setStroke(2, 0xFFD4AF37.toInt())
            }
            dayInfo.isToday -> {
                bg.setStroke(2, 0xFFD4AF37.toInt())
                bg.setColor(getStatusColor(dayInfo, isIstihada))
            }
            dayInfo.isPredictedHaidh -> {
                bg.setColor(0xFF12183A.toInt())
                bg.setStroke(2, 0xFF8B0000.toInt())
            }
            isIstihada -> {
                bg.setColor(0xFF8B6508.toInt())
            }
            dayInfo.status == MenstrualStatus.HAIDH -> {
                bg.setColor(getHaidhShade(dayInfo.flowIntensity))
            }
            else -> {
                bg.setColor(0xFF12183A.toInt())
                bg.setStroke(1, 0xFF1E293B.toInt())
            }
        }
        return bg
    }

    private fun getStatusColor(dayInfo: DayInfo, isIstihada: Boolean): Int {
        return when {
            isIstihada -> 0xFF8B6508.toInt()
            dayInfo.status == MenstrualStatus.HAIDH -> getHaidhShade(dayInfo.flowIntensity)
            else -> 0xFF12183A.toInt()
        }
    }

    private fun getHaidhShade(flowIntensity: Int?): Int {
        return when (flowIntensity) {
            1 -> 0xFF6B0000.toInt()
            2 -> 0xFF8B0000.toInt()
            3 -> 0xFFB22222.toInt()
            else -> 0xFF8B0000.toInt()
        }
    }

    private fun getTextColor(dayInfo: DayInfo, isSelected: Boolean): Int {
        val isIstihada = dayInfo.istihadaType != null && dayInfo.istihadaType != IstihadaType.NONE
        return when {
            isSelected || dayInfo.isToday -> ContextCompat.getColor(context, R.color.primary_gold)
            isIstihada -> ContextCompat.getColor(context, R.color.muted_gold)
            dayInfo.isPredictedHaidh -> ContextCompat.getColor(context, R.color.muted_gold)
            dayInfo.status == MenstrualStatus.HAIDH -> ContextCompat.getColor(context, android.R.color.white)
            else -> ContextCompat.getColor(context, R.color.lightNeutral)
        }
    }

    private fun createMonthHeader(label: String): View {
        return TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.primary_gold))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 6)
                bottomMargin = dp(context, 2)
            }
            setBackgroundColor(0x1A000000.toInt())
        }
    }

    companion object {
        fun dp(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        }
    }
}
