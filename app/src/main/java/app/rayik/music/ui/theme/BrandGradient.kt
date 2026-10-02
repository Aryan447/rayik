package app.rayik.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand gradients ported stop-for-stop from `site/` so the app and
 * the site share one identity. Two families:
 * - **Fixed gold** ([goldVerticalBrush], [ringDiagonalBrush]): brand identity
 *   (logo bars, wordmark). Never theme-tinted — gold is gold in every theme.
 * - **Theme-aware** ([heroGlowBrush], [artSweepBrush]): surfaces that follow
 *   the active [AppTheme] via `MaterialTheme.colorScheme`.
 *
 * This file is the single owner of gradient stops — never near-duplicate
 * these hex values anywhere else (AGENTS.md attention-to-detail rule).
 */
object BrandGradient {
  /**
   * Logo bars `rayikgold` (vertical): `#FFF5D4` 0% → `#F2CE62` 30% →
   * `#DEAC33` 70% → `#9C6E15` 100%.
   */
  val goldVerticalBrush: Brush =
    Brush.verticalGradient(
      0.00f to Color(0xFFFFF5D4),
      0.30f to Color(0xFFF2CE62),
      0.70f to Color(0xFFDEAC33),
      1.00f to Color(0xFF9C6E15),
    )

  /**
   * Logo ring `rayikring` (diagonal): `#FFF3CE` → `#EAC453` → `#9C6E15`.
   * Default start/end already runs corner-to-corner like the SVG's
   * `userSpaceOnUse` 20,20 → 88,88 diagonal.
   */
  val ringDiagonalBrush: Brush =
    Brush.linearGradient(
      listOf(Color(0xFFFFF3CE), Color(0xFFEAC453), Color(0xFF9C6E15)),
    )

  /** Logo tile backdrop (radial): `#261E12` → `#14100A` → `#090704`. */
  val markBackdropBrush: Brush =
    Brush.radialGradient(
      listOf(Color(0xFF261E12), Color(0xFF14100A), Color(0xFF090704)),
    )

  /**
   * Hero glow (`header.hero`): radial `primary` 0% → transparent ~62%,
   * drawn top-center behind the Raay hero card. Theme-aware.
   */
  @Composable
  @ReadOnlyComposable
  fun heroGlowBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return Brush.radialGradient(
      0.0f to primary.copy(alpha = 0.28f),
      0.62f to Color.Transparent,
    )
  }

  /**
   * Bottom-up ambient wash behind the Home feed, in the spirit of the Gemini
   * home screen: the lower third picks up a strong tint that fades out by the
   * middle. Driven by [androidx.compose.material3.ColorScheme.primary], so the
   * glow takes each theme's own colour (Peacock glows blue, Gold glows gold).
   * Never hardcode a hue here — it would break all 16 themes.
   */
  @Composable
  @ReadOnlyComposable
  fun homeGlowBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    // Dark surfaces need more punch to read as a wash; light ones would go muddy.
    val isDark = scheme.background.luminance() < 0.5f
    val near = if (isDark) 0.48f else 0.32f
    val far = if (isDark) 0.30f else 0.18f
    return Brush.verticalGradient(
      0.0f to Color.Transparent,
      0.55f to Color.Transparent,
      0.82f to primary.copy(alpha = far),
      1.0f to primary.copy(alpha = near),
    )
  }

  /**
   * Art tile (`.art`): conic `primary → secondary → tertiary → primary`.
   * Theme-aware sweep for art placeholders and the hero tile.
   */
  @Composable
  @ReadOnlyComposable
  fun artSweepBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.sweepGradient(
      listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary),
    )
  }

  /**
   * Headline text gradient: `primary → secondary` diagonal. Both stops stay
   * readable on light and dark surfaces across all themes (tertiary is
   * reserved for accents — champagne-on-cream does not survive).
   */
  @Composable
  @ReadOnlyComposable
  fun gradientTextBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
      listOf(scheme.primary, scheme.secondary),
    )
  }

  /**
   * Ambient bloom: `color` at [alpha] melting to transparent. Backs hero
   * cards and screen headers so every theme bleeds into its surfaces.
   */
  @Composable
  @ReadOnlyComposable
  fun bloomBrush(
    color: Color,
    alpha: Float,
    center: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY,
  ): Brush {
    return Brush.radialGradient(
      0f to color.copy(alpha = alpha),
      1f to Color.Transparent,
      center = center,
      radius = radius,
    )
  }

  /**
   * Luminance-aware hairline: paper at 14% on dark schemes, ink at 10% on
   * light schemes. Replaces every `Color.White.copy(alpha)` stroke so Gold
   * light keeps a visible hairline. Read the scheme — never hardcode white.
   */
  @Composable
  @ReadOnlyComposable
  fun hairline(
    alphaDark: Float = 0.14f,
    alphaLight: Float = 0.10f,
  ): Color {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return if (isDark) scheme.onSurface.copy(alpha = alphaDark)
    else scheme.onSurface.copy(alpha = alphaLight)
  }

  /** Frosted-glass hairline used on elevated pills, cards and the nav. */
  fun glassHairline(alpha: Float = 0.14f): Color = Color.White.copy(alpha = alpha)

  /** Non-composable overload for previews / canvas work with a known scheme. */
  fun hairlineFor(scheme: androidx.compose.material3.ColorScheme): Color {
    val isDark = scheme.background.luminance() < 0.5f
    return if (isDark) scheme.onSurface.copy(alpha = 0.14f)
    else scheme.onSurface.copy(alpha = 0.10f)
  }
}

private fun Color.luminance(): Float {
  fun channel(c: Float): Float =
    if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
  return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
