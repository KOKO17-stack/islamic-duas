package islamic.duas.calendar

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import islamic.duas.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val CELL_HEIGHT_DP = 36

class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var adapter: CalendarAdapter? = null
    private var config: CalendarConfig = CalendarConfig()
    private var dayRenderer: DayRenderer = ExerciseDayRenderer
    private var scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var monthTitle: TextView
    private lateinit var prevMonthBtn: TextView
    private lateinit var nextMonthBtn: TextView
    private lateinit var dayHeaders: LinearLayout
    private lateinit var gridContainer: LinearLayout

    private var currentYear = 0
    private var currentMonth = 0
    private var selectedDay: Int? = null
    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null

    private var minYear = 0
    private var minMonth = 0
    private var maxYear = 0
    private var maxMonth = 0

    private var onDayClicked: ((year: Int, month: Int, day: Int) -> Unit)? = null
    private var onDayLongClicked: ((year: Int, month: Int, day: Int) -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.calendar_view, this, true)

        monthTitle = findViewById(R.id.calendarMonthTitle)
        prevMonthBtn = findViewById(R.id.calendarPrevMonth)
        nextMonthBtn = findViewById(R.id.calendarNextMonth)
        dayHeaders = findViewById(R.id.calendarDayHeaders)
        gridContainer = findViewById(R.id.calendarGrid)

        prevMonthBtn.setOnClickListener { navigateMonth(-1) }
        nextMonthBtn.setOnClickListener { navigateMonth(1) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    fun setMonthRange(minY: Int, minM: Int, maxY: Int, maxM: Int) {
        minYear = minY; minMonth = minM
        maxYear = maxY; maxMonth = maxM
    }

    fun setOnDayClicked(callback: (year: Int, month: Int, day: Int) -> Unit) {
        onDayClicked = callback
    }

    fun setOnDayLongClicked(callback: (year: Int, month: Int, day: Int) -> Unit) {
        onDayLongClicked = callback
    }

    fun setAdapter(
        adapter: CalendarAdapter,
        rendererType: String = "exercise",
        config: CalendarConfig = CalendarConfig()
    ) {
        this.adapter = adapter
        this.config = config
        this.dayRenderer = DayRenderer.getRenderer(rendererType)

        val now = Calendar.getInstance()
        currentYear = now.get(Calendar.YEAR)
        currentMonth = now.get(Calendar.MONTH) + 1
        selectedDay = null; selectedYear = null; selectedMonth = null

        setupDayHeaders()
        updateNavButtons()
        loadMonthData(currentYear, currentMonth)
    }

    fun setSelectedDay(year: Int, month: Int, day: Int) {
        selectedDay = day; selectedYear = year; selectedMonth = month
        loadMonthData(currentYear, currentMonth)
    }

    fun clearSelection() {
        selectedDay = null; selectedYear = null; selectedMonth = null
        loadMonthData(currentYear, currentMonth)
    }

    fun refresh() {
        if (currentYear != 0) loadMonthData(currentYear, currentMonth)
    }

    private fun setupDayHeaders() {
        dayHeaders.removeAllViews()
        for (dayName in config.urduDayNames) {
            dayHeaders.addView(TextView(context).apply {
                text = dayName
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.scoreNeutral))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, 26, 1f)
            })
        }
    }

    private fun loadMonthData(year: Int, month: Int) {
        val ad = adapter ?: return
        scope.launch {
            val (meta, dayDataMap) = withContext(Dispatchers.IO) {
                val m = ad.getMonthMeta(year, month)
                val daysInMonth = MonthNavigator.getDaysInMonth(year, month)
                val map = mutableMapOf<Int, DayData>()
                for (day in 1..daysInMonth) {
                    ad.getDayData(year, month, day)?.let { map[day] = it }
                }
                Pair(m, map)
            }
            buildGrid(meta, dayDataMap)
        }
    }

    private fun updateNavButtons() {
        val atMin = currentYear == minYear && currentMonth == minMonth
        val atMax = currentYear == maxYear && currentMonth == maxMonth
        prevMonthBtn.alpha = if (atMin) 0.3f else 1f
        prevMonthBtn.isEnabled = !atMin
        nextMonthBtn.alpha = if (atMax) 0.3f else 1f
        nextMonthBtn.isEnabled = !atMax
    }

    private fun buildGrid(monthMeta: MonthMeta, dayDataMap: Map<Int, DayData>) {
        gridContainer.removeAllViews()

        val monthName = config.urduMonthNames.getOrElse(monthMeta.currentMonth - 1) { "${monthMeta.currentMonth}" }
        monthTitle.text = "$monthName ${monthMeta.currentYear}"

        val cal = Calendar.getInstance()
        cal.set(monthMeta.currentYear, monthMeta.currentMonth - 1, 1)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        var firstCol = dayOfWeek - 1
        if (firstCol < 0) firstCol = 0
        if (firstCol > 6) firstCol = 6

        val daysInMonth = MonthNavigator.getDaysInMonth(monthMeta.currentYear, monthMeta.currentMonth)
        val totalCells = firstCol + daysInMonth
        val rows = (totalCells + 6) / 7

        var dayCounter = 1
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }

            for (col in 0 until 7) {
                if (col < firstCol || dayCounter > daysInMonth) {
                    val emptyCell = View(context)
                    emptyCell.layoutParams = LinearLayout.LayoutParams(0, dp(context, CELL_HEIGHT_DP), 1f)
                    val bg = android.graphics.drawable.GradientDrawable()
                    bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    bg.setColor(0xFF12183A.toInt())
                    bg.setStroke(1, 0xFF1E293B.toInt())
                    bg.setCornerRadius(dp(context, 2).toFloat())
                    emptyCell.background = bg
                    rowLayout.addView(emptyCell)
                } else {
                    val dayData = dayDataMap[dayCounter] ?: DayData(
                        year = monthMeta.currentYear,
                        month = monthMeta.currentMonth,
                        day = dayCounter,
                        isToday = MonthNavigator.isToday(monthMeta.currentYear, monthMeta.currentMonth, dayCounter),
                        isFuture = MonthNavigator.isFuture(monthMeta.currentYear, monthMeta.currentMonth, dayCounter)
                    )

                    val isThisSelected = selectedDay == dayCounter &&
                            selectedYear == monthMeta.currentYear &&
                            selectedMonth == monthMeta.currentMonth

                    val cellView = dayRenderer.createDayView(context, dayData.copy(isSelected = isThisSelected), config)
                    val y = monthMeta.currentYear
                    val m = monthMeta.currentMonth
                    val d = dayCounter

                    if (!dayData.isFuture) {
                        cellView.setOnClickListener {
                            selectedDay = d; selectedYear = y; selectedMonth = m
                            onDayClicked?.invoke(y, m, d)
                            loadMonthData(currentYear, currentMonth)
                        }
                        cellView.setOnLongClickListener {
                            onDayLongClicked?.invoke(y, m, d)
                            true
                        }
                    }
                    rowLayout.addView(cellView)
                    dayCounter++
                }
            }
            gridContainer.addView(rowLayout)
        }
        requestLayout()
    }

    private fun navigateMonth(direction: Int) {
        val (newYear, newMonth) = if (direction > 0) {
            MonthNavigator.nextMonth(currentYear, currentMonth)
        } else {
            MonthNavigator.previousMonth(currentYear, currentMonth)
        }
        if (direction > 0 && (newYear > maxYear || (newYear == maxYear && newMonth > maxMonth))) return
        if (direction < 0 && (newYear < minYear || (newYear == minYear && newMonth < minMonth))) return

        currentYear = newYear
        currentMonth = newMonth
        selectedDay = null; selectedYear = null; selectedMonth = null
        updateNavButtons()
        loadMonthData(newYear, newMonth)
    }

    private fun dp(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    interface CalendarViewHost {
        fun onMonthChanged(year: Int, month: Int)
    }
}
