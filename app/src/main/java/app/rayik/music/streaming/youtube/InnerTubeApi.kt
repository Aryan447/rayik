package app.rayik.music.streaming.youtube

import kotlinx.serialization.Serializable

/**
 * InnerTube `youtubei/v1/player` JSON shapes. Every field is defaulted and
 * unknown keys are ignored: Google drifts the schema and parsing must
 * never crash.
 */
@Serializable
data class InnerTubeRequest(
  val videoId: String,
  val context: InnerTubeContext,
  /**
   * Official-client playback checks (yt-dlp/NewPipe shape). Live-verified:
   * the ANDROID client still answers OK with direct audio URLs when these
   * ride along, and omitting them leaves edge integrity checks an excuse
   * to 403 the stream at fetch time.
   */
  val playbackContext: InnerTubePlaybackContext = InnerTubePlaybackContext(),
  val contentCheckOk: Boolean = true,
  val racyCheckOk: Boolean = true,
  /**
   * Proof-of-origin attestation for the GVS enforcement era (absent =
   * omitted from the wire, so tokenless installs send exactly the old
   * body). Only ever populated from [app.rayik.music.streaming.youtube.PoTokenProvider].
   */
  val serviceIntegrityDimensions: InnerTubeIntegrity? = null,
)

@Serializable
data class InnerTubeIntegrity(
  val poToken: String? = null,
)

@Serializable
data class InnerTubePlaybackContext(
  val contentPlaybackContext: InnerTubeContentPlayback = InnerTubeContentPlayback(),
)

@Serializable
data class InnerTubeContentPlayback(
  val html5Preference: String = "HTML5_PREF_WANTS",
  val lactMilliseconds: String = "0",
)

@Serializable
data class InnerTubeSearchRequest(
  val query: String,
  val context: InnerTubeContext,
)

@Serializable
data class InnerTubeContext(
  val client: InnerTubeClient,
)

@Serializable
data class InnerTubeClient(
  val clientName: String,
  val clientVersion: String,
  val androidSdkVersion: Int,
  val hl: String = "en",
  val gl: String = "US",
  /**
   * Session identity that minted the stream URLs (NewPipe pattern: the
   * player response's own visitorData travels back on subsequent calls).
   * Null = omitted from the wire.
   */
  val visitorData: String? = null,
)

@Serializable
data class InnerTubePlayerResponse(
  val playabilityStatus: InnerTubePlayability = InnerTubePlayability(),
  val streamingData: InnerTubeStreamingData? = null,
  val responseContext: InnerTubeResponseContext? = null,
)

@Serializable
data class InnerTubeResponseContext(
  val visitorData: String? = null,
)

@Serializable
data class InnerTubePlayability(
  val status: String = "",
  val reason: String = "",
)

@Serializable
data class InnerTubeStreamingData(
  val adaptiveFormats: List<InnerTubeFormat> = emptyList(),
  val hlsManifestUrl: String? = null,
)

@Serializable
data class InnerTubeFormat(
  val itag: Int = 0,
  val url: String = "",
  val signatureCipher: String = "",
  val mimeType: String = "",
  val bitrate: Int = 0,
  /**
   * Present on audio-bearing formats (audio-only and muxed). Video-only
   * renditions omit it — this is how the resolver tells itag 133-style
   * video from itag 18-style muxed-with-audio without an itag table.
   */
  val audioChannels: Int = 0,
)
