package app.rayik.music.domain.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQuotasTest {
  @Test fun `allows pin under quota`() {
    assertTrue(DownloadQuotas.canPin(0, 0L, 100L))
  }

  @Test fun `rejects pin over count`() {
    assertFalse(DownloadQuotas.canPin(DownloadQuotas.MAX_PINS, 0L, 100L))
  }

  @Test fun `rejects pin over bytes`() {
    assertFalse(DownloadQuotas.canPin(0, DownloadQuotas.MAX_PIN_BYTES, 1L))
  }
}
