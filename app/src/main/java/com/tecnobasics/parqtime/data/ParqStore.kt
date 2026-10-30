package com.tecnobasics.parqtime.data

import android.content.Context
import com.tecnobasics.parqtime.core.ServiceHours
import java.time.LocalTime

/** Un conteo activo: cuándo empezó, cuántos minutos se pagaron y cuánto dinero. */
data class Session(val startMs: Long, val paidMinutes: Int, val amountCents: Long)

/** Todo lo que guarda la app, en el propio teléfono (sin internet). */
class ParqStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("parqtime", Context.MODE_PRIVATE)

    var rateCents: Long
        get() = p.getLong(K_RATE, 340L)
        set(v) {
            p.edit().putLong(K_RATE, v).apply()
        }

    var hours: ServiceHours
        get() = ServiceHours(
            start = time(K_START, 8 * 60),
            endMonTue = time(K_END_MT, 20 * 60),
            endWedSat = time(K_END_WS, 1 * 60),
            sundayFree = p.getBoolean(K_SUNDAY, true),
        )
        set(h) {
            p.edit()
                .putInt(K_START, h.start.toMinutes())
                .putInt(K_END_MT, h.endMonTue.toMinutes())
                .putInt(K_END_WS, h.endWedSat.toMinutes())
                .putBoolean(K_SUNDAY, h.sundayFree)
                .apply()
        }

    var session: Session?
        get() = if (!p.getBoolean(K_ACTIVE, false)) null
        else Session(p.getLong(K_S_START, 0L), p.getInt(K_S_MIN, 0), p.getLong(K_S_AMOUNT, 0L))
        set(s) {
            val e = p.edit()
            if (s == null) e.putBoolean(K_ACTIVE, false)
            else e.putBoolean(K_ACTIVE, true)
                .putLong(K_S_START, s.startMs)
                .putInt(K_S_MIN, s.paidMinutes)
                .putLong(K_S_AMOUNT, s.amountCents)
            e.commit() // síncrono: el receptor de alarmas puede leerlo justo después
        }

    private fun time(key: String, def: Int): LocalTime {
        val v = p.getInt(key, def)
        return LocalTime.of((v / 60) % 24, v % 60)
    }

    private fun LocalTime.toMinutes() = hour * 60 + minute

    private companion object {
        const val K_RATE = "rate"
        const val K_START = "svc_start"
        const val K_END_MT = "svc_end_mt"
        const val K_END_WS = "svc_end_ws"
        const val K_SUNDAY = "sunday_free"
        const val K_ACTIVE = "active"
        const val K_S_START = "s_start"
        const val K_S_MIN = "s_min"
        const val K_S_AMOUNT = "s_amount"
    }
}
