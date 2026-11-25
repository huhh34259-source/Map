package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TerraGridAccents(
    val signalOrange: Color,
    val contourMint: Color,
    val cautionAmber: Color,
    val alertRed: Color,
    val gridLine: Color,
    val topoCanvasBg: Color
)

val LocalTerraGridAccents = staticCompositionLocalOf {
    TerraGridAccents(
        signalOrange = SignalOrange,
        contourMint = ContourMint,
        cautionAmber = CautionAmber,
        alertRed = AlertRed,
        gridLine = PineOutline,
        topoCanvasBg = ObsidianBg
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ContourMint,
    onPrimary = ContourMintDark,
    primaryContainer = ContourMintContainer,
    onPrimaryContainer = ContourMint,
    secondary = SignalOrange,
    onSecondary = Color.White,
    secondaryContainer = SignalOrangeContainer,
    onSecondaryContainer = SignalOrange,
    tertiary = CautionAmber,
    onTertiary = Color(0xFF2B1D00),
    background = ObsidianBg,
    onBackground = TextOffWhite,
    surface = PineSurface,
    onSurface = TextOffWhite,
    surfaceVariant = PineSurfaceVariant,
    onSurfaceVariant = TextMutedSage,
    surfaceContainerHigh = PineSurfaceHigh,
    outline = PineOutline,
    error = AlertRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = Color.White,
    primaryContainer = ForestPrimaryContainer,
    onPrimaryContainer = ForestPrimary,
    secondary = BurntOrange,
    onSecondary = Color.White,
    secondaryContainer = BurntOrangeContainer,
    onSecondaryContainer = BurntOrange,
    tertiary = CautionAmber,
    onTertiary = Color(0xFF2B1D00),
    background = TopoPaperBg,
    onBackground = DeepSlateText,
    surface = TopoPaperSurface,
    onSurface = DeepSlateText,
    surfaceVariant = TopoPaperVariant,
    onSurfaceVariant = MutedSlateText,
    surfaceContainerHigh = TopoPaperVariant,
    outline = TopoPaperOutline,
    error = Color(0xFFC62828),
    onError = Color.White
)

private val TerraGridShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TerraGridTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val accents = if (darkTheme) {
        TerraGridAccents(
            signalOrange = SignalOrange,
            contourMint = ContourMint,
            cautionAmber = CautionAmber,
            alertRed = AlertRed,
            gridLine = PineOutline,
            topoCanvasBg = Color(0xFF080E0F)
        )
    } else {
        TerraGridAccents(
            signalOrange = BurntOrange,
            contourMint = ForestPrimary,
            cautionAmber = Color(0xFFB27B00),
            alertRed = Color(0xFFC62828),
            gridLine = TopoPaperOutline,
            topoCanvasBg = Color(0xFFE7EEEA)
        )
    }

    CompositionLocalProvider(LocalTerraGridAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TerraGridTypography,
            shapes = TerraGridShapes,
            content = content
        )
    }
}
