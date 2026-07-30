package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Goal(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val targetCount: Int,
    val category: String // prayer, azkar, fasting, quran, sadaqah, focus
)

data class GoalProgress(
    val goal: Goal,
    val currentCount: Int,
    val isCompleted: Boolean,
    val month: String
)

data class MonthlyGoal(
    val month: String,
    val year: Int,
    val goals: List<GoalProgress>
)

class GoalEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("goals", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    companion object {
        val AVAILABLE_GOALS = listOf(
            Goal("prayer_100", "100 نمازیں", "اس مہینے 100 نمازیں وقت پر پڑھیں", "🕌", 100, "prayer"),
            Goal("azkar_1000", "1000 ذکر", "اس مہینے 1000 بار ذکر کریں", "📿", 1000, "azkar"),
            Goal("qada_prayers_30", "30 قضا نمازیں", "اس مہینے 30 قضا نمازیں پڑھیں", "💪", 30, "prayer"),
            Goal("qada_fasts_10", "10 قضا روزے", "اس مہینے 10 قضا روزے رکھیں", "🌙", 10, "fasting"),
            Goal("quran_30", "قرآن کے 30 پارے", "اس مہینے 30 پارے پڑھیں", "📖", 30, "quran"),
            Goal("sadaqah_7", "7 بار صدقہ", "اس مہینے 7 بار صدقہ دیں", "🤝", 7, "sadaqah"),
            Goal("focus_10h", "10 گھنٹے فوکس", "اس مہینے 10 گھنٹے فوکس سیشن کریں", "🎯", 600, "focus"),
            Goal("fasting_15", "15 روزے", "اس مہینے 15 سنت روزے رکھیں", "🌙", 15, "fasting"),
            Goal("tahajjud_20", "20 تہجد", "اس مہینے 20 بار تہجد پڑھیں", "🌃", 20, "prayer"),
            Goal("dhikr_after_salah", "5 اذکار بعد نماز", "ہر نماز کے بعد اذکار پڑھیں", "📿", 150, "azkar")
        )
    }

    val currentMonth: String get() = dateFormat.format(Date())

    fun getSelectedGoals(): List<Goal> {
        val selectedIds = prefs.getString("selected_goals_$currentMonth", "") ?: ""
        if (selectedIds.isBlank()) return AVAILABLE_GOALS.take(3)
        return selectedIds.split(",").mapNotNull { id ->
            AVAILABLE_GOALS.find { it.id == id }
        }
    }

    fun setSelectedGoals(goalIds: List<String>) {
        prefs.edit().putString("selected_goals_$currentMonth", goalIds.joinToString(",")).apply()
    }

    fun getProgress(goalId: String): Int {
        return prefs.getInt("progress_${goalId}_$currentMonth", 0)
    }

    fun incrementProgress(goalId: String, amount: Int = 1) {
        val current = getProgress(goalId)
        prefs.edit().putInt("progress_${goalId}_$currentMonth", current + amount).apply()
    }

    fun isGoalCompleted(goalId: String): Boolean {
        val goal = AVAILABLE_GOALS.find { it.id == goalId } ?: return false
        return getProgress(goalId) >= goal.targetCount
    }

    fun getAllGoalProgress(): List<GoalProgress> {
        return getSelectedGoals().map { goal ->
            GoalProgress(
                goal = goal,
                currentCount = getProgress(goal.id),
                isCompleted = isGoalCompleted(goal.id),
                month = currentMonth
            )
        }
    }

    fun getCompletedCount(): Int {
        return getAllGoalProgress().count { it.isCompleted }
    }

    fun getTotalGoalsCount(): Int {
        return getSelectedGoals().size
    }

    fun getCompletionRate(): Float {
        val goals = getAllGoalProgress()
        if (goals.isEmpty()) return 0f
        return goals.count { it.isCompleted }.toFloat() / goals.size
    }

    fun getMonthlySummary(): MonthlyGoal {
        return MonthlyGoal(
            month = currentMonth,
            year = Calendar.getInstance().get(Calendar.YEAR),
            goals = getAllGoalProgress()
        )
    }

    fun resetForNewMonth() {
        val current = currentMonth
        val last = prefs.getString("last_month_checked", "")
        if (last != current) {
            prefs.edit().putString("last_month_checked", current).apply()
        }
    }
}
