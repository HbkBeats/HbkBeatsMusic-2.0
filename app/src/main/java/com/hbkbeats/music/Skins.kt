package com.hbkbeats.music

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class Skin(
    val name: String,
    val bg: Color,
    val surface: Color,
    val primary: Color,
    val light: Boolean = false
)

val Skins = listOf(
    Skin("Groove Green", Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFF1DB954)),
    Skin("Sunset", Color(0xFF140A05), Color(0xFF24140C), Color(0xFFFF6A00)),
    Skin("Ocean", Color(0xFF08121F), Color(0xFF10223A), Color(0xFF2D9CFF)),
    Skin("Royal Purple", Color(0xFF120A1F), Color(0xFF1F1433), Color(0xFFA855F7)),
    Skin("Rose", Color(0xFF1A0A12), Color(0xFF2A1420), Color(0xFFFF4D8D)),
    Skin("Crimson", Color(0xFF120505), Color(0xFF200C0C), Color(0xFFE53935)),
    Skin("Gold Black", Color(0xFF000000), Color(0xFF121212), Color(0xFFFFC107)),
    Skin("Snow", Color(0xFFF5F5F5), Color(0xFFFFFFFF), Color(0xFF1DB954), light = true)
)

val AccentChoices = listOf(
    Color(0xFF1DB954), Color(0xFFFF6A00), Color(0xFF2D9CFF), Color(0xFFA855F7),
    Color(0xFFFF4D8D), Color(0xFFE53935), Color(0xFFFFC107), Color(0xFF00BFA5)
)

object ThemeState {
    var skinIndex by mutableIntStateOf(0); private set
    var customAccent by mutableStateOf<Color?>(null); private set

    val skin: Skin get() = Skins[skinIndex.coerceIn(Skins.indices)]
    val accent: Color get() = customAccent ?: skin.primary

    fun load() {
        skinIndex = Prefs.sp.getInt("skin", 0)
        val a = Prefs.sp.getInt("accent", 0)
        customAccent = if (a == 0) null else Color(a)
    }

    fun setSkin(i: Int) {
        skinIndex = i
        Prefs.sp.edit().putInt("skin", i).apply()
    }

    fun setAccent(c: Color?) {
        customAccent = c
        Prefs.sp.edit().putInt("accent", c?.toArgb() ?: 0).apply()
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val sk = ThemeState.skin
    val accent = ThemeState.accent
    val onBg = if (sk.light) Color(0xFF121212) else Color.White
    val onAccent = if (accent.luminance() > 0.35f) Color.Black else Color.White
    val scheme = if (sk.light) lightColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent,
        background = sk.bg, onBackground = onBg,
        surface = sk.surface, onSurface = onBg,
        surfaceVariant = Color(0xFFE6E6E6), onSurfaceVariant = onBg.copy(alpha = 0.65f)
    ) else darkColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent,
        background = sk.bg, onBackground = onBg,
        surface = sk.surface, onSurface = onBg,
        surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = onBg.copy(alpha = 0.65f)
    )
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        w.statusBarColor = sk.bg.toArgb()
        w.navigationBarColor = sk.bg.toArgb()
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = sk.light
        c.isAppearanceLightNavigationBars = sk.light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
