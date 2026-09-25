package app.rayik.music.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState as collectFlowAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
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
import app.rayik.music.constants.AccountChannelHandleKey
import app.rayik.music.constants.AccountEmailKey
import app.rayik.music.constants.AccountNameKey
import app.rayik.music.constants.AudioNormalizationKey
import app.rayik.music.constants.AudioOffload
import app.rayik.music.constants.AudioQuality
import app.rayik.music.constants.AudioQualityKey
import app.rayik.music.constants.AutoDownloadOnLikeKey
import app.rayik.music.constants.AutoLoadMoreKey
import app.rayik.music.constants.AutoSkipNextOnErrorKey
import app.rayik.music.constants.CrossfadeEnabledKey
import app.rayik.music.constants.DataSyncIdKey
import app.rayik.music.constants.EnableDiscordRPCKey
import app.rayik.music.constants.EnableLastFMScrobblingKey
import app.rayik.music.constants.HideExplicitKey
import app.rayik.music.constants.InnerTubeCookieKey
import app.rayik.music.constants.ListenBrainzEnabledKey
import app.rayik.music.constants.LowDataModeKey
import app.rayik.music.constants.MaxSongCacheSizeKey
import app.rayik.music.constants.PauseListenHistoryKey
import app.rayik.music.constants.PermanentShuffleKey
import app.rayik.music.constants.PersistentQueueKey
import app.rayik.music.constants.PoTokenGvsKey
import app.rayik.music.constants.PoTokenKey
import app.rayik.music.constants.PoTokenPlayerKey
import app.rayik.music.constants.PreloadNextSongKey
import app.rayik.music.constants.SkipSilenceKey
import app.rayik.music.auth.OAuthSessionManager
import app.rayik.music.innertube.PlaybackAuthState
import app.rayik.music.innertube.YouTube
import app.rayik.music.innertube.utils.hasCompleteYouTubeLoginCookies
import app.rayik.music.storage.StorageCacheClearResult
import app.rayik.music.storage.StorageCacheKind
import app.rayik.music.storage.StorageLocationRepository
import app.rayik.music.utils.clearPlaybackWebAuthSession
import app.rayik.music.utils.dataStore
import app.rayik.music.utils.rememberPreference

