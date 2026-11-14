package com.remindly.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * All UI text is English, but dates follow the Jalali (Shamsi) calendar.
 * Month and weekday names are Latin transliterations so the interface stays
 * English while the calendar stays Iranian.
 */
object TimeFmt {

    private val locale = Locale.ENGLISH
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val chipTime = DateTimeFormatter.ofPattern("hh:mm a", locale)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun time(at: Long): String =
        timeFormatter.format(Instant.ofEpochMilli(at).atZone(zone()))

    /** "Today", "Tomorrow", "Yesterday", "Mon 25 Aban" or "25 Aban 1404". */
    fun day(at: Long): String {
        val date = Instant.ofEpochMilli(at).atZone(zone()).toLocalDate()
        val today = LocalDate.now(zone())
        val jalali = Jalali.from(date)
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> {
                val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                if (jalali.year == Jalali.from(today).year) {
                    "$weekday ${jalali.day} ${Jalali.monthName(jalali.month)}"
                } else {
                    "${jalali.day} ${Jalali.monthName(jalali.month)} ${jalali.year}"
                }
            }
        }
    }

    fun dateTime(at: Long): String = "${day(at)} · ${time(at)}"

    /** Full Jalali date for pickers: "Mon 25 Aban 1405". */
    fun dateLong(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        val jalali = Jalali.from(date)
        return "$weekday ${jalali.day} ${Jalali.monthName(jalali.month)} ${jalali.year}"
    }

    fun shortTime(hour: Int, minute: Int): String =
        chipTime.format(java.time.LocalTime.of(hour, minute))

    fun isOverdue(at: Long): Boolean = at < System.currentTimeMillis() - 60_000L

    /** Compact human span: "3d 4h", "2h 10m", "45m". */
    fun span(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return when {
            days >= 7 -> {
                val weeks = days / 7
                val rest = days % 7
                if (rest > 0) "${weeks}w ${rest}d" else "${weeks}w"
            }
            days >= 1 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
            hours >= 1 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            else -> "${minutes.coerceAtLeast(1)}m"
        }
    }

    fun countdown(at: Long, now: Long = System.currentTimeMillis()): String {
        val diff = at - now
        return when {
            diff < -60_000L -> "overdue by ${span(-diff)}"
            diff < 0L -> "due now"
            diff < 60_000L -> "in under a minute"
            else -> "in ${span(diff)}"
        }
    }
}
