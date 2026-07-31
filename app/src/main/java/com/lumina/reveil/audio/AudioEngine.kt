package com.lumina.reveil.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.lumina.reveil.data.AmbianceSound
import com.lumina.reveil.data.WakeMelody
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Moteur audio 100% synthétisé (aucun fichier .mp3 nécessaire).
 * Un seul thread génère et mixe deux couches :
 *   - couche AMBIANCE (pluie, vagues, ruisseau, oiseaux, vent)  -> gain [ambianceGain]
 *   - couche MÉLODIE  (harpe, kalimba, marimba, bol, piano...)   -> gain [melodyGain]
 * Le service fait monter [ambianceGain] pendant la phase lever de soleil,
 * puis fait monter [melodyGain] pendant la phase alarme.
 */
class AudioEngine {

    @Volatile var ambianceGain = 0f      // 0f..1f
    @Volatile var melodyGain = 0f        // 0f..1f
    @Volatile private var running = false

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    private var ambiance: AmbianceSound = AmbianceSound.PLUIE
    private var melody: WakeMelody = WakeMelody.HARPE

    fun start(ambiance: AmbianceSound, melody: WakeMelody) {
        if (running) return
        this.ambiance = ambiance
        this.melody = melody
        running = true

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf, SAMPLE_RATE) // ~1s buffer

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track?.play()

        thread = Thread { generateLoop() }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    // ---- Génération ----

    private val block = ShortArray(BLOCK)
    private var frame = 0L

    // état filtres bruit
    private var lp = 0f
    private var brown = 0f
    // état mélodie
    private val scheduler = MelodyScheduler(melody)
    private val birds = BirdChorus()

    private fun generateLoop() {
        scheduler.reset(melody)
        birds.reset()
        while (running) {
            for (i in 0 until BLOCK) {
                val t = frame.toDouble() / SAMPLE_RATE
                var s = 0f

                // --- Ambiance ---
                if (ambianceGain > 0.0001f) {
                    s += ambianceSample(t) * ambianceGain
                }

                // --- Mélodie ---
                if (melodyGain > 0.0001f) {
                    val m = if (melody == WakeMelody.OISEAUX)
                        birds.sample(t, melodyGain)
                    else
                        scheduler.sample(frame)
                    s += m * melodyGain
                }

                // soft clip
                s = softClip(s)
                block[i] = (s * 30000f).toInt().coerceIn(-32768, 32767).toShort()
                frame++
            }
            track?.write(block, 0, BLOCK)
        }
    }

    private fun ambianceSample(t: Double): Float {
        return when (ambiance) {
            AmbianceSound.PLUIE -> rain()
            AmbianceSound.OCEAN -> ocean(t)
            AmbianceSound.RUISSEAU -> stream()
            AmbianceSound.FORET -> forest(t)
            AmbianceSound.VENT -> wind()
            AmbianceSound.AUCUN -> 0f
        }
    }

    // Pluie : bruit blanc filtré passe-bas + fines gouttes
    private fun rain(): Float {
        val white = Random.nextFloat() * 2f - 1f
        lp += 0.05f * (white - lp)               // couche sourde continue
        var s = lp * 0.7f
        if (Random.nextFloat() < 0.004f) {       // gouttes ponctuelles
            s += (Random.nextFloat() * 2f - 1f) * 0.5f
        }
        return s * 0.9f
    }

    // Océan : bruit brun modulé par une lente enveloppe (ressac)
    private fun ocean(t: Double): Float {
        val white = Random.nextFloat() * 2f - 1f
        brown = (brown + 0.02f * white).coerceIn(-1f, 1f)
        val swell = (0.5f + 0.5f * sin(2.0 * PI * 0.09 * t).toFloat()) // ~11s par vague
        return brown * swell * 0.9f
    }

    // Ruisseau : bruit aigu filtré + bulles rapides
    private fun stream(): Float {
        val white = Random.nextFloat() * 2f - 1f
        lp += 0.35f * (white - lp)
        var s = (white - lp) * 0.6f             // composante aiguë (passe-haut approx)
        if (Random.nextFloat() < 0.02f) s += (Random.nextFloat() * 2f - 1f) * 0.25f
        return s
    }

    // Forêt : léger vent + chants d'oiseaux occasionnels
    private fun forest(t: Double): Float {
        var s = wind() * 0.4f
        s += birdChirp(t)
        return s
    }

