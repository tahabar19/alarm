package com.lumina.reveil.data

import android.content.Context
import org.json.JSONArray

/**
 * Stockage simple des réveils dans SharedPreferences (JSON).
 */
class AlarmStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("lumina_alarms", Context.MODE_PRIVATE)

    fun load(): MutableList<Alarm> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<Alarm>()
        for (i in 0 until arr.length()) {
            runCatching { list.add(Alarm.fromJson(arr.getJSONObject(i))) }
        }
        return list
    }

    fun save(alarms: List<Alarm>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun get(id: Long): Alarm? = load().firstOrNull { it.id == id }

    fun upsert(alarm: Alarm) {
        val list = load()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        save(list)
    }

    fun delete(id: Long) = save(load().filterNot { it.id == id })

    companion object {
        private const val KEY = "alarms_json"
    }
}
