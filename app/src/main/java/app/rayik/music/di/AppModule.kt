package app.rayik.music.di

import app.rayik.music.BuildConfig
import app.rayik.music.auth.YtAuthInterceptor
import app.rayik.music.auth.YtSessionStore
import app.rayik.music.player.PlayerViewModel
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.preferences.preference.AndroidPreferenceStore
import app.rayik.music.preferences.preference.PreferenceStore
import app.rayik.music.presentation.SearchViewModel
import app.rayik.music.streaming.StreamSource
import app.rayik.music.streaming.lyrics.LrclibLyrics
import app.rayik.music.streaming.piped.PipedSource
import app.rayik.music.streaming.youtube.InnerTubeResolver
import app.rayik.music.streaming.youtube.PoTokenProvider
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
  // YouTube session: encrypted cookie jar for optional Google sign-in.
  // Signed-in traffic gets identity-based trust from YouTube's edge, which
  // is what dodges anonymous 403-class failures. Signed out, every request
  // passes through untouched (today's behavior, byte for byte).
  single { YtSessionStore(androidContext()) }
  // Shared upstream for the player cache datasource and the streaming layer.
  single {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .dns(app.rayik.music.streaming.youtube.RayikDns)
      .addInterceptor(app.rayik.music.streaming.youtube.RayikDns.interceptor)
      .cookieJar(get<YtSessionStore>())
      .addInterceptor(YtAuthInterceptor(get()))
      .build()
  }
  // GVS proof-of-origin tokens for the enforcement era: minted on demand
  // inside YouTube's own web player (hidden WebView) after a full 403
  // cascade, never eagerly. Absent until first use — tokenless installs
  // behave exactly as before.
  single { PoTokenProvider(androidContext()) }
  viewModelOf(::PlayerViewModel)
  // InnerTube key comes from BuildConfig (env / -P / local.properties, never
  // hardcoded) — blank degrades honestly to the Piped fallback path.
  single { InnerTubeResolver(get(), BuildConfig.INNERTUBE_API_KEY, get()) }
  single<StreamSource> { PipedSource(get(), get(), get()) }
  single { LrclibLyrics(get()) }
  viewModelOf(::SearchViewModel)
}
