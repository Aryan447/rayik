package app.rayik.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackDiagnosticsTest {
  @Test fun `bundle keeps edge host and drops ip-bound query`() {
    val details = buildPlaybackDiagnostics(
      appVersion = "0.0.1",
      gitSha = "abc1234",
      trackId = "GX9x62kFsVU",
      trackTitle = "Gehra Hua",
      streamUrl = "https://rr5---sn-qxaelnls.googlevideo.com/videoplayback?expire=1789681270&ei=abc&ip=1.2.3.4",
      mimeType = "audio/webm",
      errorMessage = "This stream link died — retry for a fresh one (HTTP 403)",
    )
    assertEquals(
      "rayik 0.0.1 (abc1234)\n" +
        "track: GX9x62kFsVU — Gehra Hua\n" +
        "stream: host=rr5---sn-qxaelnls.googlevideo.com mime=audio/webm\n" +
        "error: This stream link died — retry for a fresh one (HTTP 403)",
      details,
    )
    assertFalse(details.contains("ip="))
    assertFalse(details.contains("expire="))
  }

  @Test fun `blank url and mime degrade honestly`() {
    assertEquals("", streamHost(""))
    assertEquals("", streamHost("not a url"))
    assertEquals("cdn.example.com", streamHost("https://cdn.example.com/x?a=b"))
    val details = buildPlaybackDiagnostics("v", "s", "id", "t", "", "", "e")
    assertEquals("rayik v (s)\ntrack: id — t\nstream: host= mime=unknown\nerror: e", details)
  }
}
