package app.rayik.music.streaming

import app.rayik.music.player.playbackErrorMessage
import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMimeTest {
  @Test fun `opus maps to webm`() {
    assertEquals("audio/webm", audioMimeType("opus"))
    assertEquals("audio/webm", audioMimeType("opus", "webm"))
  }

  @Test fun `aac variants map to mp4`() {
    assertEquals("audio/mp4", audioMimeType("mp4a.40.2"))
    assertEquals("audio/mp4", audioMimeType("mp4a.40.5"))
  }

  @Test fun `container hint covers piped format names`() {
    assertEquals("audio/webm", audioMimeType("", "webm"))
    assertEquals("audio/mp4", audioMimeType("", "m4a"))
    assertEquals("audio/mp4", audioMimeType("unknown-codec", "mp4"))
  }

  @Test fun `unknown codec and container return null`() {
    assertNull(audioMimeType(""))
    assertNull(audioMimeType("avc1.640028", "mp4v"))
  }
}

class PlaybackErrorsTest {
  @Test fun `dead link asks for a fresh retry`() {
    assertEquals(
      "This stream link died — retry for a fresh one",
      playbackErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "boom"),
    )
  }

  @Test fun `dead link surfaces the http status for on-device diagnosis`() {
    assertEquals(
      "This stream link died — retry for a fresh one (HTTP 403)",
      playbackErrorMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "Response code: 403"),
    )
    assertEquals(
      "Connection dropped — check your network and retry (HTTP 404)",
      playbackErrorMessage(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        "Response code: 404 gone",
      ),
    )
  }

  @Test fun `dropped connection blames the network`() {
    assertEquals(
      "Connection dropped — check your network and retry",
      playbackErrorMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null),
    )
  }

  @Test fun `unreadable stream suggests another track`() {
    assertEquals(
      "Couldn't read this stream — retry, or try another track",
      playbackErrorMessage(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, null),
    )
  }

  @Test fun `unknown code keeps a useful cause`() {
    assertEquals(
      "timeout",
      playbackErrorMessage(PlaybackException.ERROR_CODE_UNSPECIFIED, "timeout"),
    )
  }

  @Test fun `unknown code without cause falls back to default copy`() {
    assertEquals(
      "Can't play this right now",
      playbackErrorMessage(PlaybackException.ERROR_CODE_UNSPECIFIED, "  "),
    )
  }
}