    private var chirpEnd = -1.0
    private var chirpFreq = 0.0
    private var chirpStart = 0.0
    private fun birdChirp(t: Double): Float {
        if (t > chirpEnd) {
            if (Random.nextFloat() < 0.0009f) {
                chirpStart = t
                chirpEnd = t + 0.08 + Random.nextDouble(0.12)
                chirpFreq = 2000.0 + Random.nextDouble(2500.0)
            } else return 0f
        }
        val local = t - chirpStart
        val env = exp(-8.0 * local).toFloat()
        val vib = sin(2.0 * PI * (chirpFreq + 400 * sin(60.0 * local)) * local)
        return (vib * env * 0.35f).toFloat()
    }

    // Vent : bruit très filtré, lentement modulé
    private fun wind(): Float {
        val white = Random.nextFloat() * 2f - 1f
        lp += 0.008f * (white - lp)
        return lp * 1.4f
    }

    private fun softClip(x: Float): Float {
        val k = 1.5f
        return (x / (1f + kotlin.math.abs(x * k))).coerceIn(-1f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val BLOCK = 1024
    }
}

/**
 * Séquenceur de notes : joue une gamme pentatonique en boucle, avec une
 * enveloppe douce (attaque lente, longue traîne) pour un réveil apaisant.
 */
private class MelodyScheduler(melody: WakeMelody) {

    private data class Voice(var startFrame: Long, var freq: Double, var dur: Double, var soft: Boolean)

    private val voices = ArrayList<Voice>()
    private var nextNoteFrame = 0L
    private var index = 0
    private var current = melody

    // gammes / motifs
    private val pentatonic = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33)
    private val ascending = doubleArrayOf(261.63, 329.63, 392.00, 523.25, 659.25) // arpège majeur
    private val bowl = doubleArrayOf(196.00, 261.63, 329.63)

    fun reset(melody: WakeMelody) {
        current = melody
        voices.clear()
        nextNoteFrame = 0
        index = 0
    }

    fun sample(frame: Long): Float {
        if (frame >= nextNoteFrame) scheduleNext(frame)
        var s = 0f
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            val local = (frame - v.startFrame).toDouble() / AudioEngine.SAMPLE_RATE
            if (local > v.dur) { it.remove(); continue }
            s += renderVoice(v, local)
        }
        return s * 0.5f
    }

    private fun renderVoice(v: Voice, local: Double): Float {
        // enveloppe : attaque douce + décroissance exponentielle
        val attack = if (v.soft) 0.25 else 0.02
        val env = when {
            local < attack -> (local / attack)
            else -> exp(-(local - attack) * (if (v.soft) 1.2 else 2.5))
        }.toFloat()

        val w = 2.0 * PI * v.freq
        // timbre : fondamentale + quelques harmoniques atténuées (son de cloche/harpe)
        var y = sin(w * local)
        y += 0.5 * sin(2 * w * local)
        y += 0.25 * sin(3 * w * local)
        y += 0.12 * sin(4 * w * local)
        return (y * env * 0.25).toFloat()
    }

    private fun scheduleNext(frame: Long) {
        val sr = AudioEngine.SAMPLE_RATE
        val (freq, gap, dur, soft) = pickNote()
        voices.add(Voice(frame, freq, dur, soft))
        nextNoteFrame = frame + (gap * sr).toLong()
    }

    private data class NoteSpec(val freq: Double, val gap: Double, val dur: Double, val soft: Boolean)

    private fun pickNote(): NoteSpec {
        return when (current) {
            WakeMelody.HARPE -> {
                val f = ascending[index % ascending.size]; index++
                NoteSpec(f, 0.28, 2.5, true)
            }
            WakeMelody.KALIMBA -> {
                val f = pentatonic[(index * 2) % pentatonic.size]; index++
                NoteSpec(f, 0.5, 1.8, true)
            }
            WakeMelody.MARIMBA -> {
                val f = pentatonic[(3 + index * 3) % pentatonic.size]; index++
                NoteSpec(f, 0.35, 1.2, false)
            }
            WakeMelody.BOL_TIBETAIN -> {
                val f = bowl[index % bowl.size]; index++
                NoteSpec(f, 4.0, 6.0, true)
            }
            WakeMelody.PIANO -> {
                val f = pentatonic[(index * 4) % pentatonic.size]; index++
                NoteSpec(f, 0.6, 2.2, true)
            }
            WakeMelody.CARILLON -> {
                val f = pentatonic[(index + kotlin.random.Random.nextInt(pentatonic.size)) % pentatonic.size]; index++
                NoteSpec(f, 0.7, 3.0, true)
            }
        }
    }
}

