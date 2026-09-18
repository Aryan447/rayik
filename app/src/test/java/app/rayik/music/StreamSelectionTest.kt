package app.rayik.music.streaming

import app.rayik.music.preferences.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectionTest {
  private val candidates = listOf(
    AudioCandidate(url = "https://cdn/low", bitrate = 48_000, codec = "mp4a.40.2"),
    AudioCandidate(url = "https://cdn/mid", bitrate = 128_000, codec = "opus"),
    AudioCandidate(url = "https://cdn/high", bitrate = 256_000, codec = "opus"),
  )

  @Test fun `saver picks cheapest`() {
    assertEquals("https://cdn/low", StreamSelection.select(candidates, StreamQuality.Saver)?.url)
  }

  @Test fun `high picks richest`() {
    assertEquals("https://cdn/high", StreamSelection.select(candidates, StreamQuality.High)?.url)
  }

  @Test fun `auto picks best at or under cap`() {
    assertEquals("https://cdn/mid", StreamSelection.select(candidates, StreamQuality.Auto)?.url)
  }

  @Test fun `auto falls back to cheapest above cap`() {
    val pricey = listOf(
      AudioCandidate(url = "https://cdn/a", bitrate = 256_000),
      AudioCandidate(url = "https://cdn/b", bitrate = 320_000),
    )
    assertEquals("https://cdn/a", StreamSelection.select(pricey, StreamQuality.Auto)?.url)
  }

  @Test fun `blank urls are ignored`() {
    val onlyBlank = listOf(AudioCandidate(url = "", bitrate = 128_000))
    assertNull(StreamSelection.select(onlyBlank, StreamQuality.Auto))
  }

  @Test fun `empty list returns null`() {
    assertNull(StreamSelection.select(emptyList(), StreamQuality.High))
  }

  @Test fun `expiry parses expire param as epoch seconds`() {
    val url = "https://cdn/x?expire=1_700_000_000".replace("_", "")
    assertEquals(1_700_000_000_000L, ResolvedStream.expiryFromUrl(url, nowEpochMs = 0L))
  }

  @Test fun `expiry falls back without param`() {
    assertEquals(6 * 60 * 60 * 1_000L, ResolvedStream.expiryFromUrl("https://cdn/x", nowEpochMs = 0L))
  }
}
