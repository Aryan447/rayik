package app.rayik.music.di

import app.rayik.music.BuildConfig
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.preferences.preference.AndroidPreferenceStore
import app.rayik.music.preferences.preference.PreferenceStore
import app.rayik.music.presentation.SearchViewModel
import app.rayik.music.streaming.StreamSource
import app.rayik.music.streaming.lyrics.LrclibLyrics
import app.rayik.music.streaming.piped.PipedSource
import app.rayik.music.streaming.youtube.InnerTubeResolver
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
  // Explicit lambdas, not singleOf(::...): AndroidPreferenceStore has a
  // defaulted SharedPreferences parameter and Koin fills every constructor
  // parameter instead of using defaults — singleOf would crash here with
  // NoBeanDefFoundException for SharedPreferences on first inject.
  single<PreferenceStore> { AndroidPreferenceStore(androidContext()) }
  single { AppearancePreferences(get()) }
  // Shared upstream for the player cache datasource and the streaming layer.
  single {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
  }
  viewModelOf(::PlayerViewModel)
  // InnerTube key comes from BuildConfig (env / -P / local.properties, never
  // hardcoded) — blank degrades honestly to the Piped fallback path.
  single { InnerTubeResolver(get(), BuildConfig.INNERTUBE_API_KEY) }
  single<StreamSource> { PipedSource(get(), get(), get()) }
  single { LrclibLyrics(get()) }
  viewModelOf(::SearchViewModel)
}
