package app.rayik.music.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.rayik.music.ui.theme.spacing

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
    }
    ScreenState.Ready -> content()
  }
}
