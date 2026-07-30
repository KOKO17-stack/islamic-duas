package islamic.duas.haidh

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CycleDao(private val db: SQLiteDatabase) {

    suspend fun getDayStatus(date: String): CycleEntity? = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM cycles WHERE date = ? LIMIT 1", arrayOf(date)).use { c ->
            if (c.moveToFirst()) cycleFromCursor(c) else null
        }
    }

    suspend fun upsertDayStatus(entry: CycleEntity) = withContext(Dispatchers.IO) {
        val cv = cycleToCV(entry)
        val rows = db.update("cycles", cv, "date = ?", arrayOf(entry.date))
        if (rows == 0) {
            db.insert("cycles", null, cv)
        }
    }

    suspend fun getCycleRange(startDate: String, endDate: String): List<CycleEntity> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM cycles WHERE date BETWEEN ? AND ? ORDER BY date ASC", arrayOf(startDate, endDate)).use { c ->
            cyclesFromCursor(c)
        }
    }

    suspend fun upsertPhase(phase: CyclePhaseEntity) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("startDate", phase.startDate)
            put("endDate", phase.endDate)
            put("status", phase.status.name)
            put("cycleDay", phase.cycleDay)
        }
        db.insertWithOnConflict("cycle_phases", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getPhaseForDate(date: String): CyclePhaseEntity? = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM cycle_phases WHERE startDate <= ? AND endDate >= ? LIMIT 1", arrayOf(date, date)).use { c ->
            if (c.moveToFirst()) phaseFromCursor(c) else null
        }
    }

    private fun cycleFromCursor(c: Cursor) = CycleEntity(
        date = c.getString(c.getColumnIndexOrThrow("date")),
        status = MenstrualStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
        symptoms = c.getString(c.getColumnIndexOrThrow("symptoms")),
        flowIntensity = c.getInt(c.getColumnIndexOrThrow("flowIntensity")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        isHabitDay = c.getInt(c.getColumnIndexOrThrow("isHabitDay")) == 1,
        istihadaType = try { IstihadaType.valueOf(c.getString(c.getColumnIndexOrThrow("istihadaType"))) } catch (_: Exception) { IstihadaType.NONE },
        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
    )

    private fun cyclesFromCursor(c: Cursor): List<CycleEntity> {
        val result = mutableListOf<CycleEntity>()
        while (c.moveToNext()) {
            result.add(cycleFromCursor(c))
        }
        return result
    }

    private fun phaseFromCursor(c: Cursor) = CyclePhaseEntity(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        startDate = c.getString(c.getColumnIndexOrThrow("startDate")),
        endDate = c.getString(c.getColumnIndexOrThrow("endDate")),
        status = MenstrualStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
        cycleDay = c.getInt(c.getColumnIndexOrThrow("cycleDay"))
    )

    private fun cycleToCV(entry: CycleEntity) = ContentValues().apply {
        put("date", entry.date)
        put("status", entry.status.name)
        put("symptoms", entry.symptoms)
        put("flowIntensity", entry.flowIntensity)
        put("notes", entry.notes)
        put("isHabitDay", if (entry.isHabitDay) 1 else 0)
        put("istihadaType", entry.istihadaType.name)
        put("timestamp", entry.timestamp)
    }

    suspend fun getLastHaidhEndDate(): String? = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT date FROM cycles WHERE status = ? ORDER BY date DESC LIMIT 1", arrayOf(MenstrualStatus.HAIDH.name)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    suspend fun getAverageCycleLength(): Int = withContext(Dispatchers.IO) {
        val cursor = db.rawQuery("""
            SELECT MIN(diff) FROM (
                SELECT julianday(b.date) - julianday(a.date) AS diff
                FROM cycles a, cycles b
                WHERE a.status = ? AND b.status = ?
                AND a.date < b.date
                AND b.date = (SELECT MIN(c.date) FROM cycles c WHERE c.status = ? AND c.date > a.date)
            )
        """.trimIndent(), arrayOf(MenstrualStatus.HAIDH.name, MenstrualStatus.HAIDH.name, MenstrualStatus.HAIDH.name))
        cursor.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else 28
        }
    }

    suspend fun getAverageHaidhLength(): Int = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT flowIntensity FROM cycles WHERE status = ? ORDER BY date ASC", arrayOf(MenstrualStatus.HAIDH.name)).use { c ->
            val lengths = mutableListOf<Int>()
            var currentCount = 0
            while (c.moveToNext()) {
                currentCount++
            }
            if (currentCount > 0) {
                val totalDays = c.count
                val cycles = c.count / 28
                if (cycles > 0) totalDays / cycles else 6
            } else 6
        }
    }

    suspend fun getPhasesInRange(startDate: String, endDate: String): List<CyclePhaseEntity> = withContext(Dispatchers.IO) {
        db.rawQuery("SELECT * FROM cycle_phases WHERE startDate BETWEEN ? AND ? ORDER BY startDate ASC", arrayOf(startDate, endDate)).use { c ->
            val result = mutableListOf<CyclePhaseEntity>()
            while (c.moveToNext()) result.add(phaseFromCursor(c))
            result
        }
    }

    suspend fun deletePhasesInRange(startDate: String, endDate: String, status: MenstrualStatus) = withContext(Dispatchers.IO) {
        db.delete("cycle_phases", "startDate >= ? AND startDate <= ? AND status = ?", arrayOf(startDate, endDate, status.name))
    }

    suspend fun getMonthFlowData(year: Int, month: Int): Map<Int, Int> = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        cal.set(year, month - 1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val result = mutableMapOf<Int, Int>()
        db.rawQuery("SELECT date, flowIntensity FROM cycles WHERE date BETWEEN ? AND ?", arrayOf(startDate, endDate)).use { c ->
            while (c.moveToNext()) {
                val dateStr = c.getString(0)
                val day = dateStr.split("-")[2].toIntOrNull() ?: continue
                result[day] = c.getInt(1)
            }
        }
        result
    }

    suspend fun getMonthIstihadaData(year: Int, month: Int): Map<Int, IstihadaType> = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        cal.set(year, month - 1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val result = mutableMapOf<Int, IstihadaType>()
        db.rawQuery("SELECT date, istihadaType FROM cycles WHERE date BETWEEN ? AND ? AND istihadaType IS NOT NULL AND istihadaType != ''", arrayOf(startDate, endDate)).use { c ->
            while (c.moveToNext()) {
                val dateStr = c.getString(0)
                val day = dateStr.split("-")[2].toIntOrNull() ?: continue
                result[day] = try { IstihadaType.valueOf(c.getString(1)) } catch (_: Exception) { IstihadaType.NONE }
            }
        }
        result
    }

    suspend fun getMonthSymptomsData(year: Int, month: Int): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        cal.set(year, month - 1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val result = mutableMapOf<Int, Boolean>()
        db.rawQuery("SELECT date, symptoms FROM cycles WHERE date BETWEEN ? AND ? AND symptoms != ''", arrayOf(startDate, endDate)).use { c ->
            while (c.moveToNext()) {
                val dateStr = c.getString(0)
                val day = dateStr.split("-")[2].toIntOrNull() ?: continue
                result[day] = c.getString(1).isNotBlank()
            }
        }
        result
    }
}
