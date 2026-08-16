package com.procwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The visual reference is a rack-mounted monitoring panel, not a phone settings screen:
 * dark slate, one amber signal colour, numbers set in monospace so digits line up in a
 * column and a jumping value does not reflow the row. Dark only — this is an instrument.
 */
object Panel {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF161B22)
    val SurfaceRaised = Color(0xFF1F2630)
    val Outline = Color(0xFF2A323D)
    val OutlineSoft = Color(0xFF212933)

    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B98A5)
    val TextFaint = Color(0xFF5C6673)

    /** The single accent. Used for live values and the primary action, nothing else. */
    val Signal = Color(0xFFFFB454)
    val Danger = Color(0xFFFF7B72)
    val Good = Color(0xFF6BCB77)

    /** Standby bucket rail. Desaturated so it never competes with Signal. */
    val BucketActive = Color(0xFF63B3ED)
    val BucketWorking = Color(0xFF68D391)
    val BucketFrequent = Color(0xFFD9C05A)
    val BucketRare = Color(0xFFDD9550)
    val BucketRestricted = Color(0xFFD16B6B)
    val BucketUnknown = Color(0xFF39424E)

    val RowHeight = 62.dp
    val RailWidth = 3.dp
    val Radius = 4.dp
}

fun bucketColor(bucket: Int?): Color = when (bucket) {
    10 -> Panel.BucketActive
    20 -> Panel.BucketWorking
    30 -> Panel.BucketFrequent
    40 -> Panel.BucketRare
    45 -> Panel.BucketRestricted
    else -> Panel.BucketUnknown
}

private val scheme = darkColorScheme(
    primary = Panel.Signal,
    onPrimary = Panel.Background,
    secondary = Panel.TextSecondary,
    background = Panel.Background,
    onBackground = Panel.TextPrimary,
    surface = Panel.Surface,
    onSurface = Panel.TextPrimary,
    surfaceVariant = Panel.SurfaceRaised,
    onSurfaceVariant = Panel.TextSecondary,
    outline = Panel.Outline,
    error = Panel.Danger,
    onError = Panel.Background
)

/** Numbers, package names, pids, shell output — anything that is data rather than prose. */
val DataStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp
)

val DataStyleLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 26.sp,
    fontWeight = FontWeight.Medium
)

/** Section eyebrows. Wide tracking, always upper case at the call site. */
val EyebrowStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.4.sp
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = EyebrowStyle
)

@Composable
fun ProcWatchTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
