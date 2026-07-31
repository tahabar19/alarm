package com.lumina.reveil.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.lumina.reveil.AlarmActivity
import com.lumina.reveil.R
import com.lumina.reveil.audio.AudioEngine
import com.lumina.reveil.data.Alarm
import com.lumina.reveil.data.AlarmStore

/**
 * Cœur de l'expérience.
 * Ligne de temps :
 *  t = 0 .............................. début de la montée (heureRéveil - intervalle)
 *  0 -> intervalle : lumière + ambiance montent de 0 à leur maximum
 *  t = intervalle (heure de réveil) : la mélodie démarre doucement puis monte
 *  jusqu'à ce que l'utilisateur arrête (ou coupure de sécurité).
 */
class SunriseService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val engine = AudioEngine()
    private var vibrator: Vibrator? = null

    private var startTime = 0L
    private var preMillis = 0L
    private var alarmActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra("alarmId", -1L) ?: -1L
        val alarm = AlarmStore(this).get(id)
        if (alarm == null) { stopSelf(); return START_NOT_STICKY }

        currentAlarm = alarm
        active = true
        lightProgress = 0f
        phase = Phase.SUNRISE

        startForeground(NOTIF_ID, buildNotification(alarm))
        acquireWakeLock()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        engine.start(alarm.ambiance, alarm.melody)

        preMillis = alarm.preIntervalMinutes.toLong() * 60_000L
        startTime = System.currentTimeMillis()

        launchAlarmActivity()
        handler.post(tick)
        return START_STICKY
    }

    private val tick = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - startTime

            if (elapsed < preMillis) {
                // Phase lever de soleil : progression 0 -> 1
                val p = (elapsed.toFloat() / preMillis).coerceIn(0f, 1f)
                lightProgress = p
                phase = Phase.SUNRISE
                // ambiance : monte en douceur (courbe quadratique) jusqu'à ~0.7
                engine.ambianceGain = p * p * 0.7f
                engine.melodyGain = 0f
            } else {
                // Phase alarme : lumière au max, la mélodie monte
                lightProgress = 1f
                if (!alarmActive) {
                    alarmActive = true
                    phase = Phase.ALARM
                    startVibration(currentAlarm?.vibrate == true)
                }
                val alarmElapsed = elapsed - preMillis
                val mp = (alarmElapsed.toFloat() / MELODY_RAMP_MS).coerceIn(0f, 1f)
                engine.melodyGain = mp
                engine.ambianceGain = 0.35f * (1f - mp) + 0.1f // s'estompe derrière la mélodie

                // Sécurité : coupure automatique après SAFETY_MS
                if (alarmElapsed > SAFETY_MS) {
                    stopEverything()
                    return
                }
            }
            handler.postDelayed(this, 33) // ~30 fps
        }
    }

    private fun startVibration(enabled: Boolean) {
        if (!enabled) return
        val v = vibrator ?: return
        val pattern = longArrayOf(0, 400, 1200) // vibration douce espacée
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        }
    }

    fun snooze(minutes: Int) {
        val alarm = currentAlarm ?: return
        stopEverything(reschedule = false)
        // reprogramme un réveil ponctuel dans `minutes`
        val snoozeAlarm = alarm.copy(
            id = alarm.id + 1, // id temporaire distinct
            preIntervalMinutes = 0,
            days = mutableSetOf()
        )
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.MINUTE, minutes)
        snoozeAlarm.hour = c.get(java.util.Calendar.HOUR_OF_DAY)
        snoozeAlarm.minute = c.get(java.util.Calendar.MINUTE)
        AlarmStore(this).upsert(snoozeAlarm)
        AlarmScheduler.schedule(this, snoozeAlarm)
    }

    fun stopEverything(reschedule: Boolean = false) {
        handler.removeCallbacks(tick)
        runCatching { vibrator?.cancel() }
        engine.stop()
        active = false
        currentAlarm = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun launchAlarmActivity() {
        val i = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(i)
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL, "Réveil en cours",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) }
            nm.createNotificationChannel(ch)
        }
        val fullScreen = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else
            @Suppress("DEPRECATION") Notification.Builder(this)

        return builder
            .setContentTitle("Lever de soleil — ${alarm.label}")
            .setContentText("Réveil à ${alarm.timeLabel()}")
            .setSmallIcon(R.drawable.ic_sun)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "lumina:sunrise"
        ).apply { acquire(30 * 60_000L) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
        engine.stop()
        releaseWakeLock()
        active = false
        if (instance === this) instance = null
    }

    enum class Phase { SUNRISE, ALARM }

    companion object {
        private const val CHANNEL = "lumina_sunrise"
        private const val NOTIF_ID = 42
        private const val MELODY_RAMP_MS = 90_000f      // 90 s de montée mélodie
        private const val SAFETY_MS = 15 * 60_000L       // coupure auto après 15 min

        // État partagé lu par AlarmActivity (même processus)
        @Volatile var active = false
        @Volatile var currentAlarm: Alarm? = null
        @Volatile var lightProgress = 0f
        @Volatile var phase = Phase.SUNRISE

        @Volatile var instance: SunriseService? = null
    }

    init { instance = this }
}
