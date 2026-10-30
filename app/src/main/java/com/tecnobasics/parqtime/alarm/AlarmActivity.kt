package com.tecnobasics.parqtime.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tecnobasics.parqtime.MainActivity
import com.tecnobasics.parqtime.R
import com.tecnobasics.parqtime.ui.AlarmRed
import com.tecnobasics.parqtime.ui.ParqTheme

/** Pantalla roja que aparece encima de la pantalla bloqueada mientras suena la alarma. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val stage = intent.getIntExtra(AlarmScheduler.EXTRA_STAGE, AlarmService.ringing.value ?: 0)
        val expiry = AlarmTexts.expiryLine(this)
        setContent {
            ParqTheme {
                AlarmScreen(
                    initialStage = stage,
                    expiryText = expiry,
                    onStop = {
                        AlarmService.stop(this)
                        finish()
                    },
                    onAdd = {
                        AlarmService.stop(this)
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_ADD, true)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                ),
                        )
                        finish()
                    },
                    onGone = { finish() },
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}

@Composable
private fun AlarmScreen(
    initialStage: Int,
    expiryText: String?,
    onStop: () -> Unit,
    onAdd: () -> Unit,
    onGone: () -> Unit,
) {
    val ringing by AlarmService.ringing.collectAsState()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(ringing) {
        if (ringing != null) seen = true else if (seen) onGone()
    }
    val stage = ringing ?: initialStage

    BackHandler { /* solo se apaga con el botón */ }

    val flash = rememberInfiniteTransition(label = "flash")
    val k by flash.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "k",
    )
    val bg = lerp(AlarmRed, Color.Black, k)
    val isNumber = stage == 25 || stage == 10 || stage == 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_parqtime),
            contentDescription = "ParqTime",
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = AlarmTexts.big(stage),
            color = Color.White,
            fontSize = if (isNumber) 180.sp else 96.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            text = AlarmTexts.unit(stage),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        if (expiryText != null) {
            Text(text = expiryText, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = AlarmTexts.hint(stage),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text(text = "APAGAR", fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        if (stage != AlarmTexts.STAGE_TEST) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(text = "Ya pagué más: agregar tiempo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
