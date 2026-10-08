package app.rayik.music.player

import app.rayik.music.utils.YTPlayerUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolver URL expiry (v0.4.0): stream URLs carry an `expire` epoch-second
 * query param. ExoPlayer must never be handed a URL inside the 60 s safety
 * window — [YTPlayerUtils.isExpiredOrNearExpiredStreamUrl] is the boundary.
 */
class StreamExpiryTest {
  private val url = "https://rr5---sn-qxaelnls.googlevideo.com/videoplayback?expire=2000000000"
  private val expiresAtMs = 2_000_000_000L * 1000L

  @Test fun `fresh url outside the safety window is usable`() {
    assertFalse(
      YTPlayerUtils.isExpiredOrNearExpiredStreamUrl(url, expiresAtMs - 61_000L),
    )
  }

  @Test fun `url at the safety boundary counts as expired`() {
    assertTrue(
      YTPlayerUtils.isExpiredOrNearExpiredStreamUrl(url, expiresAtMs - 60_000L),
    )
  }

  @Test fun `past url counts as expired`() {
    assertTrue(
      YTPlayerUtils.isExpiredOrNearExpiredStreamUrl(url, expiresAtMs + 1L),
    )
  }

  @Test fun `url without expire param never forces refresh`() {
    assertFalse(
      YTPlayerUtils.isExpiredOrNearExpiredStreamUrl(
        "https://cdn.example.com/audio/abc123",
        expiresAtMs,
      ),
    )
  }

  @Test fun `malformed url never forces refresh`() {
    assertFalse(YTPlayerUtils.isExpiredOrNearExpiredStreamUrl("not a url", expiresAtMs))
  }
}
