package hu.szabonorbert.mokusors.util

import java.util.Calendar
import java.util.Date

object HungarianCalendar {

    private fun Date.toMonthDay(): String {
        val c = Calendar.getInstance().apply { time = this@toMonthDay }
        return "%02d-%02d".format(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun Date.toFullDate(): String {
        val c = Calendar.getInstance().apply { time = this@toFullDate }
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun Calendar.toMonthDay(): String =
        "%02d-%02d".format(get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))

    // Fixed public holidays (month-day, year-independent)
    private val fixedHolidays = setOf(
        "01-01", // Újév
        "03-15", // Nemzeti ünnep
        "05-01", // Munka ünnepe
        "08-20", // Államalapítás
        "10-23", // Forradalom
        "11-01", // Mindenszentek
        "12-25", // Karácsony
        "12-26"  // Karácsony második napja
    )

    // Extra bridge-day holidays declared by the government (yyyy-MM-dd)
    private val extraHolidays = setOf(
        "2024-08-19", // Bridge day (for 08-20 Tue)
        "2024-12-24", // Christmas Eve declared holiday
        "2025-04-18", // Bridge day (for 04-19 Sat → 04-21 Mon)
        "2025-10-24", // Bridge day (for 10-23 Thu)
        "2025-12-24", // Christmas Eve
        "2026-01-02", // Bridge day (for 01-01 Thu)
        "2026-08-21"  // Bridge day (for 08-20 Thu)
    )

    // Saturdays that are workdays due to transfer (yyyy-MM-dd)
    private val transferredWorkdays = setOf(
        "2024-04-13", // compensates for 04-19 bridge
        "2024-12-07", // compensates for 12-24 holiday
        "2025-05-02", // compensates for 05-01 Thu bridge
        "2025-10-18", // compensates for 10-24 bridge
        "2026-01-03", // compensates for 01-02 bridge
        "2026-10-24"  // compensates for 10-23 bridge (itself Sat)
    )

    // Gauss Easter algorithm (Gregorian calendar)
    private fun easterDate(year: Int): Calendar {
        val a = year % 19
        val b = year % 4
        val c = year % 7
        val d = (19 * a + 24) % 30
        val e = (2 * b + 4 * c + 6 * d + 5) % 7
        var day = 22 + d + e
        var month = 3
        if (day > 31) { day -= 31; month = 4 }
        if (d == 29 && e == 6) { day = 19; month = 4 }
        if (d == 28 && e == 6 && a > 10) { day = 18; month = 4 }
        return Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0) }
    }

    private val easterCache = mutableMapOf<Int, Set<String>>()

    private fun easterBasedHolidays(year: Int): Set<String> {
        return easterCache.getOrPut(year) {
            val easter = easterDate(year)
            val goodFriday = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
            val easterMonday = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val whitMonday = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 50) }
            setOf(
                goodFriday.toMonthDay(),
                easterMonday.toMonthDay(),
                whitMonday.toMonthDay()
            )
        }
    }

    fun isHoliday(date: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = date }
        val year = cal.get(Calendar.YEAR)
        val monthDay = date.toMonthDay()
        val fullDate = date.toFullDate()
        return fixedHolidays.contains(monthDay) ||
               easterBasedHolidays(year).contains(monthDay) ||
               extraHolidays.contains(fullDate)
    }

    fun isTransferredWorkday(date: Date): Boolean {
        return transferredWorkdays.contains(date.toFullDate())
    }

    // Returns true if the date should be treated as a non-working day
    fun isNonWorkingDay(date: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = date }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val transferred = isTransferredWorkday(date)
        return (isWeekend && !transferred) || isHoliday(date)
    }
}
