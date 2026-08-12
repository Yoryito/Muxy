package com.muxy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PondLightScheme = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = MossPale,
    onPrimaryContainer = MossDeep,
    secondary = Pond,
    onSecondary = Color.White,
    secondaryContainer = PondPale,
    onSecondaryContainer = PondDeep,
    tertiary = Lotus,
    onTertiary = Color.White,
    tertiaryContainer = LotusPale,
    onTertiaryContainer = LotusDeep,
    background = Parchment,
    onBackground = InkForest,
    surface = Parchment,
    onSurface = InkForest,
    surfaceVariant = Sand,
    onSurfaceVariant = InkForestSoft,
    surfaceContainer = ParchmentRaised,
    surfaceContainerHigh = ParchmentRaised,
    outline = SandDeep,
    outlineVariant = Sand,
    error = ErrorClay,
    onError = Color.White,
)

private val PondDarkScheme = darkColorScheme(
    primary = MossLight,
    onPrimary = MossDeep,
    primaryContainer = NightReed,
    onPrimaryContainer = MossPale,
    secondary = PondLight,
    onSecondary = PondDeep,
    secondaryContainer = PondDeep,
    onSecondaryContainer = PondPale,
    tertiary = LotusLight,
    onTertiary = LotusDeep,
    tertiaryContainer = LotusDeep,
    onTertiaryContainer = LotusPale,
    background = NightPond,
    onBackground = InkCream,
    surface = NightPond,
    onSurface = InkCream,
    surfaceVariant = NightPondHigh,
    onSurfaceVariant = InkCreamSoft,
    surfaceContainer = NightPondRaised,
    surfaceContainerHigh = NightPondHigh,
    outline = NightReed,
    outlineVariant = NightPondHigh,
    error = ErrorClayLight,
    onError = Color(0xFF3C1713),
)

/** Tokens propios del tema que Material no cubre. */
data class PondAccents(
    val waterRipple: Color,
    val reed: Color,
    val bloom: Color,
)

val LocalPondAccents = staticCompositionLocalOf {
    PondAccents(waterRipple = PondLight, reed = Moss, bloom = Lotus)
}

@Composable
fun MuxyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // El color dinámico de Android está desactivado a propósito: el tema del estanque
    // debe verse igual en todos los dispositivos.
    val colorScheme = if (darkTheme) PondDarkScheme else PondLightScheme

    val accents = if (darkTheme) {
        PondAccents(waterRipple = PondLight, reed = MossLight, bloom = LotusLight)
    } else {
        PondAccents(waterRipple = Pond, reed = Moss, bloom = Lotus)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPondAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MuxyTypography,
            shapes = MuxyShapes,
            content = content,
        )
    }
}
