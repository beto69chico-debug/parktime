package com.tecnobasics.parqtime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tecnobasics.parqtime.Notifs
import com.tecnobasics.parqtime.data.ParqStore

/** Llega a la hora de cada aviso y enciende la alarma. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val stage = intent.getIntExtra(AlarmScheduler.EXTRA_STAGE, -1)
        if (stage < 0 || ParqStore(ctx).session == null) return
        AlarmService.start(ctx, stage)
        Notifs.updateActive(ctx)
    }
}
