package app.rayik.music.domain.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverTest {
  @Test fun `fresh url is not expired`() {
    assertFalse(StreamResolver.isExpired(expiresAtEpochMs = 10_000_000L, nowEpochMs = 0L))
  }

  @Test fun `url inside skew window counts as expired`() {
    assertTrue(StreamResolver.isExpired(expiresAtEpochMs = 50_000L, nowEpochMs = 0L, skewMs = 60_000L))
  }

  @Test fun `remaining never goes negative`() {
    assertTrue(StreamResolver.remainingMs(100L, 500L) == 0L)
  }
}
