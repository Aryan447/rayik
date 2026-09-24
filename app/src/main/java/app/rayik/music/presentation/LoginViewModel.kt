package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.rayik.music.auth.CompleteYouTubeLoginUseCase
import app.rayik.music.auth.MissingYouTubeDataSyncIdException
import app.rayik.music.auth.UpdateYouTubeLoginContextUseCase
import app.rayik.music.innertube.PlaybackAuthState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface LoginScreenState {
  data object Loading : LoginScreenState

  data class Success(
    val accountName: String,
    val accountEmail: String,
    val dataSyncId: String,
  ) : LoginScreenState

  data object Empty : LoginScreenState

  data class Error(
    val missingDataSyncId: Boolean,
  ) : LoginScreenState
}

@HiltViewModel
class LoginViewModel
@Inject
constructor(
  private val completeYouTubeLogin: CompleteYouTubeLoginUseCase,
  private val updateYouTubeLoginContext: UpdateYouTubeLoginContextUseCase,
) : ViewModel() {
  private val _screenState = MutableStateFlow<LoginScreenState>(LoginScreenState.Empty)
  val screenState: StateFlow<LoginScreenState> = _screenState.asStateFlow()

  private var latestVisitorData: String? = null
  private var latestDataSyncId: String? = null
  private var loginJob: Job? = null
  private var activeCookie: String? = null
  private var completedCookie: String? = null

  fun onVisitorDataExtracted(visitorData: String?) {
    val normalized = visitorData.normalizeAuthValue() ?: return
    latestVisitorData = normalized
    viewModelScope.launch {
      updateYouTubeLoginContext(visitorData = normalized)
    }
  }

  fun onDataSyncIdExtracted(dataSyncId: String?) {
    val normalized = dataSyncId.normalizeDataSyncId() ?: return
    if (latestDataSyncId == normalized) return

    val currentState = _screenState.value
    if (currentState is LoginScreenState.Success && currentState.dataSyncId != normalized) return

    latestDataSyncId = normalized
    viewModelScope.launch {
      updateYouTubeLoginContext(dataSyncId = normalized)
    }
    activeCookie?.let { startLogin(it, replaceActive = true) }
  }

  fun onCookiesCaptured(cookie: String?) {
    val normalizedCookie = cookie.normalizeAuthValue() ?: return
    startLogin(normalizedCookie, replaceActive = false)
  }

  private fun startLogin(
    normalizedCookie: String,
    replaceActive: Boolean,
  ) {
    if (completedCookie == normalizedCookie) return
    if (!replaceActive && loginJob?.isActive == true && activeCookie == normalizedCookie) return

    activeCookie = normalizedCookie
    loginJob?.cancel()
    loginJob =
      viewModelScope.launch {
        _screenState.value = LoginScreenState.Loading
        completeYouTubeLogin(
          cookie = normalizedCookie,
          visitorData = latestVisitorData,
          dataSyncId = latestDataSyncId,
        ).onSuccess { session ->
          completedCookie = normalizedCookie
          latestVisitorData = session.authState.visitorData
          latestDataSyncId = session.authState.dataSyncId
          _screenState.value =
            LoginScreenState.Success(
              accountName = session.accountName,
              accountEmail = session.accountEmail,
              dataSyncId = session.authState.dataSyncId.orEmpty(),
            )
        }.onFailure { throwable ->
          Timber.e(throwable, "Failed to complete YouTube login")
          _screenState.value =
            LoginScreenState.Error(
              missingDataSyncId = throwable is MissingYouTubeDataSyncIdException,
            )
        }
      }
  }
}

private fun String?.normalizeAuthValue(): String? {
  val trimmed = this?.trim()
  return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? = PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
