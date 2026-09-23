package app.rayik.music.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.rayik.music.R

/**
 * rayik themes: 5 kept from mpvium (Dynamic, Forest, RoseGold, RosePine,
 * NoirCinema renamed to Vinyl Noir) + 10 new first-class entries.
 *
 * Declaration order IS picker order per AGENTS.md: Gold, Dynamic, then the
 * Indian cluster (Barber, Bus, Peacock, Chai, Banarasi, Monsoon, Indigo,
 * Rangoli), then the rest, closing with Hacker.
 *
 * Do NOT port Color.kt (legacy) or Glass.kt/Haze. Album-art dynamic
 * (Palette -> primaryContainer, toggleable) is the one sanctioned deviation
 * and lives in RayikTheme, not here.
 */
enum class AppTheme(
  @StringRes val titleRes: Int,
  val primaryLight: Color,
  val primaryDark: Color,
  val secondaryLight: Color,
  val secondaryDark: Color,
  val tertiaryLight: Color,
  val tertiaryDark: Color,
  val backgroundLight: Color,
  val backgroundDark: Color,
  val searchKeywords: String = "",
  val isDynamic: Boolean = false,
) {
  Gold(
    titleRes = R.string.theme_gold,
    primaryLight = Color(0xFF8A6D1B),
    primaryDark = Color(0xFFEAC453),
    secondaryLight = Color(0xFF7A5A2E),
    secondaryDark = Color(0xFFC8A24A),
    tertiaryLight = Color(0xFF9C7F3A),
    tertiaryDark = Color(0xFFF9E7A1),
    backgroundLight = Color(0xFFFFF8E7),
    backgroundDark = Color(0xFF14100A),
    searchKeywords = "gold, premium, bullion, brass",
  ),
  Dynamic(
    titleRes = R.string.theme_dynamic,
    primaryLight = Color(0xFF6750A4),
    primaryDark = Color(0xFFD0BCFF),
    secondaryLight = Color(0xFF625B71),
    secondaryDark = Color(0xFFCCC2DC),
    tertiaryLight = Color(0xFF7D5260),
    tertiaryDark = Color(0xFFEFB8C8),
    backgroundLight = Color(0xFFFFFBFF),
    backgroundDark = Color(0xFF1C1B1F),
    searchKeywords = "dynamic, wallpaper, adaptive, system",
    isDynamic = true,
  ),
  Barber(
    titleRes = R.string.theme_barber,
    primaryLight = Color(0xFF00695C),
    primaryDark = Color(0xFF4DB6AC),
    secondaryLight = Color(0xFF6A1B2A),
    secondaryDark = Color(0xFFE57373),
    tertiaryLight = Color(0xFF9C6D00),
    tertiaryDark = Color(0xFFFFC93C),
    backgroundLight = Color(0xFFF9F3E7),
    backgroundDark = Color(0xFF0E1514),
    searchKeywords = "barber, hajam, shave, mirror, shop",
  ),
  LocalBus(
    titleRes = R.string.theme_local_bus,
    primaryLight = Color(0xFFC62828),
    primaryDark = Color(0xFFFF6659),
    secondaryLight = Color(0xFF2E7D32),
    secondaryDark = Color(0xFF81C784),
    tertiaryLight = Color(0xFFF9A825),
    tertiaryDark = Color(0xFFFFD54F),
    backgroundLight = Color(0xFFFFF8E1),
    backgroundDark = Color(0xFF101512),
    searchKeywords = "bus, ticket, best, city, travel",
  ),
  Peacock(
    titleRes = R.string.theme_peacock,
    primaryLight = Color(0xFF1B6FA8),
    primaryDark = Color(0xFF35A7E8),
    secondaryLight = Color(0xFF0E7A4C),
    secondaryDark = Color(0xFF2FBF71),
    tertiaryLight = Color(0xFFB78A00),
    tertiaryDark = Color(0xFFF2B705),
    backgroundLight = Color(0xFFEDF5FA),
    backgroundDark = Color(0xFF081420),
    searchKeywords = "peacock, mor, feather, blue",
  ),
  Chai(
    titleRes = R.string.theme_chai,
    primaryLight = Color(0xFF9C6420),
    primaryDark = Color(0xFFE8A93D),
    secondaryLight = Color(0xFF7A3D1C),
    secondaryDark = Color(0xFFB65D2E),
    tertiaryLight = Color(0xFF8A6A3A),
    tertiaryDark = Color(0xFFF5E6C8),
    backgroundLight = Color(0xFFFBF3E4),
    backgroundDark = Color(0xFF120C07),
    searchKeywords = "chai, kulhad, tea, cutting",
  ),
  Banarasi(
    titleRes = R.string.theme_banarasi,
    primaryLight = Color(0xFF8A2A5C),
    primaryDark = Color(0xFFD94F8C),
    secondaryLight = Color(0xFF4A122E),
    secondaryDark = Color(0xFF7A1E4E),
    tertiaryLight = Color(0xFF9C7A20),
    tertiaryDark = Color(0xFFE7C15A),
    backgroundLight = Color(0xFFFBF0F4),
    backgroundDark = Color(0xFF1A0B12),
    searchKeywords = "banarasi, silk, zari, saree, wine",
  ),
  Monsoon(
    titleRes = R.string.theme_monsoon,
    primaryLight = Color(0xFF4A6B84),
    primaryDark = Color(0xFF7FA8C9),
    secondaryLight = Color(0xFF243F4D),
    secondaryDark = Color(0xFF3E6B7E),
    tertiaryLight = Color(0xFFB78F00),
    tertiaryDark = Color(0xFFF2C230),
    backgroundLight = Color(0xFFEDF2F5),
    backgroundDark = Color(0xFF0B1116),
    searchKeywords = "monsoon, barsaat, rain, cloud",
  ),
  Indigo(
    titleRes = R.string.theme_indigo,
    primaryLight = Color(0xFF35419F),
    primaryDark = Color(0xFF5C6FF0),
    secondaryLight = Color(0xFF1C245C),
    secondaryDark = Color(0xFF2E3A8C),
    tertiaryLight = Color(0xFF9C6D00),
    tertiaryDark = Color(0xFFE8A90C),
    backgroundLight = Color(0xFFEEF0FF),
    backgroundDark = Color(0xFF0C0E1E),
    searchKeywords = "indigo, neel, dye, blue",
  ),
  Rangoli(
    titleRes = R.string.theme_rangoli,
    primaryLight = Color(0xFF8A2424),
    primaryDark = Color(0xFFF2EAD8),
    secondaryLight = Color(0xFF9C4E14),
    secondaryDark = Color(0xFFE8842C),
    tertiaryLight = Color(0xFF8A2424),
    tertiaryDark = Color(0xFFC63A3A),
    backgroundLight = Color(0xFFFFF3E6),
    backgroundDark = Color(0xFF200C0A),
    searchKeywords = "rangoli, kolam, festive, red",
  ),
  VinylNoir(
    titleRes = R.string.theme_vinyl_noir,
    primaryLight = Color(0xFF1E2025),
    primaryDark = Color(0xFFE6E1D5),
    secondaryLight = Color(0xFF4E565E),
    secondaryDark = Color(0xFF9AA3AB),
    tertiaryLight = Color(0xFF7A5C1E),
    tertiaryDark = Color(0xFFD9A940),
    backgroundLight = Color(0xFFEFE9DC),
    backgroundDark = Color(0xFF0A0B0D),
    searchKeywords = "noir, vinyl, black, cinema, night",
  ),
  Forest(
    titleRes = R.string.theme_forest,
    primaryLight = Color(0xFF2E7D32),
    primaryDark = Color(0xFF9BD79C),
    secondaryLight = Color(0xFF4E6355),
    secondaryDark = Color(0xFFB3CDBD),
    tertiaryLight = Color(0xFF7A5D00),
    tertiaryDark = Color(0xFFE1C16A),
    backgroundLight = Color(0xFFF0F7EC),
    backgroundDark = Color(0xFF0C1510),
    searchKeywords = "forest, green, calm, nature",
  ),
  RoseGold(
    titleRes = R.string.theme_rose_gold,
    primaryLight = Color(0xFFA63D57),
    primaryDark = Color(0xFFF5B4C1),
    secondaryLight = Color(0xFF77574E),
    secondaryDark = Color(0xFFE2BFB2),
    tertiaryLight = Color(0xFF7E5A00),
    tertiaryDark = Color(0xFFE5BE5F),
    backgroundLight = Color(0xFFFFF4F1),
    backgroundDark = Color(0xFF1D1114),
    searchKeywords = "rose, gold, blush, romantic",
  ),
  RosePine(
    titleRes = R.string.theme_rose_pine,
    primaryLight = Color(0xFFB4637A),
    primaryDark = Color(0xFFEBBCBA),
    secondaryLight = Color(0xFF286983),
    secondaryDark = Color(0xFF9CCFD8),
    tertiaryLight = Color(0xFF907AA9),
    tertiaryDark = Color(0xFFC4A7E7),
    backgroundLight = Color(0xFFFAF4ED),
    backgroundDark = Color(0xFF191724),
    searchKeywords = "rose, pine, rosepine, soft, dreamy",
  ),
  Hacker(
    titleRes = R.string.theme_hacker,
    primaryLight = Color(0xFF2EA800),
    primaryDark = Color(0xFF4AF626),
    secondaryLight = Color(0xFF1F7A3A),
    secondaryDark = Color(0xFF1F9D55),
    tertiaryLight = Color(0xFFCC8A00),
    tertiaryDark = Color(0xFFFFB000),
    backgroundLight = Color(0xFFF2FFF2),
    backgroundDark = Color(0xFF060A06),
    searchKeywords = "hacker, terminal, matrix, green, code",
  );

  fun getLightColorScheme(): ColorScheme {
    val surfaceTint = primaryLight.copy(alpha = 0.05f).compositeOver(backgroundLight)
    return lightColorScheme(
      primary = primaryLight,
      onPrimary = Color.White,
      primaryContainer = primaryLight.copy(alpha = 0.15f).compositeOver(Color.White),
      onPrimaryContainer = primaryLight.darken(0.3f),
      secondary = secondaryLight,
      onSecondary = Color.White,
      secondaryContainer = secondaryLight.copy(alpha = 0.15f).compositeOver(Color.White),
      onSecondaryContainer = secondaryLight.darken(0.3f),
      tertiary = tertiaryLight,
      onTertiary = Color.White,
      tertiaryContainer = tertiaryLight.copy(alpha = 0.15f).compositeOver(Color.White),
      onTertiaryContainer = tertiaryLight.darken(0.3f),
      error = Color(0xFFBA1A1A),
      onError = Color.White,
      errorContainer = Color(0xFFFFDAD6),
      onErrorContainer = Color(0xFF93000A),
      background = backgroundLight,
      onBackground = Color(0xFF1C1B1F),
      surface = backgroundLight,
      onSurface = Color(0xFF1C1B1F),
      surfaceVariant = primaryLight.copy(alpha = 0.08f).compositeOver(Color(0xFFF0F0F0)),
      onSurfaceVariant = Color(0xFF49454F),
      outline = secondaryLight.copy(alpha = 0.5f).compositeOver(Color(0xFF79747E)),
      outlineVariant = primaryLight.copy(alpha = 0.12f).compositeOver(Color(0xFFCAC4D0)),
      inverseSurface = backgroundDark,
      inverseOnSurface = Color(0xFFF4EFF4),
      inversePrimary = primaryDark,
      surfaceContainerLowest = backgroundLight,
      surfaceContainerLow = surfaceTint,
      surfaceContainer = primaryLight.copy(alpha = 0.06f).compositeOver(backgroundLight),
      surfaceContainerHigh = primaryLight.copy(alpha = 0.08f).compositeOver(backgroundLight),
      surfaceContainerHighest = primaryLight.copy(alpha = 0.11f).compositeOver(backgroundLight),
    )
  }

  fun getDarkColorScheme(): ColorScheme {
    val surfaceTint = primaryDark.copy(alpha = 0.05f).compositeOver(backgroundDark)
    return darkColorScheme(
      primary = primaryDark,
      onPrimary = primaryLight.darken(0.5f),
      primaryContainer = primaryLight.darken(0.3f),
      onPrimaryContainer = primaryDark.lighten(0.1f),
      secondary = secondaryDark,
      onSecondary = secondaryLight.darken(0.5f),
      secondaryContainer = secondaryLight.darken(0.3f),
      onSecondaryContainer = secondaryDark.lighten(0.1f),
      tertiary = tertiaryDark,
      onTertiary = tertiaryLight.darken(0.5f),
      tertiaryContainer = tertiaryLight.darken(0.3f),
      onTertiaryContainer = tertiaryDark.lighten(0.1f),
      error = Color(0xFFFFB4AB),
      onError = Color(0xFF690005),
      errorContainer = Color(0xFF93000A),
      onErrorContainer = Color(0xFFFFDAD6),
      background = backgroundDark,
      onBackground = Color(0xFFE6E1E5),
      surface = backgroundDark,
      onSurface = Color(0xFFE6E1E5),
      surfaceVariant = primaryDark.copy(alpha = 0.12f).compositeOver(Color(0xFF2A2A2A)),
      onSurfaceVariant = Color(0xFFCAC4D0),
      outline = secondaryDark.copy(alpha = 0.4f).compositeOver(Color(0xFF938F99)),
      outlineVariant = primaryDark.copy(alpha = 0.15f).compositeOver(Color(0xFF49454F)),
      inverseSurface = backgroundLight,
      inverseOnSurface = Color(0xFF313033),
      inversePrimary = primaryLight,
      surfaceContainerLowest = backgroundDark.darken(0.2f),
      surfaceContainerLow = surfaceTint,
      surfaceContainer = primaryDark.copy(alpha = 0.05f).compositeOver(backgroundDark),
      surfaceContainerHigh = primaryDark.copy(alpha = 0.08f).compositeOver(backgroundDark),
      surfaceContainerHighest = primaryDark.copy(alpha = 0.11f).compositeOver(backgroundDark),
    )
  }

  fun getAmoledColorScheme(): ColorScheme {
    return getDarkColorScheme().copy(
      background = Color.Black,
      surface = Color.Black,
      surfaceVariant = primaryDark.copy(alpha = 0.08f).compositeOver(Color(0xFF1A1A1A)),
      surfaceContainer = Color(0xFF0A0A0A),
      surfaceContainerLow = Color(0xFF050505),
      surfaceContainerLowest = Color.Black,
      surfaceContainerHigh = primaryDark.copy(alpha = 0.05f).compositeOver(Color(0xFF151515)),
      surfaceContainerHighest = primaryDark.copy(alpha = 0.08f).compositeOver(Color(0xFF1F1F1F)),
      surfaceDim = Color.Black,
      surfaceBright = primaryDark.copy(alpha = 0.06f).compositeOver(Color(0xFF2A2A2A)),
    )
  }

  companion object {
    fun search(query: String): List<AppTheme> {
      val q = query.trim().lowercase()
      if (q.isEmpty()) return entries
      return entries.filter {
        it.name.lowercase().contains(q) || it.searchKeywords.lowercase().contains(q)
      }
    }
  }
}

private fun Color.darken(factor: Float): Color {
  return Color(
    red = (red * (1 - factor)).coerceIn(0f, 1f),
    green = (green * (1 - factor)).coerceIn(0f, 1f),
    blue = (blue * (1 - factor)).coerceIn(0f, 1f),
    alpha = alpha
  )
}

private fun Color.lighten(factor: Float): Color {
  return Color(
    red = (red + (1 - red) * factor).coerceIn(0f, 1f),
    green = (green + (1 - green) * factor).coerceIn(0f, 1f),
    blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
    alpha = alpha
  )
}

private fun Color.compositeOver(background: Color): Color {
  val bgAlpha = background.alpha
  val fgAlpha = alpha
  val a = fgAlpha + bgAlpha * (1f - fgAlpha)
  return if (a == 0f) {
    Color.Transparent
  } else {
    Color(
      red = (red * fgAlpha + background.red * bgAlpha * (1f - fgAlpha)) / a,
      green = (green * fgAlpha + background.green * bgAlpha * (1f - fgAlpha)) / a,
      blue = (blue * fgAlpha + background.blue * bgAlpha * (1f - fgAlpha)) / a,
      alpha = a
    )
  }
}
