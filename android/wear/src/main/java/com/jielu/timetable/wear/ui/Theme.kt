package com.jielu.timetable.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import com.jielu.timetable.wear.data.WearPalette
import com.jielu.timetable.wear.data.WearPalettes

/** 纯黑底让 OLED 像素真正熄灭；强调色随用户在设置里选的配色变化。 */
object JieluColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF16110F)
    val SurfaceHigh = Color(0xFF211A17)
    val OnSurface = Color(0xFFF4ECE7)
    val Muted = Color(0xFF9C8F88)

    fun course(tone: String): Color = when (tone) {
        "sage" -> Color(0xFFA9D5B3)
        "sky" -> Color(0xFF9FCFE8)
        "iris" -> Color(0xFFCFBDF8)
        "amber" -> Color(0xFFEBC77E)
        "rose" -> Color(0xFFF1B7C2)
        else -> Color(0xFFFFB4A4)
    }
}

private fun jieluColors(palette: WearPalette) = Colors(
    primary = Color(palette.accent),
    primaryVariant = Color(palette.accentDeep),
    secondary = Color(palette.secondary),
    surface = JieluColors.Surface,
    background = JieluColors.Background,
    error = Color(0xFFFFB4AB),
    onPrimary = Color(palette.onAccent),
    onSecondary = Color(palette.onAccent),
    onSurface = JieluColors.OnSurface,
    onBackground = JieluColors.OnSurface,
    onError = Color(0xFF690005)
)

private val JieluTypography = Typography(
    display1 = TextStyle(fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 44.sp),
    display2 = TextStyle(fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 38.sp),
    display3 = TextStyle(fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 32.sp),
    title1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    title2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 21.sp),
    title3 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
    caption1 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
    caption2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)

@Composable
fun JieluWearTheme(
    palette: WearPalette = WearPalettes.clay,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = remember(palette) { jieluColors(palette) },
        typography = JieluTypography,
        content = content
    )
}
