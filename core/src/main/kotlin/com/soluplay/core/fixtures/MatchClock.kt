package com.soluplay.core.fixtures

import java.util.Calendar
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
            days = (total / 86_400).toInt(),
            hours = ((total % 86_400) / 3600).toInt(),
            minutes = ((total % 3600) / 60).toInt(),
            seconds = (total % 60).toInt(),
        )
    }

    /**
     * The kick-off as the card prints it: the date, and the time of day in
     * Bangladesh.
     *
     * Bangladesh is six hours ahead of the clock and keeps no daylight saving,
     * so the offset is a constant rather than a lookup.
     */
    fun kickoffInBdt(millis: Long): BdtTime {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.timeInMillis = millis + BDT_OFFSET_MILLIS

        val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        return BdtTime(
            date = "%s %d, %d".format(
                MONTHS[calendar.get(Calendar.MONTH)],
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.YEAR),
            ),
            time = "%02d:%02d %s BDT".format(
                hour12,
                calendar.get(Calendar.MINUTE),
                if (hour24 < 12) "AM" else "PM",
            ),
        )
    }

    /**
     * The day a match falls on, in words where words help.
     *
     * A card that says "TODAY" or "TOMORROW" tells the reader more than the
     * date it replaces, and the two are counted in Bangladesh time so the card
     * never says tomorrow at ten at night.
     */
    fun dayWord(millis: Long, nowMillis: Long): String {
        val day = Math.floorDiv(millis + BDT_OFFSET_MILLIS, 86_400_000L)
        val today = Math.floorDiv(nowMillis + BDT_OFFSET_MILLIS, 86_400_000L)
        return when (day) {
            today -> "TODAY"
            today + 1 -> "TOMORROW"
            else -> ""
        }
    }

    /**
     * The clock the card shows: days, hours and minutes while the kick-off is
     * still more than a day away, and hours, minutes and seconds for the last
     * day — the hour before the match is the one a viewer watches most closely,
     * so that is where the seconds are.
     */
    fun countdownLabel(remaining: Remaining): String =
        if (remaining.days > 0) {
            "%dd %02dh %02dm".format(remaining.days, remaining.hours, remaining.minutes)
        } else {
            "%02dh %02dm %02ds".format(remaining.hours, remaining.minutes, remaining.seconds)
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

    /** Bangladesh is six hours ahead of the clock, all year. */
    private const val BDT_OFFSET_MILLIS = 6L * 60L * 60L * 1000L

    private val MONTHS = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )
}

/** The four numbers of the clock left until kick-off. */
data class Remaining(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

/** The kick-off as the card prints it. */
data class BdtTime(
    val date: String,
    val time: String,
)
