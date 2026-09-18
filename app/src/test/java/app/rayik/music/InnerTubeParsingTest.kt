package app.rayik.music.streaming.youtube

import app.rayik.music.preferences.StreamQuality
import app.rayik.music.streaming.piped.PipedErrorEnvelope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeParsingTest {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val resolver = InnerTubeResolver(OkHttpClient(), "test-key")

  private fun response() = json.decodeFromString(
    InnerTubePlayerResponse.serializer(),
    """
    {"playabilityStatus":{"status":"OK"},
     "streamingData":{
       "adaptiveFormats":[
         {"itag":140,"url":"https://cdn/low?expire=1700000000","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":131000},
         {"itag":251,"url":"https://cdn/high?expire=1700000000","mimeType":"audio/webm; codecs=\"opus\"","bitrate":160000},
         {"itag":249,"url":"https://cdn/tiny?expire=1700000000","mimeType":"audio/webm; codecs=\"opus\"","bitrate":50000},
         {"itag":137,"url":"https://cdn/video","mimeType":"video/mp4; codecs=\"avc1.640028\"","bitrate":4000000},
         {"itag":248,"signatureCipher":"s=abc&url=https://cdn/cipher","mimeType":"audio/webm; codecs=\"opus\"","bitrate":200000}
       ],
       "extraFutureField":1}}
    """.trimIndent(),
  )

  @Test fun `high picks richest direct audio`() {
    val picked = resolver.pick(response(), StreamQuality.High)
    assertEquals("https://cdn/high?expire=1700000000", picked?.url)
    assertEquals("opus", picked?.codec)
    assertEquals("audio/webm", picked?.mimeType)
    assertEquals(1_700_000_000_000L, picked?.expiresAtEpochMs)
  }

  @Test fun `saver picks cheapest direct audio`() {
    assertEquals("https://cdn/tiny?expire=1700000000", resolver.pick(response(), StreamQuality.Saver)?.url)
  }

  @Test fun `auto picks best at or under cap`() {
    assertEquals("https://cdn/high?expire=1700000000", resolver.pick(response(), StreamQuality.Auto)?.url)
  }

  @Test fun `ciphered and video entries are skipped`() {
    with(InnerTubeResolver) {
      val formats = response().streamingData!!.adaptiveFormats
      assertNull(formats.first { it.itag == 248 }.toCandidate()) // ciphered, no direct url
      assertNull(formats.first { it.itag == 137 }.toCandidate()) // muxed video
      assertNotNull(formats.first { it.itag == 251 }.toCandidate())
    }
  }

  @Test fun `hls is the fallback when no direct audio exists`() {
    val hlsOnly = InnerTubePlayerResponse(
      playabilityStatus = InnerTubePlayability(status = "OK"),
      streamingData = InnerTubeStreamingData(hlsManifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant/xyz"),
    )
    val picked = resolver.pick(hlsOnly, StreamQuality.Auto)
    assertEquals("https://manifest.googlevideo.com/api/manifest/hls_variant/xyz", picked?.url)
    // Extension-less manifest URL: without an explicit MIME ExoPlayer
    // sniffs it as progressive and buffers forever.
    assertEquals("application/x-mpegURL", picked?.mimeType)
    assertTrue((picked?.expiresAtEpochMs ?: 0L) > System.currentTimeMillis())
  }

  @Test fun `excluded urls never come back on retry`() {
    // A 403'd edge URL must not be handed to ExoPlayer again — the retry
    // picks the next-best rendition (a different edge host).
    val picked = resolver.pick(
      response(),
      StreamQuality.High,
      excludedUrls = setOf("https://cdn/high?expire=1700000000"),
    )
    assertEquals("https://cdn/low?expire=1700000000", picked?.url)
  }

  @Test fun `all urls excluded picks nothing honest`() {
    val picked = resolver.pick(
      InnerTubePlayerResponse(
        playabilityStatus = InnerTubePlayability(status = "OK"),
        streamingData = InnerTubeStreamingData(
          adaptiveFormats = listOf(
            InnerTubeFormat(itag = 140, url = "https://cdn/only", mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrate = 131000),
          ),
        ),
      ),
      StreamQuality.Auto,
      excludedUrls = setOf("https://cdn/only"),
    )
    assertNull(picked)
  }

  @Test fun `blocked response picks nothing`() {
    val blocked = InnerTubePlayerResponse(
      playabilityStatus = InnerTubePlayability(
        status = "LOGIN_REQUIRED",
        reason = "Sign in to confirm that you're not a bot",
      ),
    )
    assertNull(resolver.pick(blocked, StreamQuality.Auto))
  }

  @Test fun `blank key sends the player endpoint bare`() {
    // Live-verified: the ANDROID client resolves without a key, so a
    // default install (no env / local.properties key) must still attempt
    // resolution instead of failing with "Streaming key not configured".
    assertEquals(
      "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
      InnerTubeResolver.playerUrl(""),
    )
    assertEquals(
      "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
      InnerTubeResolver.playerUrl("   "),
    )
  }

  @Test fun `configured key is appended to the player endpoint`() {
    assertEquals(
      "https://www.youtube.com/youtubei/v1/player?prettyPrint=false&key=abc123",
      InnerTubeResolver.playerUrl("abc123"),
    )
  }

  @Test fun `request serializes the android client`() {
    val body = json.encodeToString(
      InnerTubeRequest.serializer(),
      InnerTubeRequest(
        videoId = "dQw4w9WgXcQ",
        context = InnerTubeContext(InnerTubeClient("ANDROID", "20.10.38", 34)),
      ),
    )
    assertTrue(body.contains("\"videoId\":\"dQw4w9WgXcQ\""))
    assertTrue(body.contains("\"clientName\":\"ANDROID\""))
  }

  @Test fun `piped bot-block envelope decodes for honest errors`() {
    val envelope = json.decodeFromString(
      PipedErrorEnvelope.serializer(),
      """{"error":"org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException: blocked"}""",
    )
    assertTrue(envelope.error!!.contains("SignInConfirmNotBot"))
  }
}
