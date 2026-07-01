package islamic.duas

import java.util.Calendar

object HijriCalendar {

    fun toHijri(gregorian: Calendar = Calendar.getInstance()): Triple<Int, Int, Int> {
        val y = gregorian.get(Calendar.YEAR)
        val m = gregorian.get(Calendar.MONTH) + 1
        val d = gregorian.get(Calendar.DAY_OF_MONTH)

        val jd = gregToJulian(y, m, d)
        return julianToHijri(jd)
    }

    fun getHijriDateString(): String {
        val (year, month, day) = toHijri()
        val monthNames = arrayOf(
            "محرم", "صفر", "ربیع الأول", "ربیع الآخر",
            "جمادی الأول", "جمادی الآخر", "رجب", "شعبان",
            "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )
        return "${day} ${monthNames[month - 1]} ${year}ھ"
    }

    private fun gregToJulian(year: Int, month: Int, day: Int): Int {
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = y / 100
        val b = 2 - a + a / 4
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day + b - 1524
    }

    private fun julianToHijri(jd: Int): Triple<Int, Int, Int> {
        val epoch = 1948440 // Julian day of Hijri epoch (July 16, 622 CE)
        val y = 10631.0 / 30.0
        val dy = jd - epoch
        var year = (dy / y).toInt()
        var remaining = (dy - (year * y).toInt()).toDouble()
        if (remaining < 0) {
            remaining += y
            year--
        }
        val monthLengths = intArrayOf(30, 59, 89, 118, 148, 177, 207, 236, 266, 295, 325, 355)
        val dayOfYear = remaining.toInt() + 1
        var month = 0
        for (i in monthLengths.indices) {
            if (dayOfYear <= monthLengths[i]) {
                month = i + 1
                break
            }
        }
        val day = if (month == 1) dayOfYear else dayOfYear - monthLengths[month - 2]
        return Triple(year + 1, month, day)
    }
}
