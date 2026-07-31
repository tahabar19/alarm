package com.lumina.reveil.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lumina.reveil.data.Alarm
import com.lumina.reveil.data.AlarmStore
import java.util.Calendar

/**
 * Planifie le déclenchement au moment (heure de réveil - intervalle),
 * c'est-à-dire le début de la montée "lever de soleil".
 */
object AlarmScheduler {

    fun rescheduleAll(context: Context) {
        val store = AlarmStore(context)
        store.load().forEach { alarm ->
            if (alarm.enabled) schedule(context, alarm) else cancel(context, alarm.id)
        }
    }

    fun schedule(context: Context, alarm: Alarm) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(alarm)

        val pi = pendingIntent(context, alarm.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Repli : alarme approximative si l'autorisation exacte manque
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }

        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, alarm.id))
        am.setAlarmClock(info, pi)
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, id))
    }

    /**
     * Calcule le prochain instant de DÉBUT de montée = heureRéveil - intervalle.
     * Gère la répétition sur les jours choisis.
     */
    fun nextTriggerMillis(alarm: Alarm): Long {
        val now = Calendar.getInstance()

        fun candidateFor(dayOffset: Int): Calendar {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, dayOffset)
            c.set(Calendar.HOUR_OF_DAY, alarm.hour)
            c.set(Calendar.MINUTE, alarm.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.MINUTE, -alarm.preIntervalMinutes) // recule du temps d'intervalle
            return c
        }

        if (!alarm.isRepeating) {
            var c = candidateFor(0)
            if (c.timeInMillis <= now.timeInMillis) c = candidateFor(1)
            return c.timeInMillis
        }

        // Répétition : cherche le prochain jour actif (0..7 offsets)
        for (offset in 0..7) {
            val c = candidateFor(offset)
            val dow = c.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 -> 0
            if (alarm.days.contains(dow) && c.timeInMillis > now.timeInMillis) {
                return c.timeInMillis
            }
        }
        return candidateFor(1).timeInMillis
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.lumina.reveil.TRIGGER"
            putExtra("alarmId", id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, com.lumina.reveil.MainActivity::class.java)
        return PendingIntent.getActivity(
            context, id.toInt() + 90000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
