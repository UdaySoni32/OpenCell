package com.opencell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.opencell.calllog.CallSource

// "Signal" palette: deep green for the phone itself, warm amber for anything needing attention.
private val Light = lightColorScheme(
    primary = Color(0xFF0F5132),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EBD6),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF8A5A00),
    secondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF3A5BA0),
    error = Color(0xFFB3261E),
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFF7F8F5),
    surfaceVariant = Color(0xFFDDE5DC),
    onSurfaceVariant = Color(0xFF414941),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DD5AC),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF0F5132),
    onPrimaryContainer = Color(0xFFC8EBD6),
    secondary = Color(0xFFFFB950),
    secondaryContainer = Color(0xFF694300),
    tertiary = Color(0xFFAFC6FF),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF0F1411),
    surface = Color(0xFF0F1411),
    surfaceVariant = Color(0xFF2A322C),
    onSurfaceVariant = Color(0xFFC0C9BF),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun OpenCellTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppTypography, content = content)
}

/** Brand-ish accent per source so the unified list is scannable at a glance. */
fun CallSource.accent(): Color = when (this) {
    CallSource.SIM -> Color(0xFF0F5132)
    CallSource.WHATSAPP, CallSource.WHATSAPP_BUSINESS -> Color(0xFF25A35A)
    CallSource.TELEGRAM -> Color(0xFF2AA3DE)
    CallSource.SIGNAL -> Color(0xFF3A76F0)
    CallSource.DISCORD -> Color(0xFF5865F2)
    CallSource.MESSENGER -> Color(0xFF0A7CFF)
    CallSource.INSTAGRAM -> Color(0xFFD62976)
    CallSource.TEAMS -> Color(0xFF5B5FC7)
    CallSource.MEET -> Color(0xFF00897B)
    CallSource.OTHER -> Color(0xFF6B7280)
}
