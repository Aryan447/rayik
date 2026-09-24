package app.rayik.music.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.rayik.music.ui.theme.spacing

/** Copies diagnostics for a bug report — no adb needed on the reporter's side. */
fun copyDiagnostics(context: Context, details: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("rayik diagnostics", details))
  Toast.makeText(context, "Details copied — paste them into your report", Toast.LENGTH_SHORT).show()
}

/** Every streaming screen must handle loading / unavailable / retry — never blank or crash. */
sealed interface ScreenState {
  data object Loading : ScreenState
  data class Unavailable(val reason: String) : ScreenState
  data object Ready : ScreenState
}

@Composable
fun ScreenScaffold(
  state: ScreenState,
  loadingText: String,
  onRetry: () -> Unit,
  /** Optional secondary action under Try again (e.g. Copy details). */
  secondaryLabel: String? = null,
  onSecondary: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  when (state) {
    ScreenState.Loading -> Column(
      modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.large),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      CircularProgressIndicator()
      Text(
        loadingText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
      )
    }
    is ScreenState.Unavailable -> Column(
      modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.large),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("Can\'t play this right now", style = MaterialTheme.typography.titleMedium)
      Text(
        state.reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = MaterialTheme.spacing.small),
      )
      Button(onClick = onRetry, modifier = Modifier.padding(top = MaterialTheme.spacing.medium)) {
        Text("Try again")
      }
      if (secondaryLabel != null && onSecondary != null) {
        TextButton(onClick = onSecondary) {
          Text(secondaryLabel)
        }
      }
    }
    ScreenState.Ready -> content()
  }
}
