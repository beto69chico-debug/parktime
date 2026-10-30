package com.tecnobasics.parqtime.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Verde del logo ParqTime sobre un fondo casi negro: se lee bien a pleno sol y de noche.
val Lime = Color(0xFFA1BC0E)
val LimeSoft = Color(0x29A1BC0E)
val Ink = Color(0xFFF2F4EA)
val Muted = Color(0xFF979D88)
val Bg = Color(0xFF0F110C)
val Card = Color(0xFF191C14)
val CardHigh = Color(0xFF23271C)
val Line = Color(0xFF2E3326)
val Amber = Color(0xFFF5B400)
val Orange = Color(0xFFFF6A1A)
val Red = Color(0xFFFF3B30)
val AlarmRed = Color(0xFFD3140F)
val OnLime = Color(0xFF12150A)

@Composable
fun ParqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = OnLime,
            primaryContainer = CardHigh,
            onPrimaryContainer = Ink,
            secondary = Lime,
            onSecondary = OnLime,
            background = Bg,
            onBackground = Ink,
            surface = Card,
            onSurface = Ink,
            surfaceVariant = CardHigh,
            onSurfaceVariant = Muted,
            surfaceContainer = Card,
            surfaceContainerHigh = CardHigh,
            outline = Line,
            outlineVariant = Line,
            error = Red,
            onError = Color.White,
        ),
        content = content,
    )
}
