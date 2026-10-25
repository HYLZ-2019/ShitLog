package com.lunhui.log.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

val Pine = Color(0xFF1D6659)
val Cream = Color(0xFFFAF8F2)
private val Light = lightColorScheme(primary = Pine, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBE2), onPrimaryContainer = Color(0xFF153B32),
    secondary = Color(0xFF746747), secondaryContainer = Color(0xFFF0E8D6),
    background = Cream, surface = Cream, surfaceVariant = Color(0xFFEBEDE5),
    onBackground = Color(0xFF243B34), onSurface = Color(0xFF243B34), onSurfaceVariant = Color(0xFF68766E),
    outline = Color(0xFF87958D))
private val Dark = darkColorScheme(primary = Color(0xFFA1D5BC), onPrimary = Color(0xFF103A2E),
    primaryContainer = Color(0xFF254D40), onPrimaryContainer = Color(0xFFDDEBE2),
    secondary = Color(0xFFD4C59F), secondaryContainer = Color(0xFF49402B),
    background = Color(0xFF131D19), surface = Color(0xFF131D19), surfaceVariant = Color(0xFF26352E),
    onBackground = Color(0xFFE1E8DE), onSurface = Color(0xFFE1E8DE), onSurfaceVariant = Color(0xFFB1BFB4))
@Composable fun LunhuiTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
