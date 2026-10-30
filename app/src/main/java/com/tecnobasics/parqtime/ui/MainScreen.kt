package com.tecnobasics.parqtime.ui

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tecnobasics.parqtime.Notifs
import com.tecnobasics.parqtime.Perm
import com.tecnobasics.parqtime.Perms
import com.tecnobasics.parqtime.R
import com.tecnobasics.parqtime.SessionController
import com.tecnobasics.parqtime.alarm.AlarmService
import com.tecnobasics.parqtime.core.Fmt
import com.tecnobasics.parqtime.core.ParkingMath
import com.tecnobasics.parqtime.core.toLocal
import com.tecnobasics.parqtime.data.ParqStore
import com.tecnobasics.parqtime.data.Session
import kotlinx.coroutines.delay

private enum class Screen { Main, Settings }

@Composable
fun ParqApp(openAdd: Boolean, onOpenAddHandled: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ParqStore(ctx) }
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var revision by remember { mutableIntStateOf(0) }

    // Reloj de la pantalla: un tic por segundo, alineado al segundo exacto
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val session = remember(nowMs, revision) { store.session }
    val missing = remember(nowMs / 2000L, revision) { Perms.missing(ctx) }
    val changed: () -> Unit = { revision++ }

    when (screen) {
        Screen.Main -> MainScreen(
            store = store,
            session = session,
            nowMs = nowMs,
            missing = missing,
            openAdd = openAdd,
            onOpenAddHandled = onOpenAddHandled,
            onChanged = changed,
            onSettings = { screen = Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(
            store = store,
            missing = missing,
            onBack = {
                screen = Screen.Main
                changed()
            },
        )
    }
}

@Composable
private fun MainScreen(
    store: ParqStore,
    session: Session?,
    nowMs: Long,
    missing: List<Perm>,
    openAdd: Boolean,
    onOpenAddHandled: () -> Unit,
    onChanged: () -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted || !Notifs.canPost(ctx)) Perms.open(ctx, Perm.NOTIFICATIONS)
        onChanged()
    }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else Perms.open(ctx, Perm.NOTIFICATIONS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TopBar(onSettings)
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PermissionCard(missing) { p -> if (p == Perm.NOTIFICATIONS) askNotifications() else Perms.open(ctx, p) }
        }
        Spacer(Modifier.height(18.dp))
        if (session == null) {
            IdleContent(store, nowMs) { cents ->
                if (Perm.NOTIFICATIONS in missing) askNotifications()
                SessionController.start(ctx, cents)
                onChanged()
            }
        } else {
            ActiveContent(store, session, nowMs, openAdd, onOpenAddHandled, onChanged)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.logo_parqtime),
            contentDescription = "ParqTime",
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = "ParqTime", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "Parquímetros ecoParq", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSettings) {
            Text(text = "Ajustes", color = Lime, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PermissionCard(missing: List<Perm>, onFix: (Perm) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.copy(alpha = 0.12f))
            .border(1.dp, Amber.copy(alpha = 0.6f), shape)
            .padding(16.dp),
    ) {
        Text(
            text = "Falta configurar para que la alarma no falle",
            color = Amber,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        missing.forEach { p ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = p.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(text = p.desc, color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onFix(p) },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text(text = "Activar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------- Sin conteo: capturar el pago ----------

@Composable
private fun IdleContent(store: ParqStore, nowMs: Long, onStart: (Long) -> Unit) {
    val rate = store.rateCents
    var text by rememberSaveable { mutableStateOf("") }
    val cents = Fmt.parseMoney(text)
    val minutes = if (cents != null) ParkingMath.minutesFor(cents, rate) else 0

    Text(text = "¿Cuánto pagaste?", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    Text(text = "Tarifa ${Fmt.money(rate)} por 15 min", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))
    AmountPicker(text = text, rate = rate, onText = { text = it })
    Spacer(Modifier.height(16.dp))
    ExpiryPreview(store, nowMs, minutes, invalid = cents == null)
    Spacer(Modifier.height(18.dp))
    Button(
        onClick = {
            if (cents != null) onStart(cents)
            text = ""
        },
        enabled = minutes > 0,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(text = "INICIAR", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Te aviso a los 25, 10 y 5 minutos y cuando se acaba el tiempo, con sirena, vibración y linterna.",
        color = Muted,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Campo de monto + botones rápidos (+15 min, +30 min, +1 h, +2 h). */
@Composable
private fun AmountPicker(text: String, rate: Long, onText: (String) -> Unit) {
    val current = Fmt.parseMoney(text) ?: 0L
    val big = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink, fontFeatureSettings = "tnum")
    OutlinedTextField(
        value = text,
        onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() || it == '.' || it == ',' }) onText(v) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = big,
        prefix = { Text(text = "$", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Lime) },
        placeholder = { Text(text = "0.00", style = big.copy(color = Muted.copy(alpha = 0.5f))) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Lime,
            unfocusedBorderColor = Line,
            cursorColor = Lime,
            focusedContainerColor = Card,
            unfocusedContainerColor = Card,
        ),
    )
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60, 120).forEach { m ->
            val add = ParkingMath.centsFor(m, rate)
            QuickChip(
                label = if (m < 60) "+$m min" else "+${m / 60} h",
                price = Fmt.money(add),
                modifier = Modifier.weight(1f),
            ) { onText(Fmt.moneyInput(current + add)) }
        }
    }
    if (text.isNotEmpty()) {
        TextButton(onClick = { onText("") }) { Text(text = "Borrar", color = Muted) }
    }
}

@Composable
private fun QuickChip(label: String, price: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, color = Lime, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(text = price, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ExpiryPreview(store: ParqStore, nowMs: Long, minutes: Int, invalid: Boolean) {
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        when {
            invalid -> Text(text = "Monto no válido", color = Red, fontWeight = FontWeight.Bold)
            minutes <= 0 -> Text(text = "Escribe el monto o toca los botones de arriba.", color = Muted)
            else -> {
                val h = store.hours
                val now = nowMs.toLocal()
                val today = now.toLocalDate()
                val expiry = ParkingMath.instantAfter(h, now, minutes * 60L)
                val first = h.windowsFrom(now).first()
                Text(text = "= ${Fmt.duration(minutes)}", color = Lime, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = "Vence ${Fmt.dayLabel(expiry, today)} a las ${Fmt.clock(expiry, h24)}",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (now !in first) {
                    Text(
                        text = "Fuera de horario: el conteo arranca ${Fmt.dayLabel(first.start, today)} a las ${Fmt.clock(first.start, h24)}.",
                        color = Amber,
                        fontSize = 13.sp,
                    )
                } else if (!expiry.isBefore(first.end)) {
                    val next = h.windowsFrom(first.end).first().start
                    Text(
                        text = "Se pausa a las ${Fmt.clock(first.end, h24)} (fin del cobro) y sigue ${Fmt.dayLabel(next, today)} a las ${Fmt.clock(next, h24)}.",
                        color = Amber,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ---------- Con conteo activo ----------

@Composable
private fun ActiveContent(
    store: ParqStore,
    session: Session,
    nowMs: Long,
    openAdd: Boolean,
    onOpenAddHandled: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val h24 = DateFormat.is24HourFormat(ctx)
    val st = SessionController.status(store, session, nowMs)
    val today = nowMs.toLocal().toLocalDate()
    val total = session.paidMinutes * 60L
    val rem = st.remainingSec
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showEnd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openAdd) {
        if (openAdd) {
            showAdd = true
            onOpenAddHandled()
        }
    }

    val color = when {
        rem <= 300 -> Red
        rem <= 600 -> Orange
        rem <= 1500 -> Amber
        else -> Lime
    }
    val blink = rememberInfiniteTransition(label = "blink")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "alpha",
    )

    // Anillo de tiempo
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val frac = if (total > 0) (rem.toFloat() / total).coerceIn(0f, 1f) else 0f
        val ringColor = if (st.paused) Muted else color
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(CardHigh, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(ringColor, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (rem <= 0) {
                Text(
                    text = "¡VENCIDO!",
                    color = Red,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.alpha(alpha),
                )
                Text(text = "hace ${Fmt.countdown(-rem)}", color = Muted, fontSize = 16.sp)
            } else {
                Text(
                    text = Fmt.countdown(rem),
                    color = if (st.paused) Muted else color,
                    fontSize = if (rem >= 3600) 58.sp else 72.sp,
                    fontWeight = FontWeight.Black,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = if (st.paused) "EN PAUSA" else "RESTANTES",
                    color = if (st.paused) Amber else Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        if (rem > 0) {
            Text(
                text = "Vence ${Fmt.dayLabel(st.expiry, today)} a las ${Fmt.clock(st.expiry, h24)}",
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        } else {
            Text(
                text = "Venció a las ${Fmt.clock(st.expiry, h24)}",
                color = Red,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        val resume = st.resumeAt
        if (st.paused && resume != null) {
            Text(
                text = "El parquímetro no cobra ahora. Se reanuda ${Fmt.dayLabel(resume, today)} a las ${Fmt.clock(resume, h24)}.",
                color = Amber,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Pagaste ${Fmt.money(session.amountCents)} · ${Fmt.duration(session.paidMinutes)}",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ParkingMath.MILESTONES.forEach { m ->
                MilestoneChip(
                    label = if (m == 0) "Fin" else "$m min",
                    applies = m == 0 || session.paidMinutes > m,
                    passed = rem <= m * 60L,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(text = "AGREGAR TIEMPO", fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = { showEnd = true },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
    ) {
        Text(text = "Terminar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }

    if (showAdd) {
        AddTimeDialog(
            rate = store.rateCents,
            expired = rem <= 0,
            onDismiss = { showAdd = false },
            onConfirm = { cents ->
                SessionController.addTime(ctx, cents)
                showAdd = false
                onChanged()
            },
        )
    }
    if (showEnd) {
        AlertDialog(
            onDismissRequest = { showEnd = false },
            containerColor = Card,
            title = { Text(text = "¿Terminar el conteo?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Se cancelan todas las alarmas de este estacionamiento.") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmService.stop(ctx)
                    SessionController.end(ctx)
                    showEnd = false
                    onChanged()
                }) { Text(text = "Terminar", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEnd = false }) { Text(text = "Cancelar", color = Muted) }
            },
        )
    }
}

@Composable
private fun MilestoneChip(label: String, applies: Boolean, passed: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val active = applies && !passed
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (active) LimeSoft else if (applies) CardHigh else Color.Transparent)
            .border(1.dp, if (active) Lime.copy(alpha = 0.5f) else Line, shape)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (applies && passed) "✓ $label" else label,
            color = when {
                active -> Lime
                applies -> Muted
                else -> Muted.copy(alpha = 0.4f)
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = if (!applies) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun AddTimeDialog(rate: Long, expired: Boolean, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val cents = Fmt.parseMoney(text)
    val minutes = if (cents != null) ParkingMath.minutesFor(cents, rate) else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text(text = "Agregar tiempo", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (expired) {
                    Text(
                        text = "El tiempo ya venció: se inicia un conteo nuevo desde ahora.",
                        color = Amber,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                AmountPicker(text = text, rate = rate, onText = { text = it })
                if (minutes > 0) {
                    Text(
                        text = "+ ${Fmt.duration(minutes)}",
                        color = Lime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (cents != null) onConfirm(cents) }, enabled = minutes > 0) {
                Text(text = "Agregar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancelar", color = Muted) }
        },
    )
}
