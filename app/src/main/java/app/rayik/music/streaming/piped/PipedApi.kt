package app.rayik.music.streaming.piped

import kotlinx.serialization.Serializable

/**
 * Piped-protocol JSON shapes. Every field is defaulted and unknown keys
 * are ignored: public instances drift, and parsing must never crash.
 */
@Serializable
data class PipedSearchResponse(
  val items: List<PipedSearchItem> = emptyList(),
)

@Serializable
data class PipedSearchItem(
  val url: String = "",
  val title: String = "",
  val uploaderName: String = "",
  val thumbnail: String = "",
  val duration: Double? = null,
  val type: String = "",
)

@Serializable
data class PipedStreamsResponse(
  val title: String = "",
  val uploader: String = "",
  val duration: Double? = null,
  val hls: String? = null,
  val dash: String? = null,
  val audioStreams: List<PipedAudioStream> = emptyList(),
)

@Serializable
data class PipedAudioStream(
  val url: String = "",
  val codec: String = "",
  val format: String = "",
  val bitrate: Int = 0,
)

/**
 * Failure envelope Piped returns with HTTP 200 when YouTube blocks the
 * instance (e.g. `{"error": "...SignInConfirmNotBotException..."}`).
 * Decoded best-effort so resolve can report the real reason.
 */
@Serializable
data class PipedErrorEnvelope(
  val error: String? = null,
  val message: String? = null,
)
