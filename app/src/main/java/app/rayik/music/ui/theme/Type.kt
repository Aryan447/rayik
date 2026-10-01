package app.rayik.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = Typography()

/**
 * A single native sans-serif family keeps the interface calm and readable.
 * The hierarchy comes from size and weight rather than mixing display faces.
 */
val AppTypography =
  Typography(
    displayLarge = default.displayLarge.copy(letterSpacing = (-0.5).sp),
    displayMedium = default.displayMedium.copy(letterSpacing = (-0.5).sp),
    displaySmall = default.displaySmall.copy(letterSpacing = (-0.5).sp),
    headlineLarge = default.headlineLarge.copy(letterSpacing = (-0.35).sp),
    headlineMedium = default.headlineMedium.copy(letterSpacing = (-0.25).sp),
    headlineSmall = default.headlineSmall.copy(letterSpacing = 0.sp),
    titleLarge = default.titleLarge.copy(letterSpacing = 0.sp),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = default.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = default.bodyLarge.copy(fontWeight = FontWeight.Normal),
    bodyMedium = default.bodyMedium.copy(fontWeight = FontWeight.Normal),
    bodySmall = default.bodySmall.copy(fontWeight = FontWeight.Normal),
    labelLarge = default.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = default.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = default.labelSmall.copy(fontWeight = FontWeight.Medium),
  )
