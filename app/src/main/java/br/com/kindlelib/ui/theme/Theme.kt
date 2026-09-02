package br.com.kindlelib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F54),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8FD9C4),
    onPrimaryContainer = Color(0xFF0A2E28),
    secondary = Color(0xFFB07A3C),
    onSecondary = Color.White,
    background = Color(0xFFFAF6F1),
    onBackground = Color(0xFF201A15),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF201A15),
    surfaceVariant = Color(0xFFECE4D8),
    onSurfaceVariant = Color(0xFF5A524A),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD9C4),
    onPrimary = Color(0xFF0A2E28),
    primaryContainer = Color(0xFF1F5F54),
    onPrimaryContainer = Color(0xFFD6F6EC),
    secondary = Color(0xFFE0B080),
    background = Color(0xFF141210),
    onBackground = Color(0xFFEDE8E2),
    surface = Color(0xFF1C1916),
    onSurface = Color(0xFFEDE8E2),
    surfaceVariant = Color(0xFF2C2722),
    onSurfaceVariant = Color(0xFFCFC6BC),
    error = Color(0xFFF2B8B5)
)

@Composable
fun KindleLibTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
