package hu.szabonorbert.mokusors.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueLight,
    onPrimaryContainer = BlueDark,
    background = BackgroundLight,
    onBackground = Color(0xFF000000),
    surface = CardLight,
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFD1D1D6),
    error = StatusRed,
    tertiary = StatusPurple
)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C3A5F),
    onPrimaryContainer = Color(0xFF90CAF9),
    background = BackgroundDark,
    onBackground = Color.White,
    surface = CardDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF3A3A3C),
    error = StatusRed,
    tertiary = StatusPurple
)

data class AppColors(
    val statusRed: Color,
    val statusYellow: Color,
    val statusGreen: Color,
    val statusPurple: Color,
    val statusBlue: Color,
    val statusGray: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(StatusRed, StatusYellow, StatusGreen, StatusPurple, Blue, StatusGray)
}

@Composable
fun MokusorsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAppColors provides AppColors(StatusRed, StatusYellow, StatusGreen, StatusPurple, Blue, StatusGray)
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}
