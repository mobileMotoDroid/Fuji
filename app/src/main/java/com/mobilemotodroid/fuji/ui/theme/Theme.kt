package com.mobilemotodroid.fuji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// NFR-USA-03：屋外の直射日光でも読めるよう、背景と文字のコントラストを強めにとる。
private val Forest = Color(0xFF2E5E3A)
private val ForestDark = Color(0xFF1B3A23)
private val Moss = Color(0xFF8FB996)
private val Sunrise = Color(0xFFE8A33D)
private val Alert = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8D3),
    onPrimaryContainer = ForestDark,
    secondary = Color(0xFF8A5A12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Sunrise,
    error = Alert,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8F3),
    onBackground = Color(0xFF111411),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111411),
    surfaceVariant = Color(0xFFE2E7DF),
    onSurfaceVariant = Color(0xFF353C35),
    outline = Color(0xFF5E665D),
)

private val DarkColors = darkColorScheme(
    primary = Moss,
    onPrimary = ForestDark,
    primaryContainer = Color(0xFF2F4F36),
    onPrimaryContainer = Color(0xFFCFE8D3),
    secondary = Color(0xFFF2BD6B),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Sunrise,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE3E7E0),
    surface = Color(0xFF181C18),
    onSurface = Color(0xFFE3E7E0),
    surfaceVariant = Color(0xFF353C35),
    onSurfaceVariant = Color(0xFFC3C9C0),
    outline = Color(0xFF8D948B),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        // 記録中の数値は大きく太く表示する
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    )
}

/** 記録中パネルの数値用。 */
val StatValueStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)

@Composable
fun FujiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
