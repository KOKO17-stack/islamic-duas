package islamic.duas.haidh

enum class MenstrualStatus {
    TUHR,
    HAIDH
}

enum class IstihadaType {
    NONE,
    QALILA,
    MUTAWASITA,
    KATHIRA
}

data class CycleEntity(
    val date: String,
    val status: MenstrualStatus = MenstrualStatus.TUHR,
    val symptoms: String = "",
    val flowIntensity: Int = 0,
    val notes: String = "",
    val isHabitDay: Boolean = false,
    val istihadaType: IstihadaType = IstihadaType.NONE,
    val timestamp: Long = System.currentTimeMillis()
)

data class CyclePhaseEntity(
    val id: Long = 0,
    val startDate: String,
    val endDate: String,
    val status: MenstrualStatus,
    val cycleDay: Int = 1
)
