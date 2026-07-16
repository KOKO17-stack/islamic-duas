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
        put("timestamp", entry.timestamp)
    }
}
