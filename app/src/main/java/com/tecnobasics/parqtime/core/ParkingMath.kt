package com.tecnobasics.parqtime.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
fun LocalDateTime.toEpochMs(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

object ParkingMath {
    /** Minutos restantes en los que suena cada aviso. 0 = se acabó el tiempo. */
    val MILESTONES = listOf(25, 10, 5, 0)

    /** Minutos de estacionamiento que compra [amountCents] con la tarifa por 15 minutos. */
    fun minutesFor(amountCents: Long, rateCentsPer15: Long): Int =
        if (amountCents <= 0 || rateCentsPer15 <= 0) 0 else (amountCents * 15 / rateCentsPer15).toInt()

    fun centsFor(minutes: Int, rateCentsPer15: Long): Long = rateCentsPer15 * minutes / 15

    /**
     * Momento real en que se habrán consumido [paidSeconds] de tiempo cobrable desde [start].
     * Fuera del horario de cobro el reloj se detiene y se reanuda al siguiente inicio de servicio.
     */
    fun instantAfter(hours: ServiceHours, start: LocalDateTime, paidSeconds: Long): LocalDateTime {
        var left = paidSeconds.coerceAtLeast(0)
        for (w in hours.windowsFrom(start)) {
            val from = if (start.isAfter(w.start)) start else w.start
            val available = Duration.between(from, w.end).seconds
            // Estrictamente menor: si el tiempo cae justo en el cierre, el aviso pasa al siguiente
            // inicio de servicio (de noche no hay multa; importa a las 8:00).
            if (left < available) return from.plusSeconds(left)
            left -= available
        }
        error("Secuencia de horarios vacía")
    }

    /** Segundos cobrables transcurridos entre [start] y [now]. */
    fun consumedSeconds(hours: ServiceHours, start: LocalDateTime, now: LocalDateTime): Long {
        if (!now.isAfter(start)) return 0
        var total = 0L
        for (w in hours.windowsFrom(start)) {
            if (!w.start.isBefore(now)) break
            val a = if (start.isAfter(w.start)) start else w.start
            val b = if (now.isBefore(w.end)) now else w.end
            if (b.isAfter(a)) total += Duration.between(a, b).seconds
        }
        return total
    }

    fun remainingSeconds(hours: ServiceHours, start: LocalDateTime, paidMinutes: Int, now: LocalDateTime): Long =
        paidMinutes * 60L - consumedSeconds(hours, start, now)

    /** Hito → momento real en que suena. Se omiten avisos mayores o iguales al tiempo pagado. */
    fun milestones(hours: ServiceHours, start: LocalDateTime, paidMinutes: Int): List<Pair<Int, LocalDateTime>> =
        MILESTONES
            .filter { it == 0 || paidMinutes > it }
            .map { it to instantAfter(hours, start, (paidMinutes - it) * 60L) }
}
