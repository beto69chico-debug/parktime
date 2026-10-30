package com.tecnobasics.parqtime.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tecnobasics.parqtime.Notifs
import com.tecnobasics.parqtime.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La alarma "fin del mundo": sirena al volumen máximo del canal de alarma (suena aunque el
 * teléfono esté en silencio), vibración continua y linterna en estrobo, hasta que se apague.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "com.tecnobasics.parqtime.action.STOP"
        private const val AUTO_STOP_MS = 10 * 60_000L // seguro: no dejar la linterna prendida para siempre

        private val _ringing = MutableStateFlow<Int?>(null)
        /** Aviso que está sonando (25, 10, 5, 0 o prueba), o null si no suena nada. */
        val ringing: StateFlow<Int?> = _ringing.asStateFlow()

        fun start(ctx: Context, stage: Int) {
            val i = Intent(ctx, AlarmService::class.java).putExtra(AlarmScheduler.EXTRA_STAGE, stage)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (_: Exception) {
                fallback(ctx, stage)
            }
        }

        fun stop(ctx: Context) {
            if (_ringing.value == null) return
            try {
                ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))
            } catch (_: Exception) {
            }
        }

        /** Si Android no deja arrancar la sirena, al menos una notificación que repite el tono de alarma. */
        @SuppressLint("MissingPermission")
        fun fallback(ctx: Context, stage: Int) {
            Notifs.ensureChannels(ctx)
            if (!Notifs.canPost(ctx)) return
            val n = NotificationCompat.Builder(ctx, Notifs.CH_LOUD)
                .setSmallIcon(R.drawable.ic_stat_parq)
                .setContentTitle(AlarmTexts.title(stage))
                .setContentText(AlarmTexts.expiryLine(ctx) ?: AlarmTexts.hint(stage))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(Notifs.openAppIntent(ctx))
                .setAutoCancel(true)
                .build()
            n.flags = n.flags or Notification.FLAG_INSISTENT // repite el sonido hasta que la toques
            try {
                NotificationManagerCompat.from(ctx).notify(Notifs.ID_FALLBACK, n)
            } catch (_: SecurityException) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedVolume = -1
    private var torchId: String? = null
    private var torchOn = false

    private val strobe = object : Runnable {
        override fun run() {
            setTorch(!torchOn)
            handler.postDelayed(this, if (torchOn) 90L else 160L)
        }
    }
    private val autoStop = Runnable { stopAlarm() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val stage = intent?.getIntExtra(AlarmScheduler.EXTRA_STAGE, 0) ?: 0
        Notifs.ensureChannels(this)
        _ringing.value = stage // antes de la notificación: la pantalla de alarma lo lee al abrir
        val n = buildNotification(stage)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifs.ID_ALARM, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(Notifs.ID_ALARM, n)
            }
        } catch (_: Exception) {
            _ringing.value = null
            fallback(this, stage)
            stopSelf()
            return START_NOT_STICKY
        }
        startRinging()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
        // Con el teléfono desbloqueado, intenta abrir la pantalla roja directamente
        try {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_STAGE, stage)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(stage: Int): Notification {
        val full = PendingIntent.getActivity(
            this, 2,
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_STAGE, stage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this, 3,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val expiry = AlarmTexts.expiryLine(this)
        return NotificationCompat.Builder(this, Notifs.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_parq)
            .setContentTitle(AlarmTexts.title(stage))
            .setContentText(expiry ?: AlarmTexts.hint(stage))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(expiry, AlarmTexts.hint(stage)).joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setColor(0xFFD3140F.toInt())
            .setColorized(true)
            .addAction(0, "APAGAR", stopPi)
            .build()
    }

    private fun startRinging() {
        stopRingingOnly()

        // Volumen de alarma al máximo (se restaura al apagar)
        val am = getSystemService(AudioManager::class.java)
        try {
            if (savedVolume < 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: Exception) {
        }

        // Sirena en bucle por el canal de alarma
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            resources.openRawResourceFd(R.raw.siren).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {
            player = null
        }

        // Vibración continua
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            }
            vibrate(v, VibrationEffect.createWaveform(longArrayOf(0, 800, 200, 800, 200), 0))
            vibrator = v
        } catch (_: Exception) {
        }

        // Mantener la CPU despierta mientras suena
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ParqTime:alarm").apply {
                acquire(AUTO_STOP_MS + 5_000L)
            }
        } catch (_: Exception) {
        }

        // Linterna en estrobo
        torchId = findTorch()
        if (torchId != null) handler.post(strobe)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun findTorch(): String? = try {
        val cm = getSystemService(CameraManager::class.java)
        val withFlash = cm.cameraIdList.filter {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        torchOn = on
        try {
            getSystemService(CameraManager::class.java).setTorchMode(id, on)
        } catch (_: Exception) {
            // La cámara puede estar en uso por otra app: se ignora y se sigue con sonido y vibración
        }
    }

    private fun stopRingingOnly() {
        handler.removeCallbacks(strobe)
        if (torchOn) setTorch(false)
        torchOn = false
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun restoreVolume() {
        if (savedVolume < 0) return
        try {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
        } catch (_: Exception) {
        }
        savedVolume = -1
    }

    private fun stopAlarm() {
        handler.removeCallbacks(autoStop)
        stopRingingOnly()
        restoreVolume()
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Notifs.updateActive(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopRingingOnly()
        restoreVolume()
        _ringing.value = null
        super.onDestroy()
    }
}
