package com.remindly.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Jalali (Shamsi) calendar conversion.
 *
 * Timestamps are still stored as epoch milliseconds, so alarms, time zones and
 * daylight-saving changes behave exactly as before. Only the human-readable
 * calendar changes: every date shown in the app is Jalali.
 *
 * Uses the Borkowski break-year algorithm, valid for Jalali years 1178-3177.
 */
object Jalali {

    data class Date(val year: Int, val month: Int, val day: Int)

    val monthNames = arrayOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    )

    private const val JDN_EPOCH_OFFSET = 2440588

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private class YearInfo(val leap: Int, val gy: Int, val march: Int)

    private fun div(a: Int, b: Int): Int = a / b

    private fun mod(a: Int, b: Int): Int = Math.floorMod(a, b)

    private fun jalCal(jy: Int, withoutLeap: Boolean): YearInfo {
        require(jy >= breaks.first() && jy < breaks.last()) { "Invalid Jalali year $jy" }

        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        var i = 1
        while (i < breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
            i++
        }

        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ++

        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG

        if (!withoutLeap) {
            if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
            val leap = mod(mod(n + 1, 33) - 1, 4)
            return YearInfo(leap = if (leap == -1) 4 else leap, gy = gy, march = march)
        }
        return YearInfo(leap = 0, gy = gy, march = march)
    }

    fun isLeap(jy: Int): Boolean = jalCal(jy, false).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    fun monthName(jm: Int): String = monthNames[jm - 1]

    fun from(date: LocalDate): Date =
        fromJdn((date.toEpochDay() + JDN_EPOCH_OFFSET).toInt())

    fun from(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Date =
        from(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    fun toLocalDate(date: Date): LocalDate {
        require(date.month in 1..12) { "Invalid Jalali month ${date.month}" }
        require(date.day in 1..monthLength(date.year, date.month)) {
            "Invalid Jalali day ${date.day}"
        }
        val r = jalCal(date.year, true)
        val jdn = LocalDate.of(r.gy, 3, r.march).toEpochDay().toInt() + JDN_EPOCH_OFFSET +
            (date.month - 1) * 31 - (date.month / 7) * (date.month - 7) + date.day - 1
        return LocalDate.ofEpochDay((jdn - JDN_EPOCH_OFFSET).toLong())
    }

    /** "18 Aban 1405" */
    fun label(date: Date): String = "${date.day} ${monthName(date.month)} ${date.year}"

    private fun fromJdn(jdn: Int): Date {
        val gy = LocalDate.ofEpochDay((jdn - JDN_EPOCH_OFFSET).toLong()).year
        var jy = gy - 621
        val r = jalCal(jy, false)
        val jdn1f = LocalDate.of(gy, 3, r.march).toEpochDay().toInt() + JDN_EPOCH_OFFSET

        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return Date(jy, 1 + k / 31, k % 31 + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return Date(jy, 7 + k / 30, k % 30 + 1)
    }
}
