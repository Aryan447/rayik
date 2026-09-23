package app.rayik.music.di

import android.content.Context
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.preferences.preference.AndroidPreferenceStore
import app.rayik.music.preferences.preference.PreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RayikModule {
  @Singleton
  @Provides
  fun providePreferenceStore(@ApplicationContext context: Context): PreferenceStore =
    AndroidPreferenceStore(context)

  @Singleton
  @Provides
  fun provideAppearancePreferences(store: PreferenceStore): AppearancePreferences =
    AppearancePreferences(store)
}
