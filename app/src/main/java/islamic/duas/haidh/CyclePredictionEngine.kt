package islamic.duas.haidh

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CyclePredictionEngine {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Predicts the next Haidh start date based on last Haidh end, average cycle length, and average Haidh duration.
     * Returns null if insufficient data.
     */
    fun predictNextHaidhStart(
        lastHaidhEnd: String,
        avgCycleLength: Int,
        avgHaidhLength: Int
    ): String? {
        if (avgCycleLength <= 0) return null

        val cal = Calendar.getInstance()
        try {
            cal.time = dateFormat.parse(lastHaidhEnd)
        } catch (e: Exception) {
            return null
        }

        // Add average cycle length to get next predicted start
        cal.add(Calendar.DAY_OF_YEAR, avgCycleLength)

        val predictedStart = dateFormat.format(cal.time)
        
        // Also calculate predicted end
        val endCal = Calendar.getInstance()
        endCal.time = dateFormat.parse(predictedStart)!!
        endCal.add(Calendar.DAY_OF_YEAR, avgHaidhLength - 1)
        val predictedEnd = dateFormat.format(endCal.time)

        return predictedStart
    }
}