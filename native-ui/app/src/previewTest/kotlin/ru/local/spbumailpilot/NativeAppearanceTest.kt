package ru.local.spbumailpilot

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeAppearanceTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5,showSystemUi=false)

    @Test fun themeModesRespectSystemOverride() {
        val mode=mutableStateOf("dark")
        val systemDark=mutableStateOf(false)
        var observedDark=false
        var observedBackground=Color.Unspecified
        var observedCategory=Color.Unspecified
        fun render(name:String,choice:String,night:Boolean,expected:Boolean) {
            mode.value=choice;systemDark.value=night
            paparazzi.snapshot(name=name) {
                val configuration=Configuration(LocalConfiguration.current).apply {
                    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        (if(systemDark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    InboxTheme(mode.value) {
                        observedDark=LocalInboxDark.current
                        observedBackground=MaterialTheme.colorScheme.background
                        observedCategory=categoryColor("blue")
                        Text("Рабочая почта")
                    }
                }
            }
            assertEquals(expected,observedDark)
            assertTrue(if(expected)observedBackground.luminance()<0.1f else observedBackground.luminance()>0.9f)
            assertEquals(if(expected)Color(0xFF99C6F5) else Color(0xFF30649E),observedCategory)
        }
        render("dark-on-light-system","dark",false,true)
        render("light-on-dark-system","light",true,false)
        render("system-light","system",false,false)
        render("system-dark","system",true,true)
        render("dark-on-dark-system","dark",true,true)
        render("light-on-light-system","light",false,false)
    }

    @Test fun avatarPaletteHasReadableStableIdentityColors() {
        val colors=(0..255).map {senderAvatarColors("sender$it@example.invalid")}.toSet()
        assertEquals(8,colors.size)
        colors.forEach {
            val contrast=(it.foreground.luminance()+0.05f)/(it.background.luminance()+0.05f)
            assertTrue("Avatar initials contrast: $contrast",contrast>=4.5f)
        }
        assertEquals(senderAvatarColors(" User@Example.invalid "),senderAvatarColors("user@example.invalid"))
        assertNotNull(senderAvatarColors(""))
    }
}
