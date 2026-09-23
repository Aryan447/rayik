package app.rayik.music.player

/** m:ss display for positions and durations. Pure so CI unit tests cover it. */
fun formatMs(ms: Long): String {
  val totalSeconds = (ms.coerceAtLeast(0L) / 1_000)
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
