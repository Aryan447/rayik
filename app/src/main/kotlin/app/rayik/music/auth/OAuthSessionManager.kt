package app.rayik.music.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.rayik.music.innertube.auth.OAuthTokens
import app.rayik.music.innertube.auth.TvOAuthClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val OAUTH_PREFS_NAME = "oauth_tokens"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val BACKGROUND_REFRESH_THROTTLE_MS = 60_000L

/**
 * Owns the TV device-flow session: in-memory access token, encrypted
 * refresh token, proactive refresh, and best-effort revoke on sign-out.
 *
 * [validAccessToken] is synchronous on purpose — InnerTube's header builder
 * is not suspending. It serves the cached token while fresh; when stale it
 * kicks off a background refresh (throttled) and returns null once, so that
 * single request goes out anonymous instead of blocking the player.
 */
@Singleton
class OAuthSessionManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = TvOAuthClient()

    @Volatile
    private var cached: OAuthTokens? = null
    private val refreshMutex = Mutex()

    @Volatile
    private var lastBackgroundRefreshAttemptMs = 0L

    private val _hasSession = MutableStateFlow(false)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    init {
        // Disk + keystore reads stay off the main thread; UI collects the flow.
        scope.launch {
            val stored = loadRefreshToken()
            _hasSession.value = stored != null
            if (stored != null) refreshIfNeeded()
        }
    }

    fun validAccessToken(): String? {
        val current = cached
        if (current != null && !current.isExpired()) return current.accessToken
        val now = System.currentTimeMillis()
        if (current?.refreshToken != null && now - lastBackgroundRefreshAttemptMs > BACKGROUND_REFRESH_THROTTLE_MS) {
            lastBackgroundRefreshAttemptMs = now
            scope.launch { refreshIfNeeded() }
        }
        return null
    }

    suspend fun store(tokens: OAuthTokens) {
        withContext(Dispatchers.IO) {
            persistRefreshToken(tokens.refreshToken)
            cached = tokens
            _hasSession.value = true
        }
    }

    /**
     * Ensures a usable access token, refreshing when stale. Returns true
     * when [validAccessToken] will now yield a token.
     */
    suspend fun refreshIfNeeded(): Boolean {
        val current = cached
        if (current != null && !current.isExpired()) return true
        val refreshToken = current?.refreshToken ?: loadRefreshToken() ?: return false
        return refreshMutex.withLock {
            val fresh = cached
            if (fresh != null && !fresh.isExpired()) return true
            try {
                val renewed = client.refresh(refreshToken)
                cached = renewed
                persistRefreshToken(renewed.refreshToken)
                _hasSession.value = true
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "OAuth background refresh failed")
                // Dead refresh token (revoked password change): drop the session
                // so the UI falls back to signed-out instead of retrying forever.
                if (e is app.rayik.music.innertube.auth.OAuthException.Transient &&
                    e.message?.contains("invalid_grant", ignoreCase = true) == true
                ) {
                    clearStored()
                    cached = null
                    _hasSession.value = false
                }
                false
            }
        }
    }

    suspend fun signOut() {
        val token = withContext(Dispatchers.IO) {
            val stored = cached?.refreshToken ?: loadRefreshToken()
            cached = null
            clearStored()
            _hasSession.value = false
            stored
        }
        // Best-effort server-side revoke; never throws, never blocks sign-out.
        if (token != null) client.revoke(token)
    }

    private fun prefs(): SharedPreferences {
        // Cached per call-site via lazy holder below; creation touches disk +
        // keystore, so callers must stay on Dispatchers.IO (all of them do).
        return prefsHolder
    }

    private val prefsHolder: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                OAUTH_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Timber.w(e, "Encrypted prefs unavailable — OAuth token stored in plain prefs")
            context.getSharedPreferences("${OAUTH_PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    private fun loadRefreshToken(): String? =
        prefs().getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun persistRefreshToken(token: String) {
        prefs().edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    private fun clearStored() {
        prefs().edit().remove(KEY_REFRESH_TOKEN).apply()
    }
}
