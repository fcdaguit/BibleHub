package com.sermonprep.app.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    secondary = Color(0xFF8A6D1E),
    tertiary = Color(0xFF3F6B53),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7F5),
    secondary = Color(0xFFE8C96A),
    tertiary = Color(0xFF9ED1B1),
)

/** Material 3 theme. Uses wallpaper-based dynamic colour on Android 12+, a navy/gold palette below that. */
@Composable
fun SermonTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
