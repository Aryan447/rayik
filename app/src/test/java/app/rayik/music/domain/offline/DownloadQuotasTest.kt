package app.rayik.music.domain.offline

import androidx.media3.exoplayer.offline.Download
import org.junit.Assert.assertEquals
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

  @Test fun `selectPinnable collapses duplicate requests`() {
    assertEquals(
      listOf("a", "b"),
      DownloadQuotas.selectPinnable(listOf("a", "a", "b", "b"), emptySet(), 0),
    )
  }

  @Test fun `selectPinnable skips already pinned ids`() {
    assertEquals(
      listOf("c"),
      DownloadQuotas.selectPinnable(listOf("a", "b", "c"), setOf("a", "b"), 2),
    )
  }

  @Test fun `selectPinnable caps new pins at remaining budget`() {
    val requested = (1..10).map { "song$it" }
    assertEquals(
      listOf("song1", "song2"),
      DownloadQuotas.selectPinnable(requested, emptySet(), DownloadQuotas.MAX_PINS - 2),
    )
  }

  @Test fun `selectPinnable rejects everything at the cap`() {
    assertTrue(
      DownloadQuotas.selectPinnable(listOf("a"), emptySet(), DownloadQuotas.MAX_PINS).isEmpty(),
    )
    assertTrue(
      DownloadQuotas.selectPinnable(listOf("a"), emptySet(), DownloadQuotas.MAX_PINS + 40).isEmpty(),
    )
  }

  @Test fun `completed and in-flight downloads occupy a pin slot`() {
    assertTrue(DownloadQuotas.occupiesPinSlot(Download.STATE_COMPLETED))
    assertTrue(DownloadQuotas.occupiesPinSlot(Download.STATE_QUEUED))
    assertTrue(DownloadQuotas.occupiesPinSlot(Download.STATE_DOWNLOADING))
    assertTrue(DownloadQuotas.occupiesPinSlot(Download.STATE_RESTARTING))
  }

  @Test fun `paused failed and removed downloads free the pin slot for retry`() {
    assertFalse(DownloadQuotas.occupiesPinSlot(Download.STATE_STOPPED))
    assertFalse(DownloadQuotas.occupiesPinSlot(Download.STATE_FAILED))
    assertFalse(DownloadQuotas.occupiesPinSlot(Download.STATE_REMOVING))
  }
}
