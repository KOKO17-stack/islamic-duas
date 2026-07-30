package islamic.duas.calendar

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import islamic.duas.R
import islamic.duas.haidh.IstihadaType
import islamic.duas.haidh.MenstrualStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface DayRenderer {
    fun createDayView(context: Context, data: DayData, config: CalendarConfig): View
    fun updateDayView(view: View, data: DayData, config: CalendarConfig)

    companion object {
        fun getRenderer(type: String): DayRenderer = when (type) {
            "exercise" -> ExerciseDayRenderer
            "haidh" -> HaidhDayRenderer
            else -> ExerciseDayRenderer
        }
    }
}

private const val CELL_HEIGHT_DP = 36
private const val DAY_TEXT_SIZE = 11f
private const val DAY_TEXT_SIZE_TODAY = 13f
private const val CELL_GAP_DP = 1

internal fun dp(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
}

object ExerciseDayRenderer : DayRenderer {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun createDayView(context: Context, data: DayData, config: CalendarConfig): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(context, CELL_HEIGHT_DP), 1f).apply {
                setMargins(dp(context, CELL_GAP_DP), dp(context, CELL_GAP_DP), dp(context, CELL_GAP_DP), dp(context, CELL_GAP_DP))
            }
            setPadding(dp(context, 1), dp(context, 1), dp(context, 1), dp(context, 1))
            background = ContextCompat.getDrawable(context, R.drawable.calendar_cell_bg)
        }

        val dayNum = TextView(context).apply {
            text = data.day.toString()
            textSize = DAY_TEXT_SIZE
            gravity = Gravity.CENTER
            setTextColor(getDayTextColor(context, data))
            if (data.isToday) { setTypeface(null, Typeface.BOLD); textSize = DAY_TEXT_SIZE_TODAY }
        }
        cell.addView(dayNum)

        if (data.isToday) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_today_bg)
        }
        return cell
    }

    override fun updateDayView(view: View, data: DayData, config: CalendarConfig) = Unit

    private fun getDayTextColor(context: Context, data: DayData): Int {
        return when {
            data.isToday || data.isSelected -> ContextCompat.getColor(context, R.color.primary_gold)
            data.isDimmed -> ContextCompat.getColor(context, R.color.scoreNeutral)
            data.exerciseMinutes != null && data.exerciseMinutes!! > 0 -> ContextCompat.getColor(context, R.color.tuhrGreen)
            else -> ContextCompat.getColor(context, R.color.lightNeutral)
        }
    }
}

object HaidhDayRenderer : DayRenderer {
    override fun createDayView(context: Context, data: DayData, config: CalendarConfig): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(context, CELL_HEIGHT_DP), 1f).apply {
                setMargins(dp(context, CELL_GAP_DP), dp(context, CELL_GAP_DP), dp(context, CELL_GAP_DP), dp(context, CELL_GAP_DP))
            }
            setPadding(dp(context, 1), dp(context, 1), dp(context, 1), dp(context, 1))
            background = getCellBg(context, data)
        }

        if (data.isPredictedHaidh) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_predicted_haidh_bg)
        }
        if (data.isToday) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_today_bg)
        }
        if (data.isSelected) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_selected_bg)
        }

        val dayNum = TextView(context).apply {
            text = data.day.toString()
            textSize = DAY_TEXT_SIZE
            gravity = Gravity.CENTER
            setTextColor(getDayTextColor(context, data))
            if (data.isToday) { setTypeface(null, Typeface.BOLD); textSize = DAY_TEXT_SIZE_TODAY }
        }
        cell.addView(dayNum)

        return cell
    }

    override fun updateDayView(view: View, data: DayData, config: CalendarConfig) = Unit

    private fun getCellBg(context: Context, data: DayData): android.graphics.drawable.Drawable {
        val res = when {
            data.isPredictedHaidh -> R.drawable.calendar_predicted_haidh_bg
            data.istihadaType != null && data.istihadaType != IstihadaType.NONE -> R.drawable.calendar_cell_bg
            data.status == MenstrualStatus.HAIDH -> R.drawable.calendar_haidh_cell_bg
            data.status == MenstrualStatus.TUHR -> R.drawable.calendar_tuhr_cell_bg
            else -> R.drawable.calendar_cell_bg
        }
        return ContextCompat.getDrawable(context, res)
            ?: ContextCompat.getDrawable(context, R.drawable.calendar_cell_bg)!!
    }

    private fun getDayTextColor(context: Context, data: DayData): Int {
        return when {
            data.isToday || data.isSelected -> ContextCompat.getColor(context, R.color.primary_gold)
            data.isDimmed -> ContextCompat.getColor(context, R.color.scoreNeutral)
            data.isPredictedHaidh -> ContextCompat.getColor(context, R.color.muted_gold)
            data.status == MenstrualStatus.HAIDH -> ContextCompat.getColor(context, android.R.color.white)
            data.status == MenstrualStatus.TUHR -> ContextCompat.getColor(context, R.color.lightNeutral)
            else -> ContextCompat.getColor(context, R.color.lightNeutral)
        }
    }
}
