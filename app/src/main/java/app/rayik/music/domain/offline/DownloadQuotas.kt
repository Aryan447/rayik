package app.rayik.music.domain.offline

/** Capped user pins only — no permanent rip-and-store (TOS). */
object DownloadQuotas {
  const val MAX_PINS = 500
  const val MAX_PIN_BYTES = 10L * 1024 * 1024 * 1024 // 10 GiB transient+pin cap

  fun canPin(currentPins: Int, currentBytes: Long, incomingBytes: Long): Boolean =
    currentPins < MAX_PINS && currentBytes + incomingBytes <= MAX_PIN_BYTES
}
