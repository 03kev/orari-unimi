package dev.kevinmuka.orariunimi

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

private val LightColors = lightColorScheme(
    primary = Color(0xFF3158B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF102656),
    secondary = Color(0xFF49766C),
    secondaryContainer = Color(0xFFD2EEE6),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFF6F8FC),
    surfaceContainer = Color(0xFFECF0F7),
    surfaceContainerLow = Color(0xFFF0F3F9),
    onSurface = Color(0xFF1B2638),
    onSurfaceVariant = Color(0xFF5B6574)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFABC4FF),
    onPrimary = Color(0xFF12336E),
    primaryContainer = Color(0xFF244887),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF9DD4C6),
    secondaryContainer = Color(0xFF28564C),
    background = Color(0xFF111823),
    surface = Color(0xFF111823),
    surfaceContainer = Color(0xFF202B39),
    surfaceContainerLow = Color(0xFF1A2431),
    onSurface = Color(0xFFE6EAF2),
    onSurfaceVariant = Color(0xFFAFB9C8)
)

@Composable
fun OrariTheme(content: @Composable () -> Unit) {
    val dark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
