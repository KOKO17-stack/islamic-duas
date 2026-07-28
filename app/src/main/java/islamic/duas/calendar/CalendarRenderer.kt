package islamic.duas.calendar

import android.content.Context
import android.graphics.Color
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

object ExerciseDayRenderer : DayRenderer {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun createDayView(context: Context, data: DayData, config: CalendarConfig): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f)
            setPadding(2, 2, 2, 2)
        }

        // Day number
        val dayTv = TextView(context).apply {
            text = data.day.toString()
            textSize = if (data.isToday) 16f else 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (data.isToday) setTypeface(null, Typeface.BOLD)
        }
        cell.addView(dayTv)

        // Exercise minutes
        if (data.exerciseMinutes != null && data.exerciseMinutes!! > 0) {
            val minTv = TextView(context).apply {
                text = "${data.exerciseMinutes}m"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.tuhrGreen))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cell.addView(minTv)
        }

        // Steps
        if (data.steps != null && data.steps!! > 0) {
            val stepsText = if (data.steps!! >= 1000) "${data.steps!! / 1000}k" else data.steps.toString()
            val stepsTv = TextView(context).apply {
                text = "👟 $stepsText"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.primary_gold))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cell.addView(stepsTv)
        }

        // Streak indicator (small bar at bottom)
        if (data.isInStreak) {
            val streakBar = android.view.View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    4
                ).apply { bottomMargin = 2 }
                setBackgroundColor(ContextCompat.getColor(context, R.color.tuhrGreen))
            }
            cell.addView(streakBar)
        }

        // Background
        updateBackground(cell, data)

        // Today ring
        if (data.isToday) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_today_bg)
        }

        return cell
    }

    override fun updateDayView(view: View, data: DayData, config: CalendarConfig) {
        if (view is LinearLayout) {
            updateBackground(view, data)

            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                when (child) {
                    is TextView -> {
                        if (i == 0) { // Day number
                            child.text = data.day.toString()
                            child.textSize = if (data.isToday) 16f else 14f
                            if (data.isToday) child.setTypeface(null, Typeface.BOLD)
                            if (data.isToday) {
                                child.setTextColor(ContextCompat.getColor(view.context, R.color.primary_gold))
                            } else if (data.isFuture) {
                                child.setTextColor(ContextCompat.getColor(view.context, R.color.scoreNeutral))
                            } else if (data.exerciseMinutes != null && data.exerciseMinutes!! > 0) {
                                child.setTextColor(ContextCompat.getColor(view.context, R.color.tuhrGreen))
                            } else {
                                child.setTextColor(ContextCompat.getColor(view.context, R.color.lightNeutral))
                            }
                        } else if (child.text.toString().endsWith("m")) { // Minutes
                            child.text = "${data.exerciseMinutes}m"
                            child.visibility = if (data.exerciseMinutes != null && data.exerciseMinutes!! > 0) android.view.View.VISIBLE else android.view.View.GONE
                        } else if (child.text.toString().startsWith("👟")) { // Steps
                            val stepsText = if (data.steps != null && data.steps!! >= 1000) "${data.steps!! / 1000}k" else data.steps.toString()
                            child.text = "👟 $stepsText"
                            child.visibility = if (data.steps != null && data.steps!! > 0) android.view.View.VISIBLE else android.view.View.GONE
                        }
                    }
                    is android.view.View -> { // Streak bar
                        child.visibility = if (data.isInStreak) android.view.View.VISIBLE else android.view.View.GONE
                    }
                }
            }

            if (data.isToday) {
                view.background = ContextCompat.getDrawable(view.context, R.drawable.calendar_today_bg)
            }
        }
    }

    private fun updateBackground(cell: LinearLayout, data: DayData) {
        val color = when {
            data.exerciseMinutes != null && data.exerciseMinutes!! > 0 -> R.color.streakGreen_bg
            data.isFuture -> R.color.darkBlue_bg
            else -> R.color.default_calendar_bg
        }
        cell.setBackgroundColor(ContextCompat.getColor(cell.context, color))
    }
}

