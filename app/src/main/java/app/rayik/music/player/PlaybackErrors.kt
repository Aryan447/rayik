package app.rayik.music.player

import androidx.media3.common.PlaybackException

/**
 * Turns an ExoPlayer failure into UI-safe copy. ExoPlayer's own message is
 * useless here — source errors always read exactly "Source error" (see
 * `ExoPlaybackException.deriveMessage`) — so the mapping works off the
 * numeric [PlaybackException.errorCode], which is what actually
 * distinguishes a dead link from a dead network.
 *
 * Pure over `(errorCode, causeMessage)` so unit tests cover the rule
 * without touching the player.
 */
fun playbackErrorMessage(errorCode: Int, causeMessage: String?): String {
  val cause = causeMessage?.trim().takeIf { !it.isNullOrBlank() }
  // ExoPlayer reports HTTP failures as "Response code: 403" inside the
  // cause — surface the status so a dead link is diagnosable on-device
  // without logcat (e.g. "…fresh one (HTTP 403)").
  val httpStatus = cause?.let { HTTP_STATUS.find(it)?.groupValues?.getOrNull(1) }
  val statusSuffix = httpStatus?.let { " (HTTP $it)" }.orEmpty()
  return when (errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    -> "This stream link died — retry for a fresh one$statusSuffix"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> "Connection dropped — check your network and retry$statusSuffix"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    -> "Couldn't read this stream — retry, or try another track$statusSuffix"
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
    -> "This device can't decode the stream — try Data saver quality$statusSuffix"
    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
    -> "Protected track — rāyik can't play this one$statusSuffix"
    else -> (cause?.take(MAX_CAUSE_CHARS) ?: "Can't play this right now") + statusSuffix
  }
}

private const val MAX_CAUSE_CHARS = 140
private val HTTP_STATUS = Regex("Response code:\\s*(\\d{3})")
