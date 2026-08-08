package islamic.duas.haidh

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shared Haidh phase tracking. Used by both the health tracker calendar and the
 * full-screen daily log dialog so the calendar's cycle-day / predicted-haidh
 * rendering stays consistent no matter where the entry was recorded.
 */
object CyclePhaseHelper {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun updatePhaseTracking(dao: CycleDao, dateStr: String, status: MenstrualStatus) {
        val blockStart = findHaidhBlockStart(dao, dateStr)
        val blockEnd = findHaidhBlockEnd(dao, dateStr)

        dao.deletePhasesInRange(blockStart, blockEnd, MenstrualStatus.HAIDH)

        var scanDate = LocalDate.parse(blockStart, formatter)
        val endDate = LocalDate.parse(blockEnd, formatter)
        var cycleDay = 1
        while (!scanDate.isAfter(endDate)) {
            val scanDateStr = scanDate.format(formatter)
            val entry = dao.getDayStatus(scanDateStr)
            if (entry?.status == MenstrualStatus.HAIDH) {
                dao.upsertPhase(CyclePhaseEntity(
                    startDate = scanDateStr,
                    endDate = scanDateStr,
                    status = MenstrualStatus.HAIDH,
                    cycleDay = cycleDay
                ))
                cycleDay++
            }
            scanDate = scanDate.plusDays(1)
        }
    }

    private suspend fun findHaidhBlockStart(dao: CycleDao, fromDate: String): String {
        var date = LocalDate.parse(fromDate, formatter)
        while (true) {
            val prevDate = date.minusDays(1)
            val prevDateStr = prevDate.format(formatter)
            val prevEntry = dao.getDayStatus(prevDateStr)
            if (prevEntry?.status != MenstrualStatus.HAIDH) {
                return date.format(formatter)
            }
            date = prevDate
        }
    }

    private suspend fun findHaidhBlockEnd(dao: CycleDao, fromDate: String): String {
        var date = LocalDate.parse(fromDate, formatter)
        val todayStr = LocalDate.now().format(formatter)
        while (true) {
            val nextDate = date.plusDays(1)
            val nextDateStr = nextDate.format(formatter)
            if (nextDateStr > todayStr) return date.format(formatter)
            val nextEntry = dao.getDayStatus(nextDateStr)
            if (nextEntry?.status != MenstrualStatus.HAIDH) {
                return date.format(formatter)
            }
            date = nextDate
        }
    }
}
