package app.rayik.music.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtAuthTest {
  @Test fun `sapisid hash matches the documented scheme`() {
    // SAPISIDHASH <sec>_<sha1(sec + " " + sapisid)> — pinned against the
    // construction NewPipeExtractor uses, so servers accept our header.
    val header = YtAuth.authorizationHeader(mapOf("SAPISID" to "abc123"), 1_700_000_000L)
    assertTrue(header!!.startsWith("SAPISIDHASH 1700000000_"))
    assertEquals(header, "SAPISIDHASH 1700000000_${YtAuth.sha1Hex("1700000000 abc123")}")
    assertEquals("5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8", YtAuth.sha1Hex("password"))
  }

  @Test fun `signed out without sapisid or sid`() {
    assertNull(YtAuth.authorizationHeader(emptyMap(), 0L))
    assertNull(YtAuth.authorizationHeader(mapOf("SID" to "x"), 0L))
    assertFalse(YtAuth.isSignedIn(mapOf("SID" to "x")))
    assertFalse(YtAuth.isSignedIn(mapOf("SAPISID" to "x")))
    assertTrue(YtAuth.isSignedIn(mapOf("SID" to "x", "SAPISID" to "y")))
  }

  @Test fun `cookie header joins pairs and drops blanks`() {
    assertEquals(
      "SID=x; HSID=y",
      YtAuth.cookieHeader(mapOf("SID" to "x", "HSID" to "y", "EMPTY" to "")),
    )
    assertNull(YtAuth.cookieHeader(emptyMap()))
  }

  @Test fun `webview cookie string parses to a jar map`() {
    val jar = YtAuth.parseCookieString("SID=x; HSID=y;  LOGIN_INFO=z; Path=/; empty=")
    assertEquals(mapOf("SID" to "x", "HSID" to "y", "LOGIN_INFO" to "z", "Path" to "/"), jar)
  }

  @Test fun `youtube host gate keeps the jar off third parties`() {
    assertTrue(YtSessionStore.isYouTubeHost("www.youtube.com"))
    assertTrue(YtSessionStore.isYouTubeHost("music.youtube.com"))
    assertTrue(YtSessionStore.isYouTubeHost("rr5---sn-qxaelnls.googlevideo.com"))
    assertTrue(YtSessionStore.isYouTubeHost("youtubei.googleapis.com"))
    assertFalse(YtSessionStore.isYouTubeHost("evil-youtube.com"))
    assertFalse(YtSessionStore.isYouTubeHost("googlevideo.com.evil.com"))
    assertFalse(YtSessionStore.isYouTubeHost("api.piped.private.coffee"))
  }
}
