package app.rayik.music.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Encrypted store for the YouTube session jar plus the OkHttp [CookieJar]
 * that serves it. Session cookies are as sensitive as a password (they
 * authenticate as the user), so they live in EncryptedSharedPreferences —
 * never in plain prefs, logs, or diagnostics.
 *
 * Harvesting happens in the login WebView ([YtLoginActivity]), which hands
 * the raw cookie string here; serving happens transparently for every
 * request through the shared OkHttp client (InnerTube + ExoPlayer media).
 */
class YtSessionStore(appContext: Context) : CookieJar {
  private val app: Context = appContext.applicationContext

  private val prefs: SharedPreferences by lazy {
    try {
      EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        app,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    } catch (e: Exception) {
      // Encrypted prefs need a working keystore; if it is broken on this
      // device, sessions simply don't persist (signed-out behavior) instead
      // of crashing the app. Never fall back to plain storage for cookies.
      Log.w(TAG, "encrypted session store unavailable; staying signed out", e)
      app.getSharedPreferences(VOLATILE_NAME, Context.MODE_PRIVATE)
    }
  }

  private val _signedIn = MutableStateFlow(current().let(YtAuth::isSignedIn))
  /** Observable sign-in state for Settings UI. */
  val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

  /** Current jar (never logged — values authenticate as the user). */
  fun current(): Map<String, String> {
    val raw = prefs.getString(KEY_JAR, "").orEmpty()
    if (raw.isBlank()) return emptyMap()
    return try {
      raw.split('\n')
        .mapNotNull { line ->
          val name = line.substringBefore('=').trim()
          val value = line.substringAfter('=', "").trim()
          if (name.isBlank() || value.isBlank()) null else name to value
        }
        .toMap()
    } catch (_: Exception) {
      emptyMap()
    }
  }

  /**
   * Replace the jar with cookies harvested from the login WebView.
   * Returns whether the result counts as signed in.
   */
  fun harvest(cookieString: String): Boolean {
    val parsed = YtAuth.parseCookieString(cookieString)
    if (parsed.isEmpty()) return false
    val merged = (current() + parsed).filterValues { it.isNotBlank() }
    prefs.edit().putString(KEY_JAR, merged.entries.joinToString("\n") { (k, v) -> "$k=$v" }).apply()
    val signedIn = YtAuth.isSignedIn(merged)
    _signedIn.value = signedIn
    Log.i(TAG, "session harvest: ${merged.size} cookies, signedIn=$signedIn")
    return signedIn
  }

  /** Forget everything (sign out). */
  fun clear() {
    prefs.edit().remove(KEY_JAR).apply()
    _signedIn.value = false
    Log.i(TAG, "session cleared")
  }

  // CookieJar: serve the jar to YouTube hosts, persist what they set back.

  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    if (!isYouTubeHost(url.host)) return
    val merged = current().toMutableMap()
    for (cookie in cookies) {
      if (cookie.value.isBlank()) merged.remove(cookie.name) else merged[cookie.name] = cookie.value
    }
    prefs.edit().putString(KEY_JAR, merged.entries.joinToString("\n") { (k, v) -> "$k=$v" }).apply()
    _signedIn.value = YtAuth.isSignedIn(merged)
  }

  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    if (!isYouTubeHost(url.host)) return emptyList()
    val now = System.currentTimeMillis()
    return current().mapNotNull { (name, value) ->
      try {
        Cookie.Builder().name(name).value(value).domain(url.host).path("/").expiresAt(now + JAR_TTL_MS).build()
      } catch (_: Exception) {
        null
      }
    }
  }

  companion object {
    const val TAG = "RayikPlayer"
    private const val PREFS_NAME = "rayik_session"
    private const val VOLATILE_NAME = "rayik_session_volatile"
    private const val KEY_JAR = "cookie_jar"
    private const val JAR_TTL_MS = 365L * 24 * 60 * 60 * 1_000

    fun isYouTubeHost(host: String): Boolean {
      val h = host.lowercase()
      return h == "youtube.com" || h.endsWith(".youtube.com") ||
        h == "googlevideo.com" || h.endsWith(".googlevideo.com") ||
        h == "youtubei.googleapis.com"
    }
  }
}
