package islamic.duas.haidh

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MenstrualStatus {
    TUHR,
    HAIDH,
    ISTIHADAH
}

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey
    val date: String, // yyyy-MM-dd
    val status: MenstrualStatus = MenstrualStatus.TUHR,
    val symptoms: String = "", // comma-separated symptoms
    val flowIntensity: Int = 0, // 0=none, 1=light, 2=moderate, 3=heavy
    val notes: String = "",
    val isHabitDay: Boolean = false, // predicted based on cycle history
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "symptoms")
data class SymptomEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val symptom: String,
    val severity: Int = 1 // 1-5
)

@Entity(tableName = "cycle_phases")
data class CyclePhaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startDate: String, // yyyy-MM-dd
    val endDate: String, // yyyy-MM-dd
    val status: MenstrualStatus,
    val cycleDay: Int = 1 // day of cycle (1 = first day of haidh)
)
