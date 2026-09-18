package app.rayik.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.rayik.music.presentation.RayikNav
import app.rayik.music.ui.theme.RayikTheme

class MainActivity : ComponentActivity() {
  private val notificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestNotificationPermission()
    enableEdgeToEdge()
    setContent {
      RayikTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          RayikNav()
        }
      }
    }
  }

  /** Player notifications need this on API 33+; playback itself works without it. */
  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}
