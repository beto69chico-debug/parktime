package com.tecnobasics.parqtime.core

import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val es: Locale = Locale.forLanguageTag("es-MX")

    fun money(cents: Long): String = "$" + moneyInput(cents)

    fun moneyInput(cents: Long): String = String.format(Locale.US, "%d.%02d", cents / 100, abs(cents % 100))

    /** "$13.60", "13,6", "13" → centavos. Vacío = 0. Texto inválido = null. */
    fun parseMoney(text: String): Long? {
        val t = text.trim().removePrefix("$").replace(',', '.')
        if (t.isEmpty()) return 0
        val bd = t.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0) return null
        return bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h " + String.format(Locale.US, "%02d", m) + " min"
        }
    }

    fun clock(t: LocalDateTime, h24: Boolean): String = clock(t.toLocalTime(), h24)

    fun clock(t: LocalTime, h24: Boolean): String =
        t.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", es))

    /** "hoy", "mañana" o el nombre del día. */
    fun dayLabel(t: LocalDateTime, today: LocalDate): String = when (t.toLocalDate()) {
        today -> "hoy"
        today.plusDays(1) -> "mañana"
        else -> "el " + t.format(DateTimeFormatter.ofPattern("EEEE", es))
    }

    fun countdown(seconds: Long): String {
        val s = abs(seconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%02d:%02d", m, ss)
    }

    /** "8", "08:00", "800", "2000" → hora. */
    fun parseTime(text: String): LocalTime? {
        val t = text.trim()
        val digits = t.filter { it.isDigit() }
        val (h, m) = when {
            ':' in t -> {
                val p = t.split(':')
                if (p.size != 2) return null
                (p[0].trim().toIntOrNull() ?: return null) to (p[1].trim().toIntOrNull() ?: return null)
            }
            digits.length in 1..2 -> digits.toInt() to 0
            digits.length in 3..4 -> digits.dropLast(2).toInt() to digits.takeLast(2).toInt()
            else -> return null
        }
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    fun timeInput(t: LocalTime): String = String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
}
