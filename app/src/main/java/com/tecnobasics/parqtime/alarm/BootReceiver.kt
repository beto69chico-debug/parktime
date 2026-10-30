package com.tecnobasics.parqtime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tecnobasics.parqtime.SessionController

/** Android borra las alarmas al apagar el teléfono o cambiar la hora: aquí se vuelven a poner. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        SessionController.refresh(ctx)
    }
}
