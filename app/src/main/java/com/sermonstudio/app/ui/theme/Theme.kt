package com.sermonstudio.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sermonstudio.app.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E3A59), onPrimary = Color.White,
    secondary = Color(0xFF8A6D1D), tertiary = Color(0xFF3E6B5A),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3C1E8), onPrimary = Color(0xFF14203A),
    secondary = Color(0xFFE0B04B), tertiary = Color(0xFF9BD1BC),
)

/**
 * Material 3 theme. [mode] is the manual Light/Dark/System switch from Settings;
 * [dynamicColor] uses wallpaper-based colours on Android 12+ and falls back to the brand palette below that.
 */
@Composable
fun SermonStudioTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
