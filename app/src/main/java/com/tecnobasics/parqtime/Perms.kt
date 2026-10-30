package com.tecnobasics.parqtime

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.tecnobasics.parqtime.alarm.AlarmScheduler

enum class Perm(val title: String, val desc: String) {
    NOTIFICATIONS("Notificaciones", "Sin esto no hay avisos."),
    FULL_SCREEN("Alarma a pantalla completa", "Para que la alarma aparezca aunque el teléfono esté bloqueado."),
    EXACT_ALARMS("Alarmas exactas", "Para que el aviso suene al minuto exacto."),
    BATTERY("Sin restricción de batería", "Evita que el teléfono cierre ParqTime. En Xiaomi, Huawei u Oppo activa también \"Inicio automático\"."),
}

object Perms {

    fun isGranted(ctx: Context, p: Perm): Boolean = when (p) {
        Perm.NOTIFICATIONS -> Notifs.canPost(ctx)
        Perm.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Perm.EXACT_ALARMS -> AlarmScheduler.canExact(ctx)
        Perm.BATTERY -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun missing(ctx: Context): List<Perm> = Perm.entries.filterNot { isGranted(ctx, it) }

    /** Pantalla de Ajustes de Android donde se activa cada permiso. */
    @SuppressLint("BatteryLife", "InlinedApi")
    fun settingsIntent(ctx: Context, p: Perm): Intent {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return when (p) {
            Perm.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            Perm.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            Perm.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            Perm.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun open(ctx: Context, p: Perm) {
        try {
            ctx.startActivity(settingsIntent(ctx, p))
        } catch (_: Exception) {
            // Algunas marcas no tienen esa pantalla: se abre la ficha de la app
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun hasTorch(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(CameraManager::class.java)
        cm.cameraIdList.any { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (_: Exception) {
        false
    }
}
