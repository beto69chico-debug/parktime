package com.tecnobasics.parqtime

import android.content.Context
import com.tecnobasics.parqtime.alarm.AlarmScheduler
import com.tecnobasics.parqtime.core.ParkingMath
import com.tecnobasics.parqtime.core.toLocal
import com.tecnobasics.parqtime.data.ParqStore
import com.tecnobasics.parqtime.data.Session
import java.time.LocalDateTime

/** Foto del conteo en un instante. */
data class Status(
    val remainingSec: Long,
    val expiry: LocalDateTime,
    val paused: Boolean,
    val resumeAt: LocalDateTime?,
)

object SessionController {

    fun status(store: ParqStore, s: Session, nowMs: Long = System.currentTimeMillis()): Status {
        val h = store.hours
        val start = s.startMs.toLocal()
        val now = nowMs.toLocal()
        val remaining = ParkingMath.remainingSeconds(h, start, s.paidMinutes, now)
        val expiry = ParkingMath.instantAfter(h, start, s.paidMinutes * 60L)
        val paused = remaining > 0 && !h.isInService(now)
        val resume = if (paused) h.windowsFrom(now).first().start else null
        return Status(remaining, expiry, paused, resume)
    }

    fun start(ctx: Context, amountCents: Long) {
        val store = ParqStore(ctx)
        val minutes = ParkingMath.minutesFor(amountCents, store.rateCents)
        if (minutes <= 0) return
        store.session = Session(System.currentTimeMillis(), minutes, amountCents)
        refresh(ctx)
    }

    /** Suma tiempo al conteo. Si ya venció, el parquímetro empieza de cero: conteo nuevo desde ahora. */
    fun addTime(ctx: Context, amountCents: Long) {
        val store = ParqStore(ctx)
        val s = store.session
        if (s == null || status(store, s).remainingSec <= 0) {
            start(ctx, amountCents)
            return
        }
        val minutes = ParkingMath.minutesFor(amountCents, store.rateCents)
        if (minutes <= 0) return
        store.session = s.copy(paidMinutes = s.paidMinutes + minutes, amountCents = s.amountCents + amountCents)
        refresh(ctx)
    }

    fun end(ctx: Context) {
        ParqStore(ctx).session = null
        refresh(ctx)
    }

    /** Vuelve a programar alarmas y notificación con lo guardado (al abrir, al reiniciar, al cambiar ajustes). */
    fun refresh(ctx: Context) {
        AlarmScheduler.schedule(ctx)
        Notifs.updateActive(ctx)
    }
}
