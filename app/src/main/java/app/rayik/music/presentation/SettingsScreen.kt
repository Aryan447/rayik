package app.rayik.music.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.preferences.StreamQuality
import app.rayik.music.preferences.preference.collectAsState
import app.rayik.music.ui.preferences.components.ThemePicker
import app.rayik.music.ui.theme.DarkMode
import app.rayik.music.ui.theme.spacing
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.rayik.music.BuildConfig
import app.rayik.music.R
import app.rayik.music.constants.AudioQuality
import app.rayik.music.constants.AudioQualityKey
import app.rayik.music.utils.dataStore

/**
 * Settings: appearance (theme picker, mode, AMOLED, art-driven colors)
 * and playback (streaming quality). Sane defaults, pro-level control.
 */
@Composable
fun SettingsScreen(
  preferences: AppearancePreferences = rayikPreferences(),
) {
  val appTheme by preferences.appTheme.collectAsState()
  val darkMode by preferences.darkMode.collectAsState()
  val amoledMode by preferences.amoledMode.collectAsState()
  val albumArtDynamic by preferences.albumArtDynamic.collectAsState()
  val streamQuality by preferences.streamQuality.collectAsState()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val systemDark = isSystemInDarkTheme()
  val useDarkTheme = when (darkMode) {
    DarkMode.Dark -> true
    DarkMode.Light -> false
    DarkMode.System -> systemDark
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(MaterialTheme.spacing.medium))

    SectionTitle(stringResource(R.string.rayik_settings_section_appearance))
    ThemePicker(
      currentTheme = appTheme,
      isDarkMode = useDarkTheme,
      onThemeSelected = { preferences.appTheme.set(it) },
    )

    RadioRow(
      label = stringResource(R.string.pref_dark_mode_label),
      options = DarkMode.entries.map { it to stringResource(it.titleRes) },
      selected = darkMode,
      onSelect = { preferences.darkMode.set(it) },
    )
    SwitchRow(
      label = stringResource(R.string.pref_amoled_label),
      checked = amoledMode,
      onChecked = { preferences.amoledMode.set(it) },
    )
    SwitchRow(
      label = stringResource(R.string.pref_album_art_label),
      checked = albumArtDynamic,
      onChecked = { preferences.albumArtDynamic.set(it) },
    )

    Spacer(Modifier.height(MaterialTheme.spacing.medium))
    SectionTitle(stringResource(R.string.settings_section_playback))
    RadioRow(
      label = stringResource(R.string.pref_quality_label),
      options = StreamQuality.entries.map { it to stringResource(it.titleRes) },
      selected = streamQuality,
      onSelect = {
        preferences.streamQuality.set(it)
        // The service reads the legacy AudioQualityKey; mirror the rayik
        // choice there so the toggle takes real effect.
        scope.launch(Dispatchers.IO) {
          context.dataStore.edit { prefs ->
            prefs[AudioQualityKey] = when (it) {
              StreamQuality.Auto -> AudioQuality.AUTO.name
              StreamQuality.High -> AudioQuality.HIGH
              StreamQuality.Saver -> AudioQuality.LOW
            }.toString()
          }
        }
      },
    )

    Spacer(Modifier.height(MaterialTheme.spacing.medium))
    SectionTitle("About")
    Text(
      "rāyik ${BuildConfig.VERSION_NAME} • GPL-3.0-only\n" +
        "Playback core: ArchiveTune (© Rukamori).\n" +
        "Unofficial client — not affiliated with Google/YouTube.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun rayikPreferences(): AppearancePreferences {
  val context = LocalContext.current
  val entryPoint = rememberEntryPoint(context)
  return entryPoint.appearancePreferences()
}

@Composable
private fun rememberEntryPoint(context: android.content.Context): RayikSettingsEntryPoint {
  return androidx.compose.runtime.remember(context) {
    EntryPointAccessors.fromApplication(
      context.applicationContext,
      RayikSettingsEntryPoint::class.java,
    )
  }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface RayikSettingsEntryPoint {
  fun appearancePreferences(): AppearancePreferences
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
  )
}

@Composable
private fun <T> RadioRow(
  label: String,
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small)) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Column(Modifier.selectableGroup()) {
      options.forEach { (value, title) ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .selectable(
              selected = value == selected,
              onClick = { onSelect(value) },
              role = Role.RadioButton,
            )
            .padding(vertical = MaterialTheme.spacing.extraSmall),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          RadioButton(selected = value == selected, onClick = null)
          Text(title, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}

@Composable
private fun SwitchRow(
  label: String,
  checked: Boolean,
  onChecked: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChecked)
  }
}
