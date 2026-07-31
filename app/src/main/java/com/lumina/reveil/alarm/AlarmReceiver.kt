package com.lumina.reveil.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lumina.reveil.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("alarmId", -1L)
        if (id == -1L) return

        val alarm = AlarmStore(context).get(id) ?: return
        if (!alarm.enabled) return

        // Démarre le service qui gère la montée lumière + son
        val svc = Intent(context, SunriseService::class.java).apply {
            putExtra("alarmId", id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }

        // Reprogramme le prochain déclenchement si c'est un réveil répétitif
        if (alarm.isRepeating) {
            AlarmScheduler.schedule(context, alarm)
        } else {
            alarm.enabled = false
            AlarmStore(context).upsert(alarm)
        }
    }
}
