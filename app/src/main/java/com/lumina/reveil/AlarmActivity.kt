package com.lumina.reveil

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumina.reveil.alarm.SunriseService
import com.lumina.reveil.data.LightScene
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Afficher par-dessus l'écran verrouillé et allumer l'écran
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { SunriseScreen(::updateBrightness, ::dismissAlarm, ::onSnooze) }
    }

    private fun updateBrightness(value: Float) {
        val lp = window.attributes
        // 0.02 minimum pour rester légèrement visible dans le noir, jusqu'à 1.0
        lp.screenBrightness = (0.02f + value * 0.98f).coerceIn(0.02f, 1f)
        window.attributes = lp
    }

    private fun dismissAlarm() {
        SunriseService.instance?.stopEverything()
        finish()
    }

    private fun onSnooze() {
        SunriseService.instance?.snooze(9)
        finish()
    }
}

@Composable
private fun SunriseScreen(
    onBrightness: (Float) -> Unit,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var isAlarm by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(currentTime()) }

    // Boucle de rafraîchissement synchronisée à l'écran
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                progress = SunriseService.lightProgress
                isAlarm = SunriseService.phase == SunriseService.Phase.ALARM
                onBrightness(progress)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            kotlinx.coroutines.delay(1000)
        }
    }

    val scene = SunriseService.currentAlarm?.scene ?: LightScene.SOLEIL
    val start = Color(scene.startColor)
    val end = Color(scene.endColor)
    // Interpolation de la couleur en fonction de la progression
    val glow = lerp(start, end, progress)
    val bg = lerp(Color.Black, start, (progress * 0.6f).coerceIn(0f, 1f))

    val config = LocalConfiguration.current
    val cx = config.screenWidthDp * 0.5f
    val sunY = config.screenHeightDp * (0.85f - progress * 0.45f) // le "soleil" monte

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(glow, lerp(glow, bg, 0.7f), bg),
                    center = Offset(cx * 3f, sunY * 3f),
                    radius = 900f + progress * 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = clock,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = textColor(progress)
            )
            Text(
                text = SunriseService.currentAlarm?.label ?: "Réveil",
                fontSize = 20.sp,
                color = textColor(progress).copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isAlarm) "Bonjour ☀️" else "Lever de soleil en cours…",
                fontSize = 16.sp,
                color = textColor(progress).copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(60.dp))

            Button(
                onClick = onStop,
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.22f)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(64.dp)
            ) {
                Text("Arrêter", fontSize = 20.sp, color = textColor(progress))
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onSnooze,
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.10f)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(52.dp)
            ) {
                Text("Répéter (9 min)", fontSize = 16.sp, color = textColor(progress).copy(alpha = 0.85f))
            }
        }
    }
}

private fun textColor(progress: Float): Color =
    lerp(Color.White.copy(alpha = 0.85f), Color(0xFF2A1500), progress)

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
