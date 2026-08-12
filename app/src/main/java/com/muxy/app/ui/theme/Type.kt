package com.muxy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.muxy.app.R

@OptIn(ExperimentalTextApi::class)
private fun fredoka(weight: Int) = Font(
    resId = R.font.fredoka_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    resId = R.font.nunito_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Redonda y con carácter. Titulares, nombres de canción, cifras. */
val FredokaFamily = FontFamily(fredoka(400), fredoka(500), fredoka(600), fredoka(700))

/** Legible y cálida. Cuerpo, metadatos, etiquetas. */
val NunitoFamily = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700))

val MuxyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight(600),
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight(600),
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight(600),
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight(500),
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight(500),
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FredokaFamily, fontWeight = FontWeight(500),
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NunitoFamily, fontWeight = FontWeight(400),
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NunitoFamily, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NunitoFamily, fontWeight = FontWeight(400),
        fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NunitoFamily, fontWeight = FontWeight(600),
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NunitoFamily, fontWeight = FontWeight(600),
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NunitoFamily, fontWeight = FontWeight(600),
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp,
    ),
)
