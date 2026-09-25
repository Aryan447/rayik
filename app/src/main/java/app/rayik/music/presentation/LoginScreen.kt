package app.rayik.music.presentation

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rayik.music.R
import app.rayik.music.innertube.utils.hasCompleteYouTubeLoginCookies
import app.rayik.music.ui.theme.spacing
import app.rayik.music.utils.resetAuthWebViewSession

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val YOUTUBE_MUSIC_PACKAGE_NAME = "com.google.android.apps.youtube.music"
private const val LOGIN_CONTEXT_RETRY_DELAY_MS = 1_000L
private const val LOGIN_CONTEXT_EXTRACTION_ATTEMPTS = 10

private const val LOGIN_CONTEXT_SCRIPT =
  "(function(){try{var c=window.ytcfg;var y=window.yt&&window.yt.config_;var s=document.querySelectorAll('script');var v=c&&c.get&&c.get('VISITOR_DATA')||y&&y.VISITOR_DATA;var d=c&&c.get&&c.get('DATASYNC_ID')||y&&y.DATASYNC_ID;for(var i=0;i<s.length&&(!v||!d);i++){var x=s[i].textContent;if(!v){var vm=x.match(/[\"']VISITOR_DATA[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(vm)v=vm[1]}if(!d){var dm=x.match(/[\"'](?:DATASYNC_ID|dataSyncId)[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(dm)d=dm[1]}}if(v)Android.onRetrieveVisitorData(v);if(d)Android.onRetrieveDataSyncId(d)}catch(e){}})();"

private val YOUTUBE_COOKIE_URLS =
  listOf(
    "https://music.youtube.com",
    "https://www.youtube.com",
    "https://youtube.com",
  )

