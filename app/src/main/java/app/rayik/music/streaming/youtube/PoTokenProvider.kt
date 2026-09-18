package app.rayik.music.streaming.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * A minted proof-of-origin token bound to one video. Tokens expire, so the
 * mint timestamp travels along and [PoTokenProvider] re-mints past [POT_TTL_MS].
 */
data class PoToken(
  val value: String,
  /** Session identity the token was minted under (best-effort, may be blank). */
  val visitorData: String = "",
  val mintedAtMs: Long = System.currentTimeMillis(),
)

/**
 * GVS proof-of-origin tokens, minted the way every working 2026 client
 * mints them: inside YouTube's own web player. BotGuard attestation is not
 * reimplemented — a hidden WebView loads the muted embed for the failing
 * video, the player's own `videoplayback` request fires carrying `&pot=`,
 * and [WebViewClient.shouldInterceptRequest] captures it off the URL
 * (request bodies are invisible there, but GVS URLs carry the token in the
 * query). The token is then appended to our stream URLs and sent back as
 * `serviceIntegrityDimensions.poToken`, with the mint session's visitor
 * identity alongside — URL, visitor header, and token stay coherent.
 *
 * Behavior contract:
 * - Tokenless installs behave exactly as before: [cachedFor] returns null
 *   and resolution skips the token pass.
 * - Minting only ever happens after a full 403 cascade (never on transport
 *   errors — a dead network can't mint), and is single-flight per video.
 * - Every failure path returns null instead of throwing: callers fall back
 *   to honest "refused playback" errors, never a crash. WebView creation
 *   itself is guarded (broken WebView implementations must not take down
 *   playback).
 */
class PoTokenProvider(
  appContext: Context,
) {
  private val app: Context = appContext.applicationContext
  private val scope = MainScope()
  private val mutex = Mutex()
  private val cache = mutableMapOf<String, PoToken>()
  private var mintJob: Job? = null

  private val _state = MutableStateFlow<Map<String, PoToken>>(emptyMap())
  /** Observable mint state (mostly for Settings/diagnostics). */
  val state: StateFlow<Map<String, PoToken>> = _state.asStateFlow()

  /** Fresh cached token for [videoId], or null (miss, expired, or tokenless). */
  fun cachedFor(videoId: String): PoToken? {
    val token = synchronized(cache) { cache[videoId] } ?: return null
    return if (isFresh(token.mintedAtMs, System.currentTimeMillis())) token else null
  }

  /**
   * Mint a token for [videoId], single-flight: concurrent callers share the
   * in-flight mint. Suspends (WebView work hops to Main internally);
   * returns null on any failure. Throws [CancellationException] through.
   */
  suspend fun tokenFor(videoId: String): PoToken? {
    if (videoId.isBlank()) return null
    cachedFor(videoId)?.let { return it }
    val job = mutex.withLock {
      cachedFor(videoId)?.let { return it }
      (mintJob?.takeIf { it.isActive }) ?: scope.launch(Dispatchers.Main) {
        mintAndStore(videoId)
      }.also { mintJob = it }
    }
    try {
      job.join()
    } catch (e: CancellationException) {
      throw e
    }
    return cachedFor(videoId)
  }

  private suspend fun mintAndStore(videoId: String) {
    val minted = try {
      withContext(Dispatchers.Main.immediate) { mintOnMain(videoId) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "PO-token mint failed for $videoId: ${e.message}")
      null
    }
    if (minted != null) {
      synchronized(cache) {
        // One fresh token per video; drop expired siblings to bound memory.
        cache.entries.removeAll { !isFresh(it.value.mintedAtMs, System.currentTimeMillis()) }
        cache[videoId] = minted
      }
      _state.value = synchronized(cache) { cache.toMap() }
      Log.i(TAG, "PO-token minted for $videoId (visitorData=${if (minted.visitorData.isBlank()) "none" else "bound"})")
    }
  }

  /**
   * Must run on the main thread (WebView). Loads the muted embed — never
   * attached to a window, never audible — and captures `pot` off the
   * player's own media request. Null on timeout or any failure.
   */
  private suspend fun mintOnMain(videoId: String): PoToken? {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      Log.w(TAG, "PO-token mint off main thread; skipping")
      return null
    }
    return try {
      withTimeout(MINT_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
          val webView = try {
            WebView(app)
          } catch (e: Exception) {
            Log.w(TAG, "WebView unavailable for PO-token mint: ${e.message}")
            if (!cont.isCompleted) cont.resume(null)
            return@suspendCancellableCoroutine
          }
          fun finish(token: PoToken?) {
            if (!cont.isCompleted) cont.resume(token)
            try {
              webView.stopLoading()
              webView.destroy()
            } catch (_: Exception) {
              // Destroy must never throw back into the mint.
            }
          }
          cont.invokeOnCancellation {
            try {
              webView.stopLoading()
              webView.destroy()
            } catch (_: Exception) {
            }
          }
          webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
              val pot = request.url.getQueryParameter("pot")
              if (!pot.isNullOrBlank() && !cont.isCompleted) {
                finish(PoToken(value = pot, visitorData = visitorDataFromCookies()))
              }
              return null // never intercept: the player must load untouched.
            }

            override fun onPageFinished(view: WebView, url: String) {
              // Autoplay is muted (embed `mute=1` + belt-and-braces here) so
              // no gesture is needed for the media request to fire.
              view.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v){v.muted=true;try{v.play();}catch(e){}}})();",
                null,
              )
            }
          }
          @SuppressLint("SetJavaScriptEnabled")
          webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
          }
          webView.loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&playsinline=1")
        }
      }
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "PO-token mint timed out for $videoId")
      null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "PO-token mint failed for $videoId: ${e.message}")
      null
    }
  }

  /**
   * Best-effort visitor identity for the mint session, read from YouTube's
   * own cookie jar. [CookieManager] never throws here — any failure yields
   * blank (unbound), and callers treat blank as "send nothing".
   */
  private fun visitorDataFromCookies(): String {
    return try {
      val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
      cookies.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("$VISITOR_COOKIE=") }
        ?.substringAfter('=').orEmpty().trim()
    } catch (_: Exception) {
      ""
    }
  }

  companion object {
    const val TAG = "RayikPlayer"
    /** Tokens expire; past this the cache re-mints on the next 403 cascade. */
    const val POT_TTL_MS = 6 * 60 * 60 * 1_000L
    /** BotGuard attestation takes seconds on first run; longer than the resolve budget. */
    const val MINT_TIMEOUT_MS = 45_000L
    private const val VISITOR_COOKIE = "VISITOR_INFO1_LIVE"

    /** Pure freshness rule so unit tests cover expiry without a WebView. */
    fun isFresh(mintedAtMs: Long, nowMs: Long): Boolean =
      mintedAtMs > 0 && nowMs >= mintedAtMs && nowMs - mintedAtMs < POT_TTL_MS
  }
}
