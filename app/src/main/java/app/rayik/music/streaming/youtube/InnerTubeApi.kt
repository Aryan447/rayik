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
)

@Serializable
data class InnerTubePlayerResponse(
  val playabilityStatus: InnerTubePlayability = InnerTubePlayability(),
  val streamingData: InnerTubeStreamingData? = null,
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
)