/**
 * Settings: profile header, searchable section cards for appearance,
 * playback, audio, offline, privacy, connected accounts, and about.
 * Every row is backed by a real preference — sane defaults, pro control.
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
  val uriHandler = LocalUriHandler.current
  val scope = rememberCoroutineScope()
  val entryPoint = rememberEntryPoint(context)
  var showLogin by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  var clearingCache by rememberSaveable { mutableStateOf(false) }

  var gapless by rememberPreference(PreloadNextSongKey, false)
  var autoplay by rememberPreference(AutoLoadMoreKey, true)
  var restoreQueue by rememberPreference(PersistentQueueKey, true)
  var skipOnError by rememberPreference(AutoSkipNextOnErrorKey, false)
  var keepShuffle by rememberPreference(PermanentShuffleKey, false)
  var normalize by rememberPreference(AudioNormalizationKey, true)
  var skipSilence by rememberPreference(SkipSilenceKey, false)
  var crossfade by rememberPreference(CrossfadeEnabledKey, false)
  var offload by rememberPreference(AudioOffload, false)
  var autoDownloadLiked by rememberPreference(AutoDownloadOnLikeKey, false)
  val songCacheMb by rememberPreference(MaxSongCacheSizeKey, 1024)
  var pauseHistory by rememberPreference(PauseListenHistoryKey, false)
  var hideExplicit by rememberPreference(HideExplicitKey, false)
  var lowData by rememberPreference(LowDataModeKey, false)
  var lastfm by rememberPreference(EnableLastFMScrobblingKey, false)
  var listenBrainz by rememberPreference(ListenBrainzEnabledKey, false)
  var discord by rememberPreference(EnableDiscordRPCKey, true)

  val oAuthSessionManager: OAuthSessionManager = entryPoint.oAuthSessionManager()
  val oAuthSignedIn by oAuthSessionManager.hasSession.collectFlowAsState()
  val accountPrefs by context.dataStore.data.collectFlowAsState(initial = null)
  val sessionCookie = accountPrefs?.get(InnerTubeCookieKey)
  val loggedIn = oAuthSignedIn ||
    sessionCookie?.let { hasCompleteYouTubeLoginCookies(it) } == true
  val accountName = accountPrefs?.get(AccountNameKey).orEmpty()
  val accountEmail = accountPrefs?.get(AccountEmailKey).orEmpty()
  val systemDark = isSystemInDarkTheme()
  val useDarkTheme = when (darkMode) {
    DarkMode.Dark -> true
    DarkMode.Light -> false
    DarkMode.System -> systemDark
  }

  val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)
  val cacheFailedMessage = stringResource(R.string.settings_cache_failed)

  fun matches(vararg keywords: String): Boolean {
    if (query.isBlank()) return true
    return keywords.any { it.contains(query, ignoreCase = true) }
  }

  Box(Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
      Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

      ProfileCard(
        loggedIn = loggedIn,
        accountName = accountName,
        accountEmail = accountEmail,
        onSignIn = { showLogin = true },
        onSignOut = {
          // Wipe the kept WebView session too — otherwise the next
          // sign-in would silently resurrect this same account.
          clearPlaybackWebAuthSession(context)
          scope.launch(Dispatchers.IO) {
            // Revokes the OAuth token server-side when present; no-op otherwise.
            oAuthSessionManager.signOut()
            context.dataStore.edit { prefs ->
              prefs.remove(InnerTubeCookieKey)
              prefs.remove(AccountNameKey)
              prefs.remove(AccountEmailKey)
              prefs.remove(AccountChannelHandleKey)
              prefs.remove(DataSyncIdKey)
              prefs.remove(PoTokenKey)
              prefs.remove(PoTokenGvsKey)
              prefs.remove(PoTokenPlayerKey)
            }
            YouTube.authState = PlaybackAuthState()
          }
        },
      )

      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
      )

      if (matches("appearance", "theme", "gold", "dark", "amoled", "color", "art", "light")) {
        SettingsCard(title = stringResource(R.string.rayik_settings_section_appearance)) {
          ThemePicker(
            currentTheme = appTheme,
            isDarkMode = useDarkTheme,
            onThemeSelected = { preferences.appTheme.set(it) },
          )
          Spacer(Modifier.height(MaterialTheme.spacing.small))
          SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DarkMode.entries.forEachIndexed { index, mode ->
              SegmentedButton(
                selected = darkMode == mode,
                onClick = { preferences.darkMode.set(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, DarkMode.entries.size),
              ) {
                Text(stringResource(mode.titleRes))
              }
            }
          }
          SwitchSetting(
            icon = Icons.Filled.Contrast,
            title = stringResource(R.string.pref_amoled_label),
            subtitle = null,
            checked = amoledMode,
            onChecked = { preferences.amoledMode.set(it) },
          )
          SwitchSetting(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.pref_album_art_label),
            subtitle = null,
            checked = albumArtDynamic,
            onChecked = { preferences.albumArtDynamic.set(it) },
          )
        }
      }

      if (matches("playback", "quality", "streaming", "gapless", "preload", "autoplay", "queue", "restore", "shuffle", "error", "skip")) {
        SettingsCard(title = stringResource(R.string.settings_section_playback)) {
          IconLabel(icon = Icons.Filled.Palette, title = stringResource(R.string.pref_quality_label))
          RadioRow(
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
          SwitchSetting(
            icon = Icons.Filled.Bolt,
            title = stringResource(R.string.settings_gapless_label),
            subtitle = stringResource(R.string.settings_gapless_body),
            checked = gapless,
            onChecked = { gapless = it },
          )
          SwitchSetting(
            icon = Icons.Filled.Repeat,
            title = stringResource(R.string.settings_autoplay_label),
            subtitle = stringResource(R.string.settings_autoplay_body),
            checked = autoplay,
            onChecked = { autoplay = it },
          )
          SwitchSetting(
            icon = Icons.Filled.History,
            title = stringResource(R.string.settings_restore_label),
            subtitle = stringResource(R.string.settings_restore_body),
            checked = restoreQueue,
            onChecked = { restoreQueue = it },
          )
          SwitchSetting(
            icon = Icons.Filled.SkipNext,
            title = stringResource(R.string.settings_skiperror_label),
            subtitle = stringResource(R.string.settings_skiperror_body),
            checked = skipOnError,
            onChecked = { skipOnError = it },
          )
          SwitchSetting(
            icon = Icons.Filled.Shuffle,
            title = stringResource(R.string.settings_keepshuffle_label),
            subtitle = stringResource(R.string.settings_keepshuffle_body),
            checked = keepShuffle,
            onChecked = { keepShuffle = it },
          )
        }
      }

      if (matches("audio", "volume", "normalize", "loudness", "silence", "crossfade", "offload", "battery", "sound")) {
        SettingsCard(title = stringResource(R.string.settings_section_audio)) {
          SwitchSetting(
            icon = Icons.Filled.VolumeUp,
            title = stringResource(R.string.settings_normalize_label),
            subtitle = stringResource(R.string.settings_normalize_body),
            checked = normalize,
            onChecked = { normalize = it },
          )
          SwitchSetting(
            icon = Icons.Filled.VolumeOff,
            title = stringResource(R.string.settings_silence_label),
            subtitle = stringResource(R.string.settings_silence_body),
            checked = skipSilence,
            onChecked = { skipSilence = it },
          )
          SwitchSetting(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.settings_crossfade_label),
            subtitle = stringResource(R.string.settings_crossfade_body),
            checked = crossfade,
            onChecked = { crossfade = it },
          )
          SwitchSetting(
            icon = Icons.Filled.Speed,
            title = stringResource(R.string.settings_offload_label),
            subtitle = stringResource(R.string.settings_offload_body),
            checked = offload,
            onChecked = { offload = it },
          )
        }
      }

      if (matches("offline", "download", "storage", "cache", "data", "pin")) {
        SettingsCard(title = stringResource(R.string.settings_section_offline)) {
          SwitchSetting(
            icon = Icons.Filled.Download,
            title = stringResource(R.string.settings_autodl_label),
            subtitle = stringResource(R.string.settings_autodl_body),
            checked = autoDownloadLiked,
            onChecked = { autoDownloadLiked = it },
          )
          ActionSetting(
            icon = Icons.Filled.Storage,
            title = stringResource(R.string.settings_cache_label),
            subtitle = stringResource(R.string.settings_cache_body, songCacheMb),
            actionLabel = if (clearingCache) {
              stringResource(R.string.settings_cache_clearing)
            } else {
              stringResource(R.string.settings_cache_clear)
            },
            actionEnabled = !clearingCache,
            onAction = {
              scope.launch(Dispatchers.IO) {
                clearingCache = true
                val result = runCatching {
                  entryPoint.storageRepository().clearCache(StorageCacheKind.SONGS) {}
                }.getOrElse { StorageCacheClearResult.Failed }
                clearingCache = false
                Toast.makeText(
                  context,
                  if (result is StorageCacheClearResult.Success) {
                    cacheClearedMessage
                  } else {
                    cacheFailedMessage
                  },
                  Toast.LENGTH_SHORT,
                ).show()
              }
            },
          )
        }
      }

      if (matches("privacy", "history", "explicit", "low-data", "lowdata", "metered", "content")) {
        SettingsCard(title = stringResource(R.string.settings_section_privacy)) {
          SwitchSetting(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.settings_history_label),
            subtitle = stringResource(R.string.settings_history_body),
            checked = pauseHistory,
            onChecked = { pauseHistory = it },
          )
          SwitchSetting(
            icon = Icons.Filled.Block,
            title = stringResource(R.string.settings_explicit_label),
            subtitle = stringResource(R.string.settings_explicit_body),
            checked = hideExplicit,
            onChecked = { hideExplicit = it },
          )
          SwitchSetting(
            icon = Icons.Filled.CloudOff,
            title = stringResource(R.string.settings_lowdata_label),
            subtitle = stringResource(R.string.settings_lowdata_body),
            checked = lowData,
            onChecked = { lowData = it },
          )
        }
      }

      if (matches("connected", "account", "lastfm", "last.fm", "listenbrainz", "discord", "scrobble")) {
        SettingsCard(title = stringResource(R.string.settings_section_connected)) {
          SwitchSetting(
            icon = Icons.Filled.Radio,
            title = stringResource(R.string.settings_lastfm_label),
            subtitle = stringResource(R.string.settings_lastfm_body),
            checked = lastfm,
            onChecked = { lastfm = it },
          )
          SwitchSetting(
            icon = Icons.Filled.LibraryMusic,
            title = stringResource(R.string.settings_listenbrainz_label),
            subtitle = stringResource(R.string.settings_listenbrainz_body),
            checked = listenBrainz,
            onChecked = { listenBrainz = it },
          )
          SwitchSetting(
            icon = Icons.Filled.Chat,
            title = stringResource(R.string.settings_discord_label),
            subtitle = stringResource(R.string.settings_discord_body),
            checked = discord,
            onChecked = { discord = it },
          )
        }
      }

      SettingsCard(title = stringResource(R.string.settings_section_about_app)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.width(MaterialTheme.spacing.medium))
          Text(
            "rāyik ${BuildConfig.VERSION_NAME} • GPL-3.0-only\n" +
              "Playback core: ArchiveTune (© Rukamori).\n" +
              "Unofficial client — not affiliated with Google/YouTube.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
        }
        TextButton(onClick = { uriHandler.openUri("https://github.com/Aryan447/rayik") }) {
          Text(stringResource(R.string.settings_github))
        }
      }
    }
    if (showLogin) {
      Surface(Modifier.fillMaxSize()) {
        LoginScreen(onDone = { showLogin = false })
      }
    }
  }
}

@Composable
private fun ProfileCard(
  loggedIn: Boolean,
  accountName: String,
  accountEmail: String,
  onSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  Surface(
    tonalElevation = 2.dp,
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.large),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        val initial = (accountName.ifBlank { accountEmail }.firstOrNull()?.uppercase()
          ?: "R")
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            initial,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.medium))
        Column(Modifier.weight(1f)) {
          Text(
            when {
              loggedIn && accountName.isNotBlank() -> accountName
              loggedIn -> accountEmail
              else -> stringResource(R.string.settings_profile_guest)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            when {
              loggedIn && accountName.isNotBlank() && accountEmail.isNotBlank() -> accountEmail
              loggedIn -> stringResource(R.string.login_with_google)
              else -> stringResource(R.string.settings_profile_guest_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Spacer(Modifier.height(MaterialTheme.spacing.medium))
      if (loggedIn) {
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.settings_sign_out))
        }
      } else {
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.settings_sign_in))
        }
      }
    }
  }
}

@Composable
private fun SettingsCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Surface(
    tonalElevation = 1.dp,
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.medium),
    ) {
      Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
      )
      content()
    }
  }
}

@Composable
private fun IconLabel(
  icon: ImageVector,
  title: String,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    SettingIcon(icon = icon)
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun SwitchSetting(
  icon: ImageVector,
  title: String,
  subtitle: String?,
  checked: Boolean,
  onChecked: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingIcon(icon = icon)
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (subtitle != null) {
        Text(
          subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
    Switch(checked = checked, onCheckedChange = onChecked)
  }
}

@Composable
private fun ActionSetting(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String,
  actionEnabled: Boolean,
  onAction: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = MaterialTheme.spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingIcon(icon = icon)
    Spacer(Modifier.width(MaterialTheme.spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.width(MaterialTheme.spacing.small))
    TextButton(onClick = onAction, enabled = actionEnabled) {
      Text(actionLabel)
    }
  }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.secondaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.size(20.dp),
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
  fun storageRepository(): StorageLocationRepository
  fun oAuthSessionManager(): OAuthSessionManager
}

@Composable
private fun <T> RadioRow(
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
) {
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
