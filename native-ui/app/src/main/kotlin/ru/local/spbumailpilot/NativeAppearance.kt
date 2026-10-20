package ru.local.spbumailpilot

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Resolved application theme, including an explicit override of the Android theme. */
internal val LocalInboxDark = staticCompositionLocalOf { false }

internal fun inboxColorScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary=Color(0xFFCEACF3), onPrimary=Color(0xFF28153A),
    background=Color(0xFF13111A), onBackground=Color(0xFFF3EFF8),
    surface=Color(0xFF1D1A27), onSurface=Color(0xFFF3EFF8)
) else lightColorScheme(
    primary=Color(0xFF70508F), onPrimary=Color.White,
    background=Color.White, onBackground=Color(0xFF211A29),
    surface=Color(0xFFF8F5FC), onSurface=Color(0xFF211A29)
)

internal data class SenderAvatarColors(val background: Color, val foreground: Color=Color.White)

// Stable identity colors, independent of unread status, selection and theme changes.
private val senderPalette = listOf(
    0xFF6D4599, 0xFF245EAD, 0xFF006F68, 0xFF3D7029,
    0xFF875008, 0xFF9E3B68, 0xFFA53E36, 0xFF4B50A0
).map { SenderAvatarColors(Color(it)) }

internal fun senderAvatarColors(sender: String): SenderAvatarColors {
    val identity=sender.trim().lowercase(Locale.ROOT)
    return senderPalette[Math.floorMod(identity.hashCode(),senderPalette.size)]
}
