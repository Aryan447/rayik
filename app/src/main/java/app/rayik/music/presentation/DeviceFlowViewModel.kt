package app.rayik.music.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.rayik.music.auth.CompleteOAuthLoginUseCase
import app.rayik.music.auth.OAuthSessionManager
import app.rayik.music.innertube.auth.OAuthException
import app.rayik.music.innertube.auth.TvOAuthClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface DeviceFlowState {
  data object Idle : DeviceFlowState
  data object RequestingCode : DeviceFlowState
  data class AwaitingApproval(
    val userCode: String,
    val verificationUrl: String,
  ) : DeviceFlowState
  data object Completing : DeviceFlowState
  data class Success(
    val accountName: String,
    val accountEmail: String,
  ) : DeviceFlowState
  data class Error(
    val message: String,
    val canRetry: Boolean = true,
  ) : DeviceFlowState
}

/**
 * One-tap sign-in: requests a TV device code, polls Google until the user
 * approves on google.com/device, stores the token pair, then completes the
 * login (account identity over Bearer, no cookies involved).
 */
@HiltViewModel
class DeviceFlowViewModel @Inject constructor(
  private val sessionManager: OAuthSessionManager,
  private val completeOAuthLogin: CompleteOAuthLoginUseCase,
) : ViewModel() {
  private val _state = MutableStateFlow<DeviceFlowState>(DeviceFlowState.Idle)
  val state: StateFlow<DeviceFlowState> = _state.asStateFlow()

  private val oAuthClient = TvOAuthClient()
  private var pollJob: Job? = null

  fun start() {
    if (pollJob?.isActive == true) return
    pollJob?.cancel()
    pollJob = viewModelScope.launch {
      _state.value = DeviceFlowState.RequestingCode
      val device = try {
        oAuthClient.requestDeviceCode()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w(e, "Device code request failed")
        _state.value = DeviceFlowState.Error(networkError(), canRetry = true)
        return@launch
      }
      _state.value = DeviceFlowState.AwaitingApproval(device.userCode, device.verificationUrl)
      try {
        val tokens = oAuthClient.awaitTokens(device)
        sessionManager.store(tokens)
        _state.value = DeviceFlowState.Completing
        completeOAuthLogin()
          .onSuccess { session ->
            _state.value = DeviceFlowState.Success(session.accountName, session.accountEmail)
          }
          .onFailure { throwable ->
            Timber.w(throwable, "OAuth login completion failed")
            _state.value = DeviceFlowState.Error(
              throwable.message?.takeIf { it.isNotBlank() } ?: "Couldn't finish signing in — try again",
              canRetry = true,
            )
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: OAuthException.AccessDenied) {
        _state.value = DeviceFlowState.Error(
          e.message ?: "Sign-in was denied",
          canRetry = true,
        )
      } catch (e: OAuthException.CodeExpired) {
        _state.value = DeviceFlowState.Error(
          e.message ?: "The code expired",
          canRetry = true,
        )
      } catch (e: Exception) {
        Timber.w(e, "Device flow failed")
        _state.value = DeviceFlowState.Error(networkError(), canRetry = true)
      }
    }
  }

  fun cancel() {
    pollJob?.cancel()
    pollJob = null
    if (_state.value !is DeviceFlowState.Success) {
      _state.value = DeviceFlowState.Idle
    }
  }

  private fun networkError(): String =
    "Couldn't reach Google — check your connection and try again"
}
