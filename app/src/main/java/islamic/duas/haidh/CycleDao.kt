package islamic.duas.haidh

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {

    @Query("SELECT * FROM cycles WHERE date = :date LIMIT 1")
    suspend fun getDayStatus(date: String): CycleEntity?

    @Query("SELECT * FROM cycles WHERE date = :date LIMIT 1")
    fun getDayStatusFlow(date: String): Flow<CycleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayStatus(entry: CycleEntity)

    @Query("SELECT * FROM cycles WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getCycleRange(startDate: String, endDate: String): List<CycleEntity>

    @Query("SELECT * FROM cycles WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getCycleRangeFlow(startDate: String, endDate: String): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles WHERE status = :status ORDER BY date DESC LIMIT 90")
    suspend fun getDaysByStatus(status: MenstrualStatus): List<CycleEntity>

    @Query("SELECT * FROM cycles ORDER BY date DESC LIMIT 365")
    suspend fun getAllDays(): List<CycleEntity>

    @Query("SELECT * FROM cycles ORDER BY date DESC LIMIT 365")
    fun getAllDaysFlow(): Flow<List<CycleEntity>>

    // Symptoms
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSymptom(symptom: SymptomEntity)

    @Query("SELECT * FROM symptoms WHERE date = :date ORDER BY severity DESC")
    suspend fun getSymptoms(date: String): List<SymptomEntity>

    @Query("DELETE FROM symptoms WHERE date = :date AND symptom = :symptom")
    suspend fun removeSymptom(date: String, symptom: String)

    // Cycle phases
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhase(phase: CyclePhaseEntity)

    @Query("SELECT * FROM cycle_phases ORDER BY startDate DESC LIMIT 20")
    suspend fun getRecentPhases(): List<CyclePhaseEntity>

    @Query("SELECT * FROM cycle_phases ORDER BY startDate DESC LIMIT 20")
    fun getRecentPhasesFlow(): Flow<List<CyclePhaseEntity>>

    @Query("SELECT * FROM cycle_phases WHERE startDate <= :date AND endDate >= :date LIMIT 1")
    suspend fun getPhaseForDate(date: String): CyclePhaseEntity?

    @Query("SELECT * FROM cycle_phases WHERE status = :status ORDER BY startDate DESC LIMIT 12")
    suspend fun getPhasesByStatus(status: MenstrualStatus): List<CyclePhaseEntity>

    @Query("SELECT AVG(cycleDay) FROM cycle_phases WHERE status = :status AND cycleDay > 0")
    suspend fun getAveragePhaseLength(status: MenstrualStatus): Double?

    @Query("SELECT MIN(cycleDay) FROM cycle_phases WHERE status = :status AND cycleDay > 0")
    suspend fun getMinPhaseLength(status: MenstrualStatus): Int?

    @Query("SELECT MAX(cycleDay) FROM cycle_phases WHERE status = :status AND cycleDay > 0")
    suspend fun getMaxPhaseLength(status: MenstrualStatus): Int?

    @Query("SELECT * FROM cycle_phases WHERE status = 'HAIDH' ORDER BY startDate DESC LIMIT 2")
    suspend fun getLastTwoHaidhPhases(): List<CyclePhaseEntity>

    @Query("SELECT * FROM cycle_phases WHERE status = 'TUHR' ORDER BY startDate DESC LIMIT 2")
    suspend fun getLastTwoTuhrPhases(): List<CyclePhaseEntity>

    // Statistics
    @Query("SELECT COUNT(*) FROM cycles WHERE status = 'HAIDH' AND date BETWEEN :start AND :end")
    suspend fun countHaidhDaysInRange(start: String, end: String): Int
}
