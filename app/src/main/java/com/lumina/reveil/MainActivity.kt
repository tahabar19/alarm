package com.lumina.reveil

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lumina.reveil.alarm.AlarmScheduler
import com.lumina.reveil.audio.AudioEngine
import com.lumina.reveil.data.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    @Composable
    private fun App() {
        val context = this
        val store = remember { AlarmStore(context) }
        var alarms by remember { mutableStateOf(store.load()) }
        var editing by remember { mutableStateOf<Alarm?>(null) }
        var isNew by remember { mutableStateOf(false) }

        // Demande d'autorisation notifications (Android 13+)
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            ensureExactAlarmPermission(context)
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFB74D))) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B12)) {
                val current = editing
                if (current == null) {
                    AlarmList(
                        alarms = alarms,
                        onAdd = {
                            editing = Alarm(); isNew = true
                        },
                        onEdit = { editing = it; isNew = false },
                        onToggle = { a, on ->
                            a.enabled = on
                            store.upsert(a)
                            if (on) AlarmScheduler.schedule(context, a)
                            else AlarmScheduler.cancel(context, a.id)
                            alarms = store.load()
                        },
                        onDelete = {
                            AlarmScheduler.cancel(context, it.id)
                            store.delete(it.id)
                            alarms = store.load()
                        }
                    )
                } else {
                    AlarmEditor(
                        alarm = current,
                        onCancel = { editing = null },
                        onSave = { saved ->
                            saved.enabled = true
                            store.upsert(saved)
                            AlarmScheduler.schedule(context, saved)
                            alarms = store.load()
                            editing = null
                        }
                    )
                }
            }
        }
    }

    private fun ensureExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

/* ---------------- Liste ---------------- */

@Composable
private fun AlarmList(
    alarms: List<Alarm>,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF0B0B12),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = Color(0xFFFFB74D)) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = Color.Black)
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(20.dp)
                .fillMaxSize()
        ) {
            Text("Lumina Réveil", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
            Text("Réveil en douceur au lever de soleil", fontSize = 14.sp, color = Color.White.copy(0.6f))
            Spacer(Modifier.height(20.dp))

            if (alarms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun réveil.\nAppuie sur + pour en créer un.",
                        color = Color.White.copy(0.5f), fontSize = 16.sp)
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { a ->
                        AlarmCard(a, onEdit, onToggle, onDelete)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    a: Alarm,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    Surface(
        color = Color(0xFF171722),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(a) }
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(a.timeLabel(), fontSize = 40.sp, fontWeight = FontWeight.Light,
                    color = if (a.enabled) Color.White else Color.White.copy(0.4f))
                Text(
                    "${a.label} · ${a.daysLabel()}",
                    fontSize = 13.sp, color = Color.White.copy(0.6f)
                )
                Text(
                    "☀️ ${a.scene.label} · ${a.preIntervalMinutes} min · ${a.ambiance.label} · ${a.melody.label}",
                    fontSize = 12.sp, color = Color(0xFFFFB74D).copy(0.8f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Switch(checked = a.enabled, onCheckedChange = { onToggle(a, it) })
                IconButton(onClick = { onDelete(a) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Color.White.copy(0.5f))
                }
            }
        }
    }
}

/* ---------------- Éditeur ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    alarm: Alarm,
    onCancel: () -> Unit,
    onSave: (Alarm) -> Unit
) {
    val context = LocalContextSafe()
    var hour by remember { mutableStateOf(alarm.hour) }
    var minute by remember { mutableStateOf(alarm.minute) }
    var interval by remember { mutableStateOf(alarm.preIntervalMinutes.toFloat()) }
    var scene by remember { mutableStateOf(alarm.scene) }
    var ambiance by remember { mutableStateOf(alarm.ambiance) }
    var melody by remember { mutableStateOf(alarm.melody) }
    var vibrate by remember { mutableStateOf(alarm.vibrate) }
    var label by remember { mutableStateOf(alarm.label) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(alarm.days) } }

    val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)

    // moteur d'aperçu audio
    val preview = remember { AudioEngine() }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }
            Text("Réglage du réveil", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))

        Section("Heure de réveil")
        TimePicker(state = timeState)

        Spacer(Modifier.height(16.dp))
        Section("Nom")
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Section("Intervalle avant réveil : ${interval.toInt()} min")
        Text(
            "L'écran s'allume et le son démarrent ${interval.toInt()} min avant l'heure, en montant progressivement.",
            fontSize = 12.sp, color = Color.White.copy(0.55f)
        )
        Slider(value = interval, onValueChange = { interval = it }, valueRange = 0f..60f, steps = 11)

        Spacer(Modifier.height(8.dp))
        Section("Ambiance lumineuse")
        ChipRow(LightScene.values().map { it.label }, scene.ordinal) { scene = LightScene.values()[it] }

        Spacer(Modifier.height(12.dp))
        Section("Son d'ambiance (phase montante)")
        ChipRow(AmbianceSound.values().map { it.label }, ambiance.ordinal) { i ->
            ambiance = AmbianceSound.values()[i]
        }
        PreviewButton("Écouter l'ambiance") {
            preview.stop(); preview.start(ambiance, melody)
            preview.ambianceGain = 0.7f; preview.melodyGain = 0f
        }

        Spacer(Modifier.height(12.dp))
        Section("Mélodie de réveil")
        ChipRow(WakeMelody.values().map { it.label }, melody.ordinal) { melody = WakeMelody.values()[it] }
        PreviewButton("Écouter la mélodie") {
            preview.stop(); preview.start(ambiance, melody)
            preview.ambianceGain = 0f; preview.melodyGain = 0.9f
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { preview.stop() }) { Text("⏹ Arrêter l'aperçu", color = Color.White.copy(0.7f)) }

        Spacer(Modifier.height(12.dp))
        Section("Répétition")
        DaysRow(days)

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            Spacer(Modifier.width(10.dp))
            Text("Vibration à l'heure du réveil", color = Color.White)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                preview.stop()
                onSave(alarm.copy(
                    hour = timeState.hour,
                    minute = timeState.minute,
                    preIntervalMinutes = interval.toInt(),
                    scene = scene,
                    ambiance = ambiance,
                    melody = melody,
                    vibrate = vibrate,
                    label = label.ifBlank { "Réveil" },
                    days = days.toMutableSet()
                ))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D))
        ) {
            Text("Enregistrer", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        color = Color(0xFFFFB74D), modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScrollable()
    ) {
        labels.forEachIndexed { i, l ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(l) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun PreviewButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFFFFB74D))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color(0xFFFFB74D))
    }
}

@Composable
private fun DaysRow(days: MutableList<Int>) {
    val names = listOf("D", "L", "M", "M", "J", "V", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 0..6) {
            val on = days.contains(d)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (on) Color(0xFFFFB74D) else Color(0xFF23232F),
                modifier = Modifier
                    .size(42.dp)
                    .clickable { if (on) days.remove(d) else days.add(d) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(names[d], color = if (on) Color.Black else Color.White.copy(0.7f))
                }
            }
        }
    }
}

/* helpers */

@Composable
private fun LocalContextSafe() = androidx.compose.ui.platform.LocalContext.current

// petit modificateur de scroll horizontal
@Composable
private fun Modifier.horizontalScrollable(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