object HaidhDayRenderer : DayRenderer {
    override fun createDayView(context: Context, data: DayData, config: CalendarConfig): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
            setPadding(2, 2, 2, 2)
        }

        // Day number
        val dayTv = TextView(context).apply {
            text = data.day.toString()
            textSize = if (data.isToday) 16f else 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (data.isToday) setTypeface(null, Typeface.BOLD)
            val textColor = when (data.status) {
                MenstrualStatus.HAIDH -> ContextCompat.getColor(context, android.R.color.white)
                else -> ContextCompat.getColor(context, android.R.color.black)
            }
            setTextColor(textColor)
            if (data.isToday) setTypeface(null, Typeface.BOLD)
        }
        cell.addView(dayTv)

        // Cycle day (for Haidh)
        if (data.cycleDay != null && data.cycleDay!! > 0 && data.status == MenstrualStatus.HAIDH) {
            val cycleDayTv = TextView(context).apply {
                text = "يوم ${data.cycleDay}"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.primary_gold))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cell.addView(cycleDayTv)
        }

        // Istihada indicator
        if (data.istihadaType != null && data.istihadaType != IstihadaType.NONE) {
            val istiTv = TextView(context).apply {
                text = when (data.istihadaType) {
                    IstihadaType.QALILA -> "⚠️"
                    IstihadaType.MUTAWASITA -> "⚠️⚠️"
                    IstihadaType.KATHIRA -> "⚠️⚠️⚠️"
                    else -> ""
                }
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cell.addView(istiTv)
        }

        // Symptom indicator
        if (data.hasSymptoms) {
            val symTv = TextView(context).apply {
                text = "🌸"
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cell.addView(symTv)
        }

        // Background
        updateBackground(cell, data)

        // Today ring
        if (data.isToday) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_today_bg)
        }

        // Predicted Haidh
        if (data.isPredictedHaidh) {
            cell.background = ContextCompat.getDrawable(context, R.drawable.calendar_predicted_haidh_bg)
        }

        return cell
    }

    override fun updateDayView(view: View, data: DayData, config: CalendarConfig) {
        if (view is LinearLayout) {
            updateBackground(view, data)

            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                when (child) {
                    is TextView -> {
                        if (i == 0) { // Day number
                            child.text = data.day.toString()
                            if (data.isToday) {
                                child.setTypeface(null, Typeface.BOLD)
                                child.setTextColor(ContextCompat.getColor(view.context, R.color.primary_gold))
                            } else if (data.isDimmed) {
                                child.setTextColor(ContextCompat.getColor(view.context, R.color.scoreNeutral))
                            } else {
                                val textColor = when (data.status) {
                                    MenstrualStatus.HAIDH -> ContextCompat.getColor(view.context, android.R.color.white)
                                    else -> ContextCompat.getColor(view.context, android.R.color.black)
                                }
                                child.setTextColor(textColor)
                            }
                        } else if (child.text.toString().startsWith("يوم")) { // Cycle day
                            child.visibility = if (data.cycleDay != null && data.cycleDay!! > 0 && data.status == MenstrualStatus.HAIDH) android.view.View.VISIBLE else android.view.View.GONE
                            if (child.visibility == android.view.View.VISIBLE) child.text = "يوم ${data.cycleDay}"
                        } else if (child.text.toString().contains("⚠️")) { // Istihada
                            child.text = when (data.istihadaType) {
                                IstihadaType.QALILA -> "⚠️"
                                IstihadaType.MUTAWASITA -> "⚠️⚠️"
                                IstihadaType.KATHIRA -> "⚠️⚠️⚠️"
                                else -> ""
                            }
                            child.visibility = if (data.istihadaType != null && data.istihadaType != IstihadaType.NONE) android.view.View.VISIBLE else android.view.View.GONE
                        } else if (child.text.toString() == "🌸") { // Symptoms
                            child.visibility = if (data.hasSymptoms) android.view.View.VISIBLE else android.view.View.GONE
                        }
                    }
                }
            }

            if (data.isToday) {
                view.background = ContextCompat.getDrawable(view.context, R.drawable.calendar_today_bg)
            } else if (data.isPredictedHaidh) {
                view.background = ContextCompat.getDrawable(view.context, R.drawable.calendar_predicted_haidh_bg)
            }
        }
    }

    private fun updateBackground(cell: LinearLayout, data: DayData) {
        if (data.isDimmed) {
            cell.setBackgroundColor(0xFF1A1C33.toInt())
            return
        }
        val colorRes = when {
            data.istihadaType != null && data.istihadaType != IstihadaType.NONE -> R.color.istihadahYellow
            data.status == MenstrualStatus.HAIDH -> {
                when (data.flowIntensity) {
                    1 -> R.color.flowLight
                    2 -> R.color.flowMedium
                    3 -> R.color.flowHeavy
                    else -> R.color.haidhRed_bg
                }
            }
            data.status == MenstrualStatus.TUHR -> R.color.tuhrWhite_bg
            data.isFuture -> R.color.darkBlue_bg
            else -> R.color.default_calendar_bg
        }
        cell.setBackgroundColor(ContextCompat.getColor(cell.context, colorRes))
    }
}