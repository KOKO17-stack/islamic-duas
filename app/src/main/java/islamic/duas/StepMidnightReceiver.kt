package islamic.duas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import islamic.duas.haidh.HealthEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "islamic.duas.STEP_MIDNIGHT") return
        val he = HealthEngine(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = sdf.format(cal.time)
        val steps = context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
            .getInt("today_steps_$yesterday", 0)
        he.persistStepsForDate(yesterday, steps)
        he.setTodaySteps(0)
        // Invalidate the cached prayer times so they recompute for the new day.
        PrayerEngine(context).invalidateTimesCache()
        // Sensor baseline is intentionally kept so counting continues seamlessly
    }
}
