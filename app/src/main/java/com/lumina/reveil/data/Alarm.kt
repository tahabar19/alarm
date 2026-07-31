package com.lumina.reveil.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un réveil.
 * @param hour / minute        heure de réveil finale (quand l'alarme sonne à plein volume)
 * @param preIntervalMinutes   durée de la montée avant l'alarme (ex: 30 min de lever de soleil)
 * @param days                 jours de répétition (0=Dimanche ... 6=Samedi). Vide = une seule fois.
 */
data class Alarm(
    val id: Long = System.currentTimeMillis(),
    var hour: Int = 7,
    var minute: Int = 0,
    var enabled: Boolean = true,
    var label: String = "Réveil",
    var preIntervalMinutes: Int = 30,
    var scene: LightScene = LightScene.SOLEIL,
    var ambiance: AmbianceSound = AmbianceSound.PLUIE,
    var melody: WakeMelody = WakeMelody.OISEAUX,
    var vibrate: Boolean = true,
    var days: MutableSet<Int> = mutableSetOf()
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    fun timeLabel(): String = "%02d:%02d".format(hour, minute)

    fun daysLabel(): String {
        if (days.isEmpty()) return "Une fois"
        if (days.size == 7) return "Tous les jours"
        val names = listOf("Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam")
        return days.sorted().joinToString(" ") { names[it] }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("label", label)
        put("preIntervalMinutes", preIntervalMinutes)
        put("scene", scene.name)
        put("ambiance", ambiance.name)
        put("melody", melody.name)
        put("vibrate", vibrate)
        put("days", JSONArray(days.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            val daySet = mutableSetOf<Int>()
            for (i in 0 until daysArr.length()) daySet.add(daysArr.getInt(i))
            return Alarm(
                id = o.getLong("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.optBoolean("enabled", true),
                label = o.optString("label", "Réveil"),
                preIntervalMinutes = o.optInt("preIntervalMinutes", 30),
                scene = runCatching { LightScene.valueOf(o.optString("scene")) }.getOrDefault(LightScene.SOLEIL),
                ambiance = runCatching { AmbianceSound.valueOf(o.optString("ambiance")) }.getOrDefault(AmbianceSound.PLUIE),
                melody = runCatching { WakeMelody.valueOf(o.optString("melody")) }.getOrDefault(WakeMelody.OISEAUX),
                vibrate = o.optBoolean("vibrate", true),
                days = daySet
            )
        }
    }
}
