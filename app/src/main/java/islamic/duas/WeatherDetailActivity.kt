package islamic.duas

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WeatherDetailActivity : ComponentActivity() {

    private lateinit var weatherEngine: WeatherEngine
    private var weekExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_App_EmeraldDusk_Dark)
        setContentView(R.layout.activity_weather_detail)

        weatherEngine = WeatherEngine(this)

        findViewById<TextView>(R.id.weatherDetailBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.weatherWeekToggle).setOnClickListener {
            weekExpanded = !weekExpanded
            val container = findViewById<LinearLayout>(R.id.weatherWeekContainer)
            container.visibility = if (weekExpanded) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.weatherWeekToggle).text =
                if (weekExpanded) "📅 7 دن کا موسم چھپائیں" else "📅 7 دن کا موسم دیکھیں"
        }

        loadForecast()
    }

    private fun loadForecast() {
        val summaryCard = findViewById<View>(R.id.weatherSummaryCard)
        val summaryRain = findViewById<TextView>(R.id.weatherSummaryRain)
        val summaryTemp = findViewById<TextView>(R.id.weatherSummaryTemp)
        val summaryHeat = findViewById<TextView>(R.id.weatherSummaryHeat)
        val container = findViewById<LinearLayout>(R.id.weatherHourlyContainer)

        if (!weatherEngine.hasBackgroundLocationPermission()) {
            summaryRain.text = "📍 بیٹی! موسم دیکھنے کے لیے لوکیشن کی اجازت دیں"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val forecast = weatherEngine.fetchRainForecast()
            if (forecast == null) {
                launch(Dispatchers.Main) {
                    summaryRain.text = "ڈیٹا دستیاب نہیں — بعد میں دوبارہ کوشش کریں"
                }
                return@launch
            }

            // Pre-compute all display data on IO thread
            val dateFormat = SimpleDateFormat("h:mm a", Locale.US)
            val nowTime = dateFormat.format(Date())
            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val isNight = currentHour >= 18 || currentHour < 6
            val rainPct = forecast.maxChance
            val rainIcon = WeatherEngine.conditionEmoji(rainPct, forecast.heatLevel, isNight)
            val condEmoji = WeatherEngine.conditionEmoji(rainPct, forecast.heatLevel, isNight)
            val tempStr = "$condEmoji آج: ${forecast.todayMinTemp}°C – ${forecast.todayMaxTemp}°C (محسوس: ${forecast.todayMaxFeelsLike}°C)"
            val heatColor = when (forecast.heatLevel) {
                HeatLevel.EXTREME -> "#EF4444"
                HeatLevel.HOT -> "#F59E0B"
                HeatLevel.MILDY_HOT -> "#C9A961"
                HeatLevel.MILD -> "#10B981"
            }
            val heatIcon = when (forecast.heatLevel) {
                HeatLevel.EXTREME -> "🔥"
                HeatLevel.HOT -> "🌡"
                HeatLevel.MILDY_HOT -> "🌤"
                HeatLevel.MILD -> "🌱"
            }

            data class RowData(
                val hour: String, val hour24: Int, val tempEmoji: String,
                val rainChance: Int, val tempInt: Int, val isRain: Boolean
            )
            val rows = forecast.hourlyData.map { hw ->
                val hwHour = try {
                    val h = hw.hour.split(" ")[0].toInt()
                    val amPm = hw.hour.split(" ")[1]
                    if (amPm == "PM" && h != 12) h + 12 else if (amPm == "AM" && h == 12) 0 else h
                } catch (_: Exception) { 0 }
                val isRowNight = hwHour >= 18 || hwHour < 6
                RowData(
                    hour = hw.hour,
                    hour24 = hwHour,
                    tempEmoji = WeatherEngine.conditionEmoji(hw.rainChance, forecast.heatLevel, isRowNight),
                    rainChance = hw.rainChance,
                    tempInt = hw.temp.toInt(),
                    isRain = hw.rainChance >= 30
                )
            }
            val isAnyRain = rows.any { it.isRain }

            // Build 7-day forecast data
            val weekData = forecast.dailyForecast.map { df ->
                val pct = df.precipitationProb.coerceIn(0, 100)
                val barColor = when {
                    pct >= 70 -> 0xFFEF4444.toInt()
                    pct >= 50 -> 0xFFF59E0B.toInt()
                    pct >= 30 -> 0xFFC9A961.toInt()
                    else -> 0xFF10B981.toInt()
                }
                WeekDayData(
                    dayName = df.dayName,
                    emoji = df.emoji,
                    maxTemp = df.maxTemp.toInt(),
                    minTemp = df.minTemp.toInt(),
                    rainChance = pct,
                    barColor = barColor,
                    barWeight = pct.toFloat()
                )
            }

            // Apply all views on main thread
            launch(Dispatchers.Main) {
                summaryRain.text = "$rainIcon بارش کا زیادہ سے زیادہ امکان: $rainPct%"
                summaryTemp.text = tempStr
                summaryHeat.text = "$heatIcon گرمی کی سطح: ${forecast.heatLevel.label}"
                summaryHeat.setTextColor(android.graphics.Color.parseColor(heatColor))

                // Hourly rows
                container.removeAllViews()
                for (rd in rows) {
                    val row = LinearLayout(this@WeatherDetailActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(8, 4, 8, 4)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 40.dpToPx()
                        )
                        if (rd.isRain) setBackgroundColor(0x1533A1FF.toInt())
                    }

                    val hourTv = TextView(this@WeatherDetailActivity).apply {
                        text = rd.hour; textSize = 13f
                        setTextColor(0xFFE0DDD8.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                    }
                    row.addView(hourTv)

                    val tempTv = TextView(this@WeatherDetailActivity).apply {
                        text = "${rd.tempEmoji}${rd.tempInt}°"; textSize = 12f
                        setTextColor(0xFFF59E0B.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        gravity = android.view.Gravity.CENTER
                    }
                    row.addView(tempTv)

                    val barContainer = LinearLayout(this@WeatherDetailActivity).apply {
                        setBackgroundResource(R.drawable.rounded_bg)
                        layoutParams = LinearLayout.LayoutParams(0, 8, 2.5f).apply { setMargins(2, 0, 2, 0) }
                        orientation = LinearLayout.HORIZONTAL
                    }
                    row.addView(barContainer)

                    val fill = View(this@WeatherDetailActivity).apply {
                        val pct = rd.rainChance.coerceIn(0, 100)
                        val params = LinearLayout.LayoutParams(0, 8).apply { weight = pct.toFloat() }
                        setBackgroundResource(R.drawable.chip_selected)
                        layoutParams = params
                    }
                    barContainer.addView(fill)

                    val probTv = TextView(this@WeatherDetailActivity).apply {
                        text = "${rd.rainChance}%"; textSize = 12f
                        val color = when {
                            rd.rainChance >= 70 -> 0xFFEF4444.toInt()
                            rd.rainChance >= 50 -> 0xFFF59E0B.toInt()
                            rd.rainChance >= 30 -> 0xFFC9A961.toInt()
                            else -> 0xFF8B7355.toInt()
                        }
                        setTextColor(color)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        gravity = android.view.Gravity.END
                    }
                    row.addView(probTv)

                    container.addView(row)

                    val divider = View(this@WeatherDetailActivity).apply {
                        setBackgroundColor(0x208B7355.toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    }
                    container.addView(divider)
                }

                val legend = TextView(this@WeatherDetailActivity).apply {
                    text = "موسم کی کیفیت: ⛈ بارش  🌧 بوچھار  🌦 ہلکی بارش  ☁️ ابر آلود  ☀️ دھوپ  |  بارش کا امکان فیصد میں (%)\nنیلے رنگ کی قطار = بارش متوقع (≥30%)"
                    textSize = 11f; setTextColor(0xFF8B7355.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) }
                }
                container.addView(legend)

                // Build 7-day forecast
                buildWeekForecast(weekData)
            }
        }
    }

    private fun buildWeekForecast(weekData: List<WeekDayData>) {
        val weekContainer = findViewById<LinearLayout>(R.id.weatherWeekContainer)

        // Remove all existing children except the header
        weekContainer.removeViews(1, weekContainer.childCount - 1)

        for (wd in weekData) {
            val card = LinearLayout(this@WeatherDetailActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 50.dpToPx()
                )
                setBackgroundColor(0xFF1C1F33.toInt())
            }

            // Day name
            card.addView(TextView(this@WeatherDetailActivity).apply {
                text = wd.dayName
                textSize = 14f
                setTextColor(0xFFE0DDD8.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            })

            // Emoji
            card.addView(TextView(this@WeatherDetailActivity).apply {
                text = wd.emoji
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
                gravity = android.view.Gravity.CENTER
            })

            // Min - Max temp
            card.addView(TextView(this@WeatherDetailActivity).apply {
                text = "${wd.minTemp}° – ${wd.maxTemp}°"
                textSize = 12f
                setTextColor(0xFFC9A961.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                gravity = android.view.Gravity.CENTER
            })

            // Rain bar
            val barContainer = LinearLayout(this@WeatherDetailActivity).apply {
                setBackgroundResource(R.drawable.rounded_bg)
                layoutParams = LinearLayout.LayoutParams(0, 10, 2f).apply { setMargins(4, 0, 4, 0) }
                orientation = LinearLayout.HORIZONTAL
            }
            card.addView(barContainer)

            val fill = View(this@WeatherDetailActivity).apply {
                val params = LinearLayout.LayoutParams(0, 10).apply { weight = wd.barWeight }
                setBackgroundColor(wd.barColor)
                layoutParams = params
            }
            barContainer.addView(fill)

            // Rain %
            card.addView(TextView(this@WeatherDetailActivity).apply {
                text = "${wd.rainChance}%"
                textSize = 12f
                setTextColor(wd.barColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
                gravity = android.view.Gravity.END
            })

            weekContainer.addView(card)

            // Divider
            val divider = View(this@WeatherDetailActivity).apply {
                setBackgroundColor(0x208B7355.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            }
            weekContainer.addView(divider)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    // Data class for week day row data (used as private inner type)
    private data class WeekDayData(
        val dayName: String, val emoji: String,
        val maxTemp: Int, val minTemp: Int,
        val rainChance: Int, val barColor: Int, val barWeight: Float
    )
}