/**
 * Sign-in, password-free by default: Google shows a short code, the user
 * taps Allow once on google.com/device (no ID/password typing — the
 * browser is already signed in), and the app polls until tokens land.
 * The legacy WebView email/password flow stays behind the
 * "use password instead" fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
  onDone: () -> Unit,
  deviceFlowViewModel: DeviceFlowViewModel = hiltViewModel(),
  passwordViewModel: LoginViewModel = hiltViewModel(),
) {
  var usePassword by rememberSaveable { mutableStateOf(false) }

  if (!usePassword) {
    DeviceFlowContent(
      onDone = onDone,
      onUsePassword = { usePassword = true },
      viewModel = deviceFlowViewModel,
    )
  } else {
    PasswordLoginContent(
      onDone = onDone,
      onBackToOptions = { usePassword = false },
      viewModel = passwordViewModel,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceFlowContent(
  onDone: () -> Unit,
  onUsePassword: () -> Unit,
  viewModel: DeviceFlowViewModel,
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsStateWithLifecycle()
  val loginSuccessMessage = stringResource(R.string.login_success)

  LaunchedEffect(Unit) {
    viewModel.start()
  }
  DisposableEffect(Unit) {
    onDispose { viewModel.cancel() }
  }
  LaunchedEffect(state) {
    if (state is DeviceFlowState.Success) {
      Toast.makeText(context, loginSuccessMessage, Toast.LENGTH_SHORT).show()
      onDone()
    }
  }

  Column(Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text(stringResource(R.string.login)) },
      navigationIcon = {
        IconButton(onClick = onDone) {
          Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
        }
      },
    )
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(MaterialTheme.spacing.large),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      when (val current = state) {
        DeviceFlowState.Idle,
        DeviceFlowState.RequestingCode,
        -> {
          CircularProgressIndicator()
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          Text(
            stringResource(R.string.deviceflow_requesting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
        is DeviceFlowState.AwaitingApproval -> {
          Text(
            stringResource(R.string.deviceflow_code_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          Text(
            current.userCode,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          Button(
            onClick = { openVerificationPage(context, current.verificationUrl) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(R.string.deviceflow_continue))
          }
          TextButton(onClick = { copyCode(context, current.userCode) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null)
            Text(stringResource(R.string.deviceflow_copy))
          }
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          LinearProgressIndicator(Modifier.fillMaxWidth())
          Spacer(Modifier.height(MaterialTheme.spacing.small))
          Text(
            stringResource(R.string.deviceflow_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(MaterialTheme.spacing.large))
          TextButton(onClick = onUsePassword) {
            Text(stringResource(R.string.deviceflow_use_password))
          }
        }
        DeviceFlowState.Completing -> {
          CircularProgressIndicator()
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          Text(
            stringResource(R.string.deviceflow_completing),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
        is DeviceFlowState.Success -> {
          CircularProgressIndicator()
        }
        is DeviceFlowState.Error -> {
          Text(
            current.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(MaterialTheme.spacing.medium))
          if (current.canRetry) {
            Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
              Text(stringResource(R.string.deviceflow_retry))
            }
          }
          TextButton(onClick = onUsePassword) {
            Text(stringResource(R.string.deviceflow_use_password))
          }
        }
      }
    }
  }

  BackHandler {
    onDone()
  }
}

private fun openVerificationPage(context: Context, url: String) {
  val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
  // Custom Tab keeps the browser session (already signed in → just Allow);
  // plain VIEW intent is the fallback when no browser handles Custom Tabs.
  runCatching {
    CustomTabsIntent.Builder().build().launchUrl(context, uri)
  }.onFailure {
    runCatching {
      context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
  }
}

private fun copyCode(context: Context, code: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("rayik sign-in code", code))
  Toast.makeText(context, context.getString(R.string.deviceflow_copied), Toast.LENGTH_SHORT).show()
}

/**
 * Legacy Google sign-in for the YouTube session, hosted in a WebView.
 * Kept as the password fallback: cookies + visitor data + datasync id are
 * harvested on YouTube pages and completed through [LoginViewModel].
 *
 * The web session is kept between visits (cookies are NOT wiped on open):
 * returning users land on Google's account chooser and finish with one tap.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordLoginContent(
  onDone: () -> Unit,
  onBackToOptions: () -> Unit,
  viewModel: LoginViewModel,
) {
  val context = LocalContext.current
  val screenState by viewModel.screenState.collectAsStateWithLifecycle()
  val loginSuccessMessage = stringResource(R.string.login_success)
  var webView: WebView? = null

  LaunchedEffect(screenState) {
    if (screenState is LoginScreenState.Success) {
      Toast.makeText(context, loginSuccessMessage, Toast.LENGTH_SHORT).show()
      onDone()
    }
  }

  Column(Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text(stringResource(R.string.login)) },
      navigationIcon = {
        IconButton(onClick = onBackToOptions) {
          Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
        }
      },
      actions = {
        IconButton(
          onClick = {
            // Forget the kept web session and start over (different account).
            val current = webView ?: return@IconButton
            resetAuthWebViewSession(context, current, clearCookies = true) {
              current.loadUrl(DEFAULT_LOGIN_URL)
            }
          },
        ) {
          Icon(
            painterResource(R.drawable.logout),
            contentDescription = stringResource(R.string.login_switch_account),
          )
        }
      },
    )
    if (screenState is LoginScreenState.Empty) {
      Text(
        text = stringResource(R.string.login_returning_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
          horizontal = MaterialTheme.spacing.medium,
          vertical = MaterialTheme.spacing.small,
        ),
      )
    }
    when (val state = screenState) {
      is LoginScreenState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
      is LoginScreenState.Error ->
        Text(
          text =
            if (state.missingDataSyncId) {
              stringResource(R.string.login_missing_datasync)
            } else {
              stringResource(R.string.login_failed)
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        )
      else -> Unit
    }
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { factoryContext ->
        WebView(factoryContext).apply {
          val cookieManager = CookieManager.getInstance()
          webViewClient =
            YouTubeLoginWebViewClient(
              cookieManager = cookieManager,
              onCookiesCaptured = viewModel::onCookiesCaptured,
            )
          settings.apply {
            javaScriptEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
          }
          val loginWebView = this
          addJavascriptInterface(
            object {
              @JavascriptInterface
              fun onRetrieveVisitorData(newVisitorData: String?) {
                loginWebView.post {
                  viewModel.onVisitorDataExtracted(newVisitorData)
                }
              }

              @JavascriptInterface
              fun onRetrieveDataSyncId(newDataSyncId: String?) {
                loginWebView.post {
                  viewModel.onDataSyncIdExtracted(newDataSyncId)
                }
              }
            },
            "Android",
          )
          webView = this
          // Keep the cookie jar: returning users get Google's account
          // chooser (one tap) instead of a fresh email + password login.
          resetAuthWebViewSession(factoryContext, this, clearCookies = false) {
            loadUrl(DEFAULT_LOGIN_URL)
          }
        }
      },
      onRelease = { releasedWebView ->
        (releasedWebView.webViewClient as? YouTubeLoginWebViewClient)?.release(releasedWebView)
        releasedWebView.removeJavascriptInterface("Android")
        releasedWebView.stopLoading()
        releasedWebView.destroy()
        if (webView === releasedWebView) {
          webView = null
        }
      },
    )
  }

  BackHandler(enabled = webView?.canGoBack() == true) {
    if (webView?.canGoBack() == true) {
      webView?.goBack()
    } else {
      onBackToOptions()
    }
  }
}

private class YouTubeLoginWebViewClient(
  private val cookieManager: CookieManager,
  private val onCookiesCaptured: (String) -> Unit,
) : WebViewClient() {
  private var extractionRunnable: Runnable? = null
  private var lastCapturedCookie: String? = null

  override fun shouldOverrideUrlLoading(
    view: WebView,
    request: WebResourceRequest,
  ): Boolean {
    val targetUrl = request.url.toString()
    if (!request.isForMainFrame && !targetUrl.isWebViewLoadableUrl()) return true
    return handleUrlLoading(view, targetUrl)
  }

  @Deprecated("Deprecated in Java")
  override fun shouldOverrideUrlLoading(
    view: WebView,
    url: String?,
  ): Boolean = handleUrlLoading(view, url)

  override fun onPageFinished(
    view: WebView,
    url: String?,
  ) {
    super.onPageFinished(view, url)
    scheduleLoginContextExtraction(view, url)
  }

  override fun doUpdateVisitedHistory(
    view: WebView,
    url: String?,
    isReload: Boolean,
  ) {
    super.doUpdateVisitedHistory(view, url, isReload)
    scheduleLoginContextExtraction(view, url)
  }

  fun release(view: WebView) {
    extractionRunnable?.let(view::removeCallbacks)
    extractionRunnable = null
  }

  private fun scheduleLoginContextExtraction(
    view: WebView,
    url: String?,
  ) {
    extractionRunnable?.let(view::removeCallbacks)
    extractionRunnable = null

    if (!url.isYouTubeUrl()) return

    var remainingAttempts = LOGIN_CONTEXT_EXTRACTION_ATTEMPTS
    val runnable =
      object : Runnable {
        override fun run() {
          if (!view.url.isYouTubeUrl()) return

          view.evaluateJavascript(LOGIN_CONTEXT_SCRIPT, null)
          captureCookies(view.url)

          remainingAttempts -= 1
          if (remainingAttempts > 0) {
            view.postDelayed(this, LOGIN_CONTEXT_RETRY_DELAY_MS)
          }
        }
      }
    extractionRunnable = runnable
    view.post(runnable)
  }

  private fun captureCookies(currentUrl: String?) {
    val mergedCookie = mergeYouTubeCookies(cookieManager, currentUrl) ?: return
    if (!hasCompleteYouTubeLoginCookies(mergedCookie) || mergedCookie == lastCapturedCookie) return

    lastCapturedCookie = mergedCookie
    onCookiesCaptured(mergedCookie)
  }

  private fun handleUrlLoading(
    view: WebView,
    url: String?,
  ): Boolean {
    val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
    if (targetUrl.isYouTubeMusicStoreListingUrl()) return true
    if (targetUrl.isWebViewLoadableUrl()) return false

    targetUrl.intentWebViewUrl()?.let(view::loadUrl)
    return true
  }
}

private fun String.isWebViewLoadableUrl(): Boolean {
  val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
  return scheme == "http" ||
    scheme == "https" ||
    scheme == "javascript" ||
    scheme == "data" ||
    scheme == "blob"
}

private fun String.intentWebViewUrl(): String? {
  val parsedIntent =
    runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }.getOrNull()
  return sequenceOf(
    parsedIntent?.dataString,
    intentUriAsHttpsUrl(),
    parsedIntent
      ?.getStringExtra("browser_fallback_url")
      ?.takeUnless(String::isYouTubeMusicStoreListingUrl),
  ).firstOrNull { it?.isHttpUrl() == true }
}

private fun String.intentUriAsHttpsUrl(): String? {
  val intentUri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
  if (!intentUri.scheme.equals("intent", ignoreCase = true)) return null

  val authority = intentUri.encodedAuthority ?: return null
  val path = intentUri.encodedPath.orEmpty()
  val query = intentUri.encodedQuery?.let { "?$it" }.orEmpty()
  return "https://$authority$path$query"
}

private fun String.isHttpUrl(): Boolean {
  val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
  return scheme == "http" || scheme == "https"
}

private fun String.isYouTubeMusicStoreListingUrl(): Boolean {
  val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
  return uri.host.equals("play.google.com", ignoreCase = true) &&
    uri.path.equals("/store/apps/details", ignoreCase = true) &&
    uri.getQueryParameter("id") == YOUTUBE_MUSIC_PACKAGE_NAME
}

private fun String?.isYouTubeUrl(): Boolean {
  val host = this?.let(Uri::parse)?.host?.lowercase() ?: return false
  return host == "youtube.com" || host.endsWith(".youtube.com")
}

private fun mergeYouTubeCookies(
  cookieManager: CookieManager,
  currentUrl: String? = null,
): String? {
  val cookieParts = linkedMapOf<String, String>()
  val candidateUrls = linkedSetOf<String>()

  currentUrl.toYouTubeCookieOrigin()?.let(candidateUrls::add)
  candidateUrls.addAll(YOUTUBE_COOKIE_URLS)

  cookieManager.flush()

  candidateUrls.forEach { url ->
    cookieManager
      .getCookie(url)
      ?.split(";")
      ?.map(String::trim)
      ?.filter(String::isNotBlank)
      ?.forEach { part ->
        val separatorIndex = part.indexOf('=')
        if (separatorIndex <= 0) return@forEach

        val key = part.substring(0, separatorIndex).trim()
        val value = part.substring(separatorIndex + 1).trim()
        if (key.isNotEmpty() && value.isNotEmpty()) {
          cookieParts.putIfAbsent(key, value)
        }
      }
  }

  return cookieParts
    .takeIf { it.isNotEmpty() }
    ?.entries
    ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

private fun String?.toYouTubeCookieOrigin(): String? {
  val parsed = this?.let(Uri::parse) ?: return null
  val host = parsed.host?.lowercase() ?: return null
  if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

  val scheme =
    parsed.scheme
      ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
      ?: "https"

  return "$scheme://$host"
}
