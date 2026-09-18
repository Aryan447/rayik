package app.rayik.music.domain.streaming

/**
 * Stream URL expiry helper. StreamResolver caches transient URLs and must
 * refresh before they die — never blank or crash, always retry.
 */
object StreamResolver {
  /** True when [expiresAtEpochMs] is within [skewMs] of [nowEpochMs]. */
  fun isExpired(expiresAtEpochMs: Long, nowEpochMs: Long, skewMs: Long = 60_000): Boolean =
    nowEpochMs + skewMs >= expiresAtEpochMs

  /** Remaining playable millis, floored at 0. */
  fun remainingMs(expiresAtEpochMs: Long, nowEpochMs: Long): Long =
    maxOf(0L, expiresAtEpochMs - nowEpochMs)
}
