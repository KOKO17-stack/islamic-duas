package islamic.duas.calendar

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import islamic.duas.HijriCalendar
import islamic.duas.R
import java.util.Calendar
import java.util.Locale

class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var adapter: CalendarAdapter? = null
    private var config: CalendarConfig = CalendarConfig()
    private var dayRenderer: DayRenderer = ExerciseDayRenderer

    private lateinit var headerContainer: LinearLayout
    private lateinit var monthTitle: TextView
    private lateinit var prevMonthBtn: TextView
    private lateinit var nextMonthBtn: TextView
    private lateinit var dayHeaders: LinearLayout
    private lateinit var gridContainer: LinearLayout

    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.calendar_view, this, true)

        headerContainer = findViewById(R.id.calendarHeader)
        monthTitle = findViewById(R.id.calendarMonthTitle)
        prevMonthBtn = findViewById(R.id.calendarPrevMonth)
        nextMonthBtn = findViewById(R.id.calendarNextMonth)
        dayHeaders = findViewById(R.id.calendarDayHeaders)
        gridContainer = findViewById(R.id.calendarGrid)

        prevMonthBtn.setOnClickListener { navigateMonth(-1) }
        nextMonthBtn.setOnClickListener { navigateMonth(1) }

        setupDayHeaders()
    }

    fun setAdapter(adapter: CalendarAdapter, rendererType: String = "exercise", config: CalendarConfig = CalendarConfig()) {
        this.adapter = adapter
        this.config = config
        this.dayRenderer = DayRenderer.getRenderer(rendererType)
        renderCalendar()
    }

    private fun setupDayHeaders() {
        dayHeaders.removeAllViews()
        for (dayName in config.urduDayNames) {
            val tv = TextView(context).apply {
                text = dayName
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.scoreNeutral))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, 40, 1f)
            }
            dayHeaders.addView(tv)
        }
    }

    private fun renderCalendar() {
        adapter?.let {
            (context as? CalendarViewHost)?.onMonthChanged(currentYear, currentMonth)
        }
    }

    /**
     * Called from the Activity after data has been fetched on background thread.
     * This method runs on the main thread to update the UI.
     */
    fun updateCalendarData(monthMeta: MonthMeta, dayDataMap: Map<Int, DayData>) {
        // Update header
        val title = "${config.urduMonthNames[monthMeta.currentMonth - 1]} ${monthMeta.currentYear}"
        val hijriPart = if (config.showHijriHeader && monthMeta.hijriMonthName != null) {
            " / ${monthMeta.hijriMonthName} ${monthMeta.hijriYear}ھ"
        } else ""
        monthTitle.text = title + hijriPart

        // Build grid
        gridContainer.removeAllViews()

        val firstDayOfWeek = MonthNavigator.getFirstDayOfWeek(monthMeta.currentYear, monthMeta.currentMonth)
        val daysInMonth = MonthNavigator.getDaysInMonth(monthMeta.currentYear, monthMeta.currentMonth)

        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        var dayCounter = 1
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                if (cellIndex < firstDayOfWeek || dayCounter > daysInMonth) {
                    // Empty cell
                    val emptyView = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
                    }
                    rowLayout.addView(emptyView)
                } else {
                    val dayData = dayDataMap[dayCounter] ?: DayData(
                        year = monthMeta.currentYear,
                        month = monthMeta.currentMonth,
                        day = dayCounter,
                        isToday = MonthNavigator.isToday(monthMeta.currentYear, monthMeta.currentMonth, dayCounter),
                        isFuture = MonthNavigator.isFuture(monthMeta.currentYear, monthMeta.currentMonth, dayCounter),
                        hijriDay = if (config.showHijriPerDay) getHijriDay(dayCounter) else null
                    )

                    val cellView = dayRenderer.createDayView(context, dayData, config)
                    if (!dayData.isFuture) {
                        cellView.setOnClickListener {
                            adapter?.onDayClick(monthMeta.currentYear, monthMeta.currentMonth, dayCounter)
                        }
                        cellView.setOnLongClickListener {
                            adapter?.onDayLongClick(monthMeta.currentYear, monthMeta.currentMonth, dayCounter)
                            true
                        }
                    }

                    rowLayout.addView(cellView)
                    dayCounter++
                }
            }
            gridContainer.addView(rowLayout)
        }
    }

    private fun navigateMonth(direction: Int) {
        val (newYear, newMonth) = if (direction > 0) {
            MonthNavigator.nextMonth(currentYear, currentMonth)
        } else {
            MonthNavigator.previousMonth(currentYear, currentMonth)
        }

        currentYear = newYear
        currentMonth = newMonth

        // Trigger async data fetch and re-render via adapter
        adapter?.let { adapter ->
            // The Activity will handle this via CalendarView.CalendarViewHost
            (context as? CalendarViewHost)?.onMonthChanged(newYear, newMonth)
        }
    }

    private fun getHijriDay(gregorianDay: Int): Int? {
        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth - 1, gregorianDay)
        val (_, _, hDay) = HijriCalendar.toHijri(cal)
        return hDay
    }

    interface CalendarViewHost {
        fun onMonthChanged(year: Int, month: Int)
    }
}