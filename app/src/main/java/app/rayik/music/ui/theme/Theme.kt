package app.rayik.music.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.rayik.music.R
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.preferences.preference.collectAsState
import org.koin.compose.koinInject

val LocalAppTheme = staticCompositionLocalOf { AppTheme.Dynamic }

/**
 * rayik theme. Ported from mpvium MpviumTheme pattern, renamed to RayikTheme.
 * Sanctioned deviation: optional album-art dynamic primaryContainer is applied
 * by callers via [LocalAlbumArtPrimary], not inside the scheme getters.
 */
val LocalAlbumArtPrimary = staticCompositionLocalOf<Color?> { null }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RayikTheme(content: @Composable () -> Unit) {
  val preferences = koinInject<AppearancePreferences>()
  val darkMode by preferences.darkMode.collectAsState()
  val amoledMode by preferences.amoledMode.collectAsState()
  val appTheme by preferences.appTheme.collectAsState()
  val darkTheme = isSystemInDarkTheme()
  val context = LocalContext.current

  val useDarkTheme = when (darkMode) {
    DarkMode.Dark -> true
    DarkMode.Light -> false
    DarkMode.System -> darkTheme
  }

  val colorScheme = when {
    appTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      when {
        useDarkTheme && amoledMode -> {
          dynamicDarkColorScheme(context).copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF151515),
            surfaceContainerHighest = Color(0xFF1F1F1F),
            surfaceDim = Color.Black,
          )
        }
        useDarkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
      }
    }
    useDarkTheme && amoledMode -> appTheme.getAmoledColorScheme()
    useDarkTheme -> appTheme.getDarkColorScheme()
    else -> appTheme.getLightColorScheme()
  }

  CompositionLocalProvider(
    LocalSpacing provides Spacing(),
    LocalAppTheme provides appTheme,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      content = content,
      motionScheme = MotionScheme.expressive(),
    )
  }
}

enum class DarkMode(
  @StringRes val titleRes: Int,
) {
  Dark(R.string.pref_appearance_darkmode_dark),
  Light(R.string.pref_appearance_darkmode_light),
  System(R.string.pref_appearance_darkmode_system),
}
