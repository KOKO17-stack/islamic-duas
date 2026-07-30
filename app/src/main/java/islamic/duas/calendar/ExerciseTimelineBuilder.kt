package islamic.duas.calendar

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import islamic.duas.haidh.HealthEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val CELL_HEIGHT_DP = 44
private const val CELL_GAP_DP = 1
private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class ExerciseTimelineBuilder(
    private val context: Context,
    private val healthEngine: HealthEngine,
    private val onSaveExercise: (minutes: Int) -> Unit
) {
    private val urduDayNames = arrayOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ")

    data class DayInfo(
        val year: Int,
        val month: Int,
        val day: Int,
        val isToday: Boolean,
        val minutes: Int,
        val steps: Int
    )

    fun build(
        container: LinearLayout,
        selectedYear: Int?,
        selectedMonth: Int?,
        selectedDay: Int?
    ) {
        try {
            container.removeAllViews()

            val today = LocalDate.now()
            val startDate = today.minusDays(29)

            // Offset: number of empty cells before startDate (0 = Sunday, 6 = Saturday)
            val offset = startDate.dayOfWeek.value % 7

            // Collect exactly 30 days of data starting from startDate
            val dayInfos = mutableListOf<DayInfo>()
            for (i in 0 until 30) {
                val date = startDate.plusDays(i.toLong())
                val dateStr = date.format(dateFormatter)
                val mins = healthEngine.getExerciseMinutesForDate(dateStr)
                val steps = healthEngine.getStepsForDate(dateStr)
                dayInfos.add(DayInfo(
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth,
                    isToday = date == today,
                    minutes = mins,
                    steps = steps
                ))
            }

            val totalCells = offset + dayInfos.size  // offset empty + 30 real
            val paddedTotal = if (totalCells % 7 == 0) totalCells else totalCells + (7 - totalCells % 7)

            // Day headers
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
                    setTextColor(0xFF8B7355.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(context, 22), 1f)
                })
            }
            container.addView(headerRow)

            // Week rows
            var cellIndex = 0
            var dayIndex = 0
            while (cellIndex < paddedTotal) {
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                for (w in 0 until 7) {
                    if (cellIndex < offset) {
                        // Empty cell before startDate
                        rowLayout.addView(createEmptyCell())
                        cellIndex++
                    } else if (dayIndex < dayInfos.size) {
                        val info = dayInfos[dayIndex]
                        val isSelected = selectedDay == info.day &&
                                selectedYear == info.year &&
                                selectedMonth == info.month
                        val cell = createDayCell(info, isSelected)
                        cell.setOnClickListener {
                            if (info.isToday) {
                                showSaveDialog()
                            } else {
                                if (info.minutes > 0) {
                                    Toast.makeText(context, "${info.day} ${info.month}/${info.year}: ${info.minutes} منٹ", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "${info.day} ${info.month}/${info.year}: کوئی ورزش نہیں", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        rowLayout.addView(cell)
                        cellIndex++
                        dayIndex++
                    } else {
                        // Pad empty cells to complete the last row
                        rowLayout.addView(createEmptyCell())
                        cellIndex++
                    }
                }
                container.addView(rowLayout)
            }

            container.requestLayout()
            container.invalidate()
        } catch (e: Exception) {
            Toast.makeText(context, "کیلنڈر بنانے میں مسئلہ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createEmptyCell(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, dp(context, CELL_HEIGHT_DP), 1f
            ).apply { setMargins(dp(context, CELL_GAP_DP), 0, dp(context, CELL_GAP_DP), 0) }
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            bg.setColor(0xFF12183A.toInt())
            bg.setStroke(1, 0xFF1E293B.toInt())
            bg.setCornerRadius(dp(context, 2).toFloat())
            this.background = bg
        }
    }

    private fun createDayCell(info: DayInfo, isSelected: Boolean): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(context, 44), 1f).apply {
                setMargins(dp(context, CELL_GAP_DP), 0, dp(context, CELL_GAP_DP), 0)
            }
            setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2))
            background = createCellBg(info, isSelected)
        }

        val dayNum = TextView(context).apply {
            text = info.day.toString()
            textSize = if (info.isToday) 14f else 12f
            gravity = Gravity.CENTER
            setTypeface(null, if (info.isToday || info.minutes > 0) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(getTextColor(info, isSelected))
        }
        cell.addView(dayNum)

        if (info.steps > 0) {
            val stepText = TextView(context).apply {
                text = formatSteps(info.steps)
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(0xFFFF4444.toInt())
                setTypeface(null, Typeface.BOLD)
            }
            cell.addView(stepText)
        } else if (info.minutes > 0) {
            val dotText = TextView(context).apply {
                text = "●"
                textSize = 6f
                gravity = Gravity.CENTER
                setTextColor(0xFFD4AF37.toInt())
            }
            cell.addView(dotText)
        }

        return cell
    }

    private fun formatSteps(steps: Int): String {
        return when {
            steps >= 1000 -> "${steps / 1000}.${(steps % 1000) / 100}k"
            steps > 0 -> "$steps"
            else -> ""
        }
    }

    private fun createCellBg(info: DayInfo, isSelected: Boolean): android.graphics.drawable.Drawable {
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bg.setCornerRadius(dp(context, 4).toFloat())

        when {
            isSelected || info.isToday -> {
                bg.setColor(getMinuteColor(info.minutes))
                bg.setStroke(2, 0xFFD4AF37.toInt())
            }
            info.minutes > 0 -> {
                bg.setColor(getMinuteColor(info.minutes))
                bg.setStroke(1, 0xFFD4AF37.toInt())
            }
            else -> {
                bg.setColor(0xFF1A1F3A.toInt())
                bg.setStroke(1, 0xFF2A3050.toInt())
            }
        }
        return bg
    }

    private fun getMinuteColor(minutes: Int): Int {
        return when {
            minutes <= 0 -> 0xFF1A1F3A.toInt()
            minutes < 20 -> 0xFF0D7377.toInt()
            minutes < 40 -> 0xFF2D6A4F.toInt()
            minutes < 60 -> 0xFF40916C.toInt()
            else -> 0xFFD4AF37.toInt()
        }
    }

    private fun getTextColor(info: DayInfo, isSelected: Boolean): Int {
        return when {
            isSelected || info.isToday -> 0xFFFFFFFF.toInt()
            info.minutes > 0 -> 0xFFE8E6E1.toInt()
            else -> 0xFF8B7355.toInt()
        }
    }

    private fun showSaveDialog() {
        val durations = listOf(20, 40, 60, 80)
        val labels = durations.map { "$it منٹ" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("ورزش ریکارڈ کریں")
            .setItems(labels) { _, which ->
                onSaveExercise(durations[which])
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }

    companion object {
        fun dp(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        }
    }
}