/**
 * Chœur d'oiseaux à l'aube — synthétisé.
 * Plusieurs "espèces" avec des motifs de chant différents se déclenchent
 * aléatoirement et se superposent. La densité augmente avec le volume (gain),
 * pour donner l'impression d'un lever du jour qui s'anime peu à peu.
 */
private class BirdChorus {

    private class Song(
        val type: Int,
        val start: Double,
        val dur: Double,
        val baseFreq: Double,
        val param: Double,
        val pan: Double
    )

    private val songs = ArrayList<Song>()
    private var lastSpawn = -1.0

    fun reset() {
        songs.clear()
        lastSpawn = -1.0
    }

    fun sample(t: Double, gain: Float): Float {
        // Densité : de ~1 chant/s (faible volume) à ~7 chants/s (plein volume)
        val rate = 1.0 + gain * 6.0
        // probabilité de spawn par échantillon
        if (kotlin.random.Random.nextDouble() < rate / AudioEngine.SAMPLE_RATE) {
            spawn(t)
        }

        var s = 0f
        val it = songs.iterator()
        while (it.hasNext()) {
            val song = it.next()
            val local = t - song.start
            if (local > song.dur) { it.remove(); continue }
            s += render(song, local)
        }
        return s * 0.6f
    }

    private fun spawn(t: Double) {
        val type = kotlin.random.Random.nextInt(5)
        val (base, dur, param) = when (type) {
            0 -> Triple(2200.0 + kotlin.random.Random.nextDouble(1500.0), 0.12 + kotlin.random.Random.nextDouble(0.10), 0.0)      // chirp bref aigu
            1 -> Triple(1800.0 + kotlin.random.Random.nextDouble(900.0), 0.35 + kotlin.random.Random.nextDouble(0.30), 30.0)       // trille rapide
            2 -> Triple(1500.0 + kotlin.random.Random.nextDouble(700.0), 0.30 + kotlin.random.Random.nextDouble(0.20), 1.0)        // sifflet à deux notes
            3 -> Triple(2600.0 + kotlin.random.Random.nextDouble(1200.0), 0.5 + kotlin.random.Random.nextDouble(0.4), 8.0)         // gazouillis modulé (FM)
            else -> Triple(900.0 + kotlin.random.Random.nextDouble(400.0), 0.4 + kotlin.random.Random.nextDouble(0.3), 4.0)        // roucoulement grave
        }
        songs.add(Song(type, t, dur, base, param, kotlin.random.Random.nextDouble(-1.0, 1.0)))
    }

    private fun render(song: Song, local: Double): Float {
        val env = envelope(local, song.dur)
        val f = when (song.type) {
            0 -> song.baseFreq                                                     // ton pur bref
            1 -> song.baseFreq + 300.0 * sin(2.0 * PI * song.param * local)        // trille (AM/FM rapide)
            2 -> song.baseFreq + (if (local < song.dur / 2) 0.0 else 450.0)        // saut de note
            3 -> song.baseFreq + song.baseFreq * 0.15 * sin(2.0 * PI * song.param * local) // gazouillis FM
            else -> song.baseFreq * (1.0 + 0.05 * sin(2.0 * PI * 6.0 * local))     // roucoulement doux
        }
        var y = sin(2.0 * PI * f * local)
        // léger harmonique pour la couleur
        y += 0.25 * sin(2.0 * PI * 2 * f * local)
        val amp = if (song.type == 1) (0.6 + 0.4 * sin(2.0 * PI * 22.0 * local)) else 1.0 // trille pulse
        return (y * env * amp * 0.28).toFloat()
    }

    private fun envelope(local: Double, dur: Double): Float {
        val attack = 0.015
        val release = 0.06
        return when {
            local < attack -> (local / attack)
            local > dur - release -> ((dur - local) / release).coerceAtLeast(0.0)
            else -> 1.0
        }.toFloat()
    }
}
