package com.tecnobasics.parqtime.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tecnobasics.parqtime.Perm
import com.tecnobasics.parqtime.Perms
import com.tecnobasics.parqtime.SessionController
import com.tecnobasics.parqtime.alarm.AlarmService
import com.tecnobasics.parqtime.alarm.AlarmTexts
import com.tecnobasics.parqtime.core.Fmt
import com.tecnobasics.parqtime.core.ServiceHours
import com.tecnobasics.parqtime.data.ParqStore

@Composable
fun SettingsScreen(store: ParqStore, missing: List<Perm>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)

    val h = remember { store.hours }
    var rate by rememberSaveable { mutableStateOf(Fmt.moneyInput(store.rateCents)) }
    var start by rememberSaveable { mutableStateOf(Fmt.timeInput(h.start)) }
    var endMonTue by rememberSaveable { mutableStateOf(Fmt.timeInput(h.endMonTue)) }
    var endWedSat by rememberSaveable { mutableStateOf(Fmt.timeInput(h.endWedSat)) }
    var sunday by rememberSaveable { mutableStateOf(h.sundayFree) }
    var error by remember { mutableStateOf<String?>(null) }
    val torch = remember { Perms.hasTorch(ctx) }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(text = "‹ Volver", color = Lime, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text(text = "Ajustes", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        Section("Tarifa") {
            Field(label = "Precio por 15 minutos", value = rate, onChange = { rate = it }, prefixText = "$", keyboard = KeyboardType.Decimal)
            Note("ecoParq Polanco: \$3.40 por 15 min. Cámbialo si suben la tarifa.")
        }

        Section("Horario de cobro") {
            Field(label = "Inicio del cobro (todos los días)", value = start, onChange = { start = it })
            Row {
                Field(label = "Cierre lun–mar", value = endMonTue, onChange = { endMonTue = it }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Field(label = "Cierre mié–sáb", value = endWedSat, onChange = { endWedSat = it }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Domingo gratis", color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = sunday,
                    onCheckedChange = { sunday = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = OnLime, checkedTrackColor = Lime),
                )
            }
            Note(
                "Si tu tiempo pagado pasa la hora de cierre, lo que sobra se pausa y sigue al inicio del " +
                    "siguiente día de cobro. Escribe las horas como 20:00 o 2000. Los días festivos no se descuentan solos.",
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = Red, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val r = Fmt.parseMoney(rate)
                val s = Fmt.parseTime(start)
                val a = Fmt.parseTime(endMonTue)
                val b = Fmt.parseTime(endWedSat)
                if (r == null || r <= 0) {
                    error = "Revisa la tarifa."
                } else if (s == null || a == null || b == null) {
                    error = "Revisa las horas (por ejemplo 08:00)."
                } else {
                    store.rateCents = r
                    store.hours = ServiceHours(start = s, endMonTue = a, endWedSat = b, sundayFree = sunday)
                    SessionController.refresh(ctx)
                    Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(text = "Guardar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Section("Probar alarma") {
            Note("Suena la sirena al máximo, vibra y parpadea la linterna. Apágala con el botón APAGAR.")
            OutlinedButton(
                onClick = { AlarmService.start(ctx, AlarmTexts.STAGE_TEST) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Lime),
            ) { Text(text = "Probar alarma ahora", fontWeight = FontWeight.Bold) }
        }

        Section("Permisos") {
            Perm.entries.forEach { p ->
                val ok = p !in missing
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (ok) "✓  " else "✗  ") + p.title,
                        color = if (ok) Lime else Amber,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (!ok) {
                        TextButton(onClick = { Perms.open(ctx, p) }) { Text(text = "Activar", color = Amber) }
                    }
                }
            }
            Text(
                text = if (torch) "✓  Linterna disponible" else "✗  Este teléfono no tiene linterna",
                color = if (torch) Lime else Muted,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "ParqTime $version · Tecnobasics",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(text = title.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun Note(text: String) {
    Text(text = text, color = Muted, fontSize = 13.sp)
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefixText: String? = null,
    keyboard: KeyboardType = KeyboardType.Number,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        prefix = { if (prefixText != null) Text(text = prefixText, color = Lime) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Lime,
            unfocusedBorderColor = Line,
            cursorColor = Lime,
            focusedLabelColor = Lime,
            unfocusedLabelColor = Muted,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}
