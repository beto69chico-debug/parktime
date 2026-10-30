package com.tecnobasics.parqtime.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

// Calendario: 2026-10-26 lunes, 27 martes, 28 miércoles, 29 jueves, 31 sábado, 2026-11-01 domingo.
class ParkingMathTest {
    private val h = ServiceHours() // 8:00; Lun-Mar cierra 20:00; Mié-Sáb cierra 01:00; domingo gratis

    private fun t(d: Int, hh: Int, mm: Int, month: Int = 10) = LocalDateTime.of(2026, month, d, hh, mm)

    @Test fun pesosAMinutos() {
        assertEquals(15, ParkingMath.minutesFor(340, 340))
        assertEquals(60, ParkingMath.minutesFor(1360, 340))
        assertEquals(29, ParkingMath.minutesFor(660, 340)) // no se redondea hacia arriba
        assertEquals(0, ParkingMath.minutesFor(0, 340))
        assertEquals(1360L, ParkingMath.centsFor(60, 340))
    }

    @Test fun vencimientoSimple() {
        assertEquals(t(29, 15, 0), ParkingMath.instantAfter(h, t(29, 14, 0), 60 * 60))
    }

    @Test fun pausaAlCierreLunesMartesYReanudaALas8() {
        // Martes 19:50 + 30 min: 10 min hasta las 20:00, sigue miércoles 8:00 + 20 min
        assertEquals(t(28, 8, 20), ParkingMath.instantAfter(h, t(27, 19, 50), 30 * 60))
    }

    @Test fun miercolesASabadoCierraALa1() {
        // Miércoles 23:00 + 60 min: cabe completo antes de la 1:00
        assertEquals(t(29, 0, 0), ParkingMath.instantAfter(h, t(28, 23, 0), 60 * 60))
        // Jueves 00:50 (ventana del miércoles) + 30 min: 10 min, luego jueves 8:00 + 20
        assertEquals(t(29, 8, 20), ParkingMath.instantAfter(h, t(29, 0, 50), 30 * 60))
    }

    @Test fun domingoGratisSaltaALunes() {
        // Domingo 00:50 (ventana del sábado) + 30 min → lunes 8:20
        assertEquals(t(2, 8, 20, month = 11), ParkingMath.instantAfter(h, t(1, 0, 50, month = 11), 30 * 60))
    }

    @Test fun inicioFueraDeHorarioArrancaALas8() {
        assertEquals(t(26, 8, 15), ParkingMath.instantAfter(h, t(26, 7, 30), 15 * 60))
    }

    @Test fun restanteSeCongelaEnLaPausa() {
        assertEquals(20 * 60L, ParkingMath.remainingSeconds(h, t(27, 19, 50), 30, t(27, 22, 0)))
        assertEquals(20 * 60L, ParkingMath.remainingSeconds(h, t(27, 19, 50), 30, t(28, 8, 0)))
        assertEquals(10 * 60L, ParkingMath.remainingSeconds(h, t(27, 19, 50), 30, t(28, 8, 10)))
    }

    @Test fun avisos25_10_5_0() {
        val m = ParkingMath.milestones(h, t(29, 14, 0), 60).toMap()
        assertEquals(t(29, 14, 35), m[25])
        assertEquals(t(29, 14, 50), m[10])
        assertEquals(t(29, 14, 55), m[5])
        assertEquals(t(29, 15, 0), m[0])
    }

    @Test fun avisoDe25SeOmiteSiPagasteMenos() {
        val m = ParkingMath.milestones(h, t(29, 14, 0), 20).toMap()
        assertFalse(25 in m)
        assertEquals(t(29, 14, 10), m[10])
    }

    @Test fun avisosRespetanLaPausa() {
        // Martes 19:40 + 30 min: la de 25 min suena 19:45; la de 10 min ya cae el miércoles 8:00
        val m = ParkingMath.milestones(h, t(27, 19, 40), 30).toMap()
        assertEquals(t(27, 19, 45), m[25])
        assertEquals(t(28, 8, 0), m[10])
        assertEquals(t(28, 8, 10), m[0])
    }

    @Test fun enServicio() {
        assertTrue(h.isInService(t(29, 0, 30)))  // jueves madrugada, ventana del miércoles
        assertFalse(h.isInService(t(29, 1, 30)))
        assertFalse(h.isInService(t(27, 21, 0))) // martes después de las 20:00
        assertFalse(h.isInService(t(1, 12, 0, month = 11))) // domingo
    }

    @Test fun formatoYLectura() {
        assertEquals(1360L, Fmt.parseMoney("13.60"))
        assertEquals(1360L, Fmt.parseMoney("$13,6"))
        assertEquals(null, Fmt.parseMoney("abc"))
        assertEquals("$13.60", Fmt.money(1360))
        assertEquals("1 h 05 min", Fmt.duration(65))
        assertEquals("1:00:00", Fmt.countdown(3600))
        assertEquals("04:59", Fmt.countdown(299))
        assertEquals(LocalTime.of(20, 0), Fmt.parseTime("2000"))
        assertEquals(LocalTime.of(8, 0), Fmt.parseTime("8"))
        assertEquals(LocalTime.of(1, 0), Fmt.parseTime("01:00"))
    }
}
