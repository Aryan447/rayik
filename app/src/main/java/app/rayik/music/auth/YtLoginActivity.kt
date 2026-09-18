package app.rayik.music.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.rayik.music.ui.theme.RayikTheme
import app.rayik.music.ui.theme.spacing
import org.koin.android.ext.android.inject

/**
 * Google sign-in for YouTube sessions (BitChord-style): a real login page
 * in a WebView, from which we harvest the session cookies — never the
 * password, which only Google's page ever sees.
 *
 * - Desktop user-agent: Google's "browser may not be secure" block targets
 *   embedded mobile WebViews; the desktop UA passes it in practice.
 * - Success = SID-family cookies present. The user taps DONE when signed
 *   in; cookies harvest on DONE and on every page finish (cheap, idempotent).
 * - Cookies go straight to [YtSessionStore] (encrypted); nothing sensitive
 *   is logged or displayed.
 */
class YtLoginActivity : ComponentActivity() {
  private val sessions: YtSessionStore by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CookieManager.getInstance().setAcceptCookie(true)
    setContent {
      RayikTheme {
        LoginScreen(
          onDone = { harvestAndFinish(fromButton = true) },
          onHarvest = { harvestAndFinish(fromButton = false) },
        )
      }
    }
  }

  private fun harvestAndFinish(fromButton: Boolean) {
    val raw = CookieManager.getInstance().getCookie(LOGIN_URL).orEmpty() +
      "; " + CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
    val signedIn = sessions.harvest(raw)
    if (signedIn || fromButton) {
      Toast.makeText(
        this,
        if (signedIn) "Signed in — retry your track" else "No session found — try signing in first",
        Toast.LENGTH_SHORT,
      ).show()
      setResult(if (signedIn) Activity.RESULT_OK else Activity.RESULT_CANCELED)
      finish()
    } else {
      Log.i(TAG, "login page settled without session cookies yet")
    }
  }

  companion object {
    const val TAG = "RayikPlayer"
    const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den&passive=true"
    const val DESKTOP_UA =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(onDone: () -> Unit, onHarvest: () -> Unit) {
  var progress by rememberSaveable { mutableIntStateOf(0) }
  Column(Modifier.fillMaxSize()) {
    if (progress in 1..99) {
      LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
    }
    AndroidView(
      modifier = Modifier.weight(1f),
      factory = { context ->
        WebView(context).apply {
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.userAgentString = YtLoginActivity.DESKTOP_UA
          webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
              progress = newProgress
            }
          }
          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
              onHarvest()
            }
          }
          loadUrl(YtLoginActivity.LOGIN_URL)
        }
      },
    )
    Button(
      onClick = onDone,
      modifier = Modifier.padding(MaterialTheme.spacing.medium),
    ) {
      Text("DONE — I'm signed in")
    }
  }
}
