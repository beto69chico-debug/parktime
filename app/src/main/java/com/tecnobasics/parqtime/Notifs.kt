package com.tecnobasics.parqtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tecnobasics.parqtime.core.Fmt
import com.tecnobasics.parqtime.core.toEpochMs
import com.tecnobasics.parqtime.data.ParqStore
import java.time.LocalDate

object Notifs {
    const val CH_ALARM = "parq_alarm_v1"     // alarma: el sonido lo pone el servicio
    const val CH_LOUD = "parq_loud_v1"       // respaldo si Android no deja arrancar el servicio
    const val CH_ACTIVE = "parq_active_v1"   // conteo en curso, silencioso
    const val ID_ALARM = 1001
    const val ID_ACTIVE = 2001
    const val ID_FALLBACK = 3001

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, "Alarmas de tiempo", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos de 25, 10 y 5 minutos, y fin del tiempo"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val loud = NotificationChannel(CH_LOUD, "Alarmas (respaldo)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Se usa solo si el teléfono bloquea la sirena"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val active = NotificationChannel(CH_ACTIVE, "Tiempo activo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Cuenta regresiva en curso"
            setShowBadge(false)
        }
        nm.createNotificationChannels(listOf(alarm, loud, active))
    }

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Notificación fija con el conteo; se quita si no hay conteo activo. */
    @SuppressLint("MissingPermission")
    fun updateActive(ctx: Context) {
        ensureChannels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val store = ParqStore(ctx)
        val s = store.session
        if (s == null) {
            nm.cancel(ID_ACTIVE)
            return
        }
        if (!canPost(ctx)) return
        val st = SessionController.status(store, s)
        val h24 = DateFormat.is24HourFormat(ctx)
        val today = LocalDate.now()
        val b = NotificationCompat.Builder(ctx, CH_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_parq)
            .setColor(0xFFA1BC0E.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(ctx))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        when {
            st.remainingSec <= 0 -> b
                .setContentTitle("Tiempo vencido")
                .setContentText("Venció a las ${Fmt.clock(st.expiry, h24)}. Recarga o mueve el coche.")
            st.paused && st.resumeAt != null -> b
                .setContentTitle("En pausa: el parquímetro no cobra ahora")
                .setContentText("Se reanuda ${Fmt.dayLabel(st.resumeAt, today)} a las ${Fmt.clock(st.resumeAt, h24)} · vence ${Fmt.dayLabel(st.expiry, today)} a las ${Fmt.clock(st.expiry, h24)}")
            else -> b
                .setContentTitle("Vence a las ${Fmt.clock(st.expiry, h24)}")
                .setContentText("Te aviso a los 25, 10 y 5 minutos")
                .setWhen(st.expiry.toEpochMs())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        try {
            nm.notify(ID_ACTIVE, b.build())
        } catch (_: SecurityException) {
        }
    }
}
