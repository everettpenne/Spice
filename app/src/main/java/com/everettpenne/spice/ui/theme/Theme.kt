package com.everettpenne.spice.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.everettpenne.glass.GlassColors
import com.everettpenne.glass.GlassTheme

/** Spice's brand colours; the theme, type scale and glass kit come from the shared Glass library. */
object Spice {
    /** Jellyfin blue: tab selection, icons, buttons. */
    val accent = Color(0xFF00A4DC)
    /** The other end of Jellyfin's brand gradient. */
    val purple = Color(0xFFAA5CC3)
    val link = Color(0xFF0A84FF)
    val green = Color(0xFF34C759)
    val indigo = Color(0xFF5E5CE6)
    val red = Color(0xFFFF453A)
    val yellow = Color(0xFFFFD60A)
    val orange = Color(0xFFFF9F0A)
}

@Composable
fun SpiceTheme(content: @Composable () -> Unit) =
    GlassTheme(GlassColors(accent = Spice.accent, red = Spice.red, green = Spice.green), content)
