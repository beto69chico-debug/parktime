package com.tecnobasics.parqtime.alarm

import android.content.Context
import android.text.format.DateFormat
import com.tecnobasics.parqtime.SessionController
import com.tecnobasics.parqtime.core.Fmt
import com.tecnobasics.parqtime.data.ParqStore

object AlarmTexts {
    const val STAGE_TEST = 99

    fun title(stage: Int): String = when (stage) {
        25 -> "Faltan 25 minutos"
        10 -> "¡Faltan 10 minutos!"
        5 -> "¡FALTAN 5 MINUTOS!"
        0 -> "¡SE ACABÓ EL TIEMPO!"
        else -> "Prueba de alarma"
    }

    fun hint(stage: Int): String = when (stage) {
        25 -> "Ve pensando en regresar al coche."
        10 -> "Ponte en camino al coche."
        5 -> "¡Corre! Quitar la araña cuesta $340."
        0 -> "Recarga ya o mueve el coche."
        else -> "Así sonará cada aviso."
    }

    /** Texto gigante de la pantalla de alarma. */
    fun big(stage: Int): String = when (stage) {
        0 -> "¡YA!"
        STAGE_TEST -> "TEST"
        else -> "$stage"
    }

    fun unit(stage: Int): String = when (stage) {
        0 -> "SE ACABÓ EL TIEMPO"
        STAGE_TEST -> "PRUEBA DE ALARMA"
        else -> "MINUTOS"
    }

    /** "Vence a las 14:37" del conteo activo, o null si no hay conteo. */
    fun expiryLine(ctx: Context): String? {
        val store = ParqStore(ctx)
        val s = store.session ?: return null
        val st = SessionController.status(store, s)
        val clock = Fmt.clock(st.expiry, DateFormat.is24HourFormat(ctx))
        return if (st.remainingSec <= 0) "Venció a las $clock" else "Vence a las $clock"
    }
}
