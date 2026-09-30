package app.rayik.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.rayik.music.R

val OutfitFamily =
  FontFamily(
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.Medium),
    Font(R.font.outfit, FontWeight.SemiBold),
  )

val FrauncesFamily =
  FontFamily(
    Font(R.font.fraunces, FontWeight.Normal),
    Font(R.font.fraunces, FontWeight.Medium),
    Font(R.font.fraunces, FontWeight.SemiBold),
  )

val FrauncesItalicFamily =
  FontFamily(
    Font(R.font.fraunces_italic, FontWeight.Normal),
    Font(R.font.fraunces_italic, FontWeight.Medium),
    Font(R.font.fraunces_italic, FontWeight.SemiBold, FontStyle.Italic),
  )

private val Outfit get() = OutfitFamily
private val Fraunces get() = FrauncesFamily
private val FrauncesItalic get() = FrauncesItalicFamily

private val default = Typography()

/**
 * rāyik type scale: Fraunces (soft optical serif) for display moments —
 * screen titles, Raay reason, player "playing from", Wrapped numbers —
 * Outfit for everything else. Display tracking tightened slightly.
 */
val AppTypography =
  Typography(
    displayLarge = default.displayLarge.copy(fontFamily = Fraunces, letterSpacing = (-0.5).sp),
    displayMedium = default.displayMedium.copy(fontFamily = Fraunces, letterSpacing = (-0.5).sp),
    displaySmall = default.displaySmall.copy(fontFamily = Fraunces, letterSpacing = (-0.5).sp),
    headlineLarge = default.headlineLarge.copy(fontFamily = Fraunces, letterSpacing = (-0.5).sp),
    headlineMedium = default.headlineMedium.copy(fontFamily = Fraunces, letterSpacing = (-0.5).sp),
    headlineSmall = default.headlineSmall.copy(fontFamily = Fraunces, letterSpacing = (-0.5).sp),
    titleLarge = default.titleLarge.copy(fontFamily = Fraunces, letterSpacing = 0.sp),
    titleMedium = default.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
    titleSmall = default.titleSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
    bodyLarge = default.bodyLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Normal),
    bodyMedium = default.bodyMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Normal),
    bodySmall = default.bodySmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Normal),
    labelLarge = default.labelLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
    labelMedium = default.labelMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
    labelSmall = default.labelSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
  )

/** Fraunces italic — the Raay reason line and player "playing from" line. */
val ReasonStyle: TextStyle
  get() = Typography().headlineSmall.copy(fontFamily = FrauncesItalic, fontStyle = FontStyle.Italic)
