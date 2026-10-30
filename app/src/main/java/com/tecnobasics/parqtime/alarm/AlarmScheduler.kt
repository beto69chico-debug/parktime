package com.tecnobasics.parqtime.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tecnobasics.parqtime.MainActivity
import com.tecnobasics.parqtime.core.ParkingMath
import com.tecnobasics.parqtime.core.toEpochMs
import com.tecnobasics.parqtime.core.toLocal
import com.tecnobasics.parqtime.data.ParqStore

object AlarmScheduler {
    const val EXTRA_STAGE = "stage"

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Cancela todo y programa los avisos que aún no han pasado. */
    fun schedule(ctx: Context) {
        cancelAll(ctx)
        val store = ParqStore(ctx)
        val s = store.session ?: return
        val now = System.currentTimeMillis()
        for ((stage, at) in ParkingMath.milestones(store.hours, s.startMs.toLocal(), s.paidMinutes)) {
            val ms = at.toEpochMs()
            if (ms > now) set(ctx, stage, ms)
        }
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (stage in ParkingMath.MILESTONES) am.cancel(pending(ctx, stage))
    }

    private fun pending(ctx: Context, stage: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        100 + stage,
        Intent(ctx, AlarmReceiver::class.java)
            .setAction("com.tecnobasics.parqtime.ALARM_$stage")
            .putExtra(EXTRA_STAGE, stage),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun set(ctx: Context, stage: Int, atMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val op = pending(ctx, stage)
        try {
            if (canExact(ctx)) {
                // Modo "despertador": exacto, despierta al teléfono aunque esté en reposo profundo
                val show = PendingIntent.getActivity(
                    ctx, 1, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, op)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, op)
        }
    }
}
