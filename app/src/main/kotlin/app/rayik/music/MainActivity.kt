/*
 * rāyik (2026) | GPL-3.0-only
 *
 * Nav host replacing the upstream UI shell. Service binding keeps the
 * proven pattern: bind MusicService, publish PlayerConnection via
 * holder + composition local, dispose on unbind.
 */

package app.rayik.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.rayik.music.preferences.AppearancePreferences
import app.rayik.music.presentation.RayikNav
import app.rayik.music.ui.theme.RayikTheme
import dagger.hilt.android.AndroidEntryPoint
import app.rayik.music.db.MusicDatabase
import app.rayik.music.playback.MusicService
import app.rayik.music.playback.PlayerConnection
import app.rayik.music.playback.PlayerConnectionHolder
import javax.inject.Inject

val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var playerConnectionHolder: PlayerConnectionHolder

    @Inject
    lateinit var appearancePreferences: AppearancePreferences

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service is MusicService.MusicBinder) {
                    val conn = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playerConnection = conn
                    playerConnectionHolder.connection.value = conn
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection?.dispose()
                playerConnection = null
                playerConnectionHolder.connection.value = null
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            RayikTheme(preferences = appearancePreferences) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RayikShell(connection = playerConnection)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onStop() {
        super.onStop()
        runCatching { unbindService(serviceConnection) }
        playerConnection?.dispose()
        playerConnection = null
        playerConnectionHolder.connection.value = null
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

@Composable
private fun RayikShell(connection: PlayerConnection?) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPlayerConnection provides connection,
    ) {
        RayikNav()
    }
}
