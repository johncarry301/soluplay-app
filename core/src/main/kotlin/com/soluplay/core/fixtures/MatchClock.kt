package com.soluplay.core.fixtures

import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * When a match kicks off.
 *
 * The board publishes the kick-off as a string and the shape of that string
 * has changed before, so it is parsed by hand: `java.time` needs API 26 and
 * the app runs on API 21.
 */
object MatchClock {

    private val pattern = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?\s*(Z|[+-]\d{2}:?\d{2})?""",
    )

    /** The kick-off in epoch millis, or null when the board sent something unreadable. */
    fun parseKickOff(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        text.toLongOrNull()?.let { epoch -> return if (epoch > 0) epoch else null }

        val match = pattern.matchEntire(text) ?: return null
        val (year, month, day, hour, minute, second, millis, zone) = match.destructured

        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.clear()
        calendar.set(
            year.toInt(),
            month.toInt() - 1,
            day.toInt(),
            hour.toInt(),
            minute.toInt(),
            second.ifEmpty { "0" }.toInt(),
        )

        val offsetMinutes = offsetMinutes(zone) ?: return null
        val millisOfSecond = millis.ifEmpty { "0" }.padEnd(3, '0').toInt()
        return calendar.timeInMillis - offsetMinutes * 60_000L + millisOfSecond
    }

    /** The clock left until the kick-off, or null when the match has started. */
    fun remaining(startAtMillis: Long, nowMillis: Long): Remaining? {
        val left = startAtMillis - nowMillis
        if (left <= 0) return null
        val total = left / 1000
        return Remaining(
            hours = (total / 3600).toInt(),
            minutes = ((total % 3600) / 60).toInt(),
            seconds = (total % 60).toInt(),
        )
    }

    private fun offsetMinutes(zone: String): Int? = when {
        zone.isEmpty() -> 0
        zone == "Z" -> 0
        zone.length == 6 && (zone[0] == '+' || zone[0] == '-') -> {
            val minutes = zone.substring(1, 3).toInt() * 60 + zone.substring(4, 6).toInt()
            if (zone[0] == '-') -minutes else minutes
        }
        zone.length == 5 && (zone[0] == '+' || zone[0] == '-') -> {
            val minutes = zone.substring(1, 3).toInt() * 60 + zone.substring(3, 5).toInt()
            if (zone[0] == '-') -minutes else minutes
        }
        else -> null
    }
}

/** The three numbers of the clock left until kick-off. */
data class Remaining(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)
