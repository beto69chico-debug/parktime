package com.tecnobasics.parqtime.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Intervalo [start, end) en el que el parquímetro cobra. */
data class Window(val start: LocalDateTime, val end: LocalDateTime) {
    operator fun contains(t: LocalDateTime): Boolean = !t.isBefore(start) && t.isBefore(end)
}

/**
 * Horario de cobro de ecoParq.
 * Cada día de cobro tiene una ventana que empieza a [start] y termina a la hora de cierre de ese día.
 * Si la hora de cierre es menor o igual que el inicio (p. ej. 01:00), cierra al día siguiente.
 * Domingos (y, si se configura, cualquier día sin cobro) no tienen ventana.
 */
data class ServiceHours(
    val start: LocalTime = LocalTime.of(8, 0),
    val endMonTue: LocalTime = LocalTime.of(20, 0),
    val endWedSat: LocalTime = LocalTime.of(1, 0),
    val sundayFree: Boolean = true,
) {
    fun windowFor(date: LocalDate): Window? {
        val endTime = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> if (sundayFree) return null else endMonTue
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> endMonTue
            else -> endWedSat
        }
        val s = date.atTime(start)
        var e = date.atTime(endTime)
        if (!e.isAfter(s)) e = e.plusDays(1)
        return Window(s, e)
    }

    /** Ventanas en orden cronológico, empezando por la que contiene [from] o la siguiente. */
    fun windowsFrom(from: LocalDateTime): Sequence<Window> = sequence {
        // Se empieza ayer: una ventana que cierra a la 1:00 puede seguir abierta hoy.
        var d = from.toLocalDate().minusDays(1)
        var lastEnd: LocalDateTime? = null
        while (true) {
            val w = windowFor(d)
            if (w != null && w.end.isAfter(from)) {
                // Si se configuran horarios que se traslapan, se recorta para no contar doble.
                val s = if (lastEnd != null && w.start.isBefore(lastEnd)) lastEnd else w.start
                if (w.end.isAfter(s)) {
                    yield(Window(s, w.end))
                    lastEnd = w.end
                }
            }
            d = d.plusDays(1)
        }
    }

    fun isInService(t: LocalDateTime): Boolean = t in windowsFrom(t).first()
}
