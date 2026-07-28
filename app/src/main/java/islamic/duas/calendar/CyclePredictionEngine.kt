package islamic.duas.calendar

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
        lastHaidhEnd: String?,
        avgCycleLength: Int,
        avgHaidhLength: Int
    ): String? {
        if (lastHaidhEnd == null || avgCycleLength <= 0) return null

        val cal = Calendar.getInstance()
        try {
            cal.time = dateFormat.parse(lastHaidhEnd)!!
        } catch (e: Exception) {
            return null
        }

        // Next cycle starts avgCycleLength days after the START of the last Haidh
        // But we have the END date. The start was avgHaidhLength days before end.
        // So: lastHaidhStart = lastHaidhEnd - avgHaidhLength + 1
        // nextHaidhStart = lastHaidhStart + avgCycleLength
        // = (lastHaidhEnd - avgHaidhLength + 1) + avgCycleLength
        // = lastHaidhEnd + (avgCycleLength - avgHaidhLength + 1)

        val daysToAdd = avgCycleLength - avgHaidhLength + 1
        if (daysToAdd <= 0) return null

        cal.add(Calendar.DAY_OF_YEAR, daysToAdd)

        // Don't predict past dates
        val today = dateFormat.format(Calendar.getInstance().time)
        val predicted = dateFormat.format(cal.time)
        if (predicted <= today) return null

        return predicted
    }
}