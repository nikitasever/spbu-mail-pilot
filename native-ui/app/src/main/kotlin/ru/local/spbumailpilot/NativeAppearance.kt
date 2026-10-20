package ru.local.spbumailpilot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalInboxDark=staticCompositionLocalOf {false}
@Composable internal fun nativeSuccessColor()=if(LocalInboxDark.current)Color(0xFF34D399) else Color(0xFF08734B)
