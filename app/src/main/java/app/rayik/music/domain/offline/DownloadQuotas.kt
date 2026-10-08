package app.rayik.music.domain.offline

import androidx.media3.exoplayer.offline.Download

/** Capped user pins only — no permanent rip-and-store (TOS). */
object DownloadQuotas {
  const val MAX_PINS = 500
  const val MAX_PIN_BYTES = 10L * 1024 * 1024 * 1024 // 10 GiB transient+pin cap

  fun canPin(currentPins: Int, currentBytes: Long, incomingBytes: Long): Boolean =
    currentPins < MAX_PINS && currentBytes + incomingBytes <= MAX_PIN_BYTES

  /** Download states that occupy a user-pin slot (explicit pins are never auto-evicted). */
  fun occupiesPinSlot(downloadState: Int): Boolean =
    when (downloadState) {
      Download.STATE_COMPLETED,
      Download.STATE_QUEUED,
      Download.STATE_DOWNLOADING,
      Download.STATE_RESTARTING,
      -> true
      else -> false
    }

  /**
   * Pure pin gate: distinct requested ids, drop ids already pinned (completed,
   * queued, or downloading), then cap to the remaining [MAX_PINS] budget.
   * Callers pass the download/cache state they already hold; no I/O here so
   * CI unit tests cover quota boundaries and duplicate pins.
   */
  fun selectPinnable(
    requestedIds: List<String>,
    alreadyPinnedIds: Set<String>,
    currentPinCount: Int,
  ): List<String> {
    val remaining = (MAX_PINS - currentPinCount).coerceAtLeast(0)
    if (remaining == 0) return emptyList()
    return requestedIds
      .distinct()
      .filter { it !in alreadyPinnedIds }
      .take(remaining)
  }
}
