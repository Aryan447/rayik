package app.rayik.music.streaming

/**
 * Contract for a streaming source. Implementations are keyless and
 * on-device (no backend per project direction): they speak HTTPS to
 * public source APIs with the shared OkHttp client and return transient
 * URLs. Failures surface as [Result.failure] so screens can render
 * Unavailable + retry instead of blank states.
 */
interface StreamSource {
  /** Search for tracks. Empty query returns success with an empty list. */
  suspend fun search(query: String): Result<List<Track>>

  /** Resolve one track to a transient playable URL. */
  suspend fun resolve(track: Track): Result<ResolvedStream> = resolve(track, emptySet())

  /**
   * Resolve while skipping [excludedUrls] — URLs ExoPlayer already failed on
   * (403/404 from an edge host, expired links). googlevideo renditions live
   * on different edge hosts, so a retry with a *different* URL can succeed
   * where replaying the dead one fails forever.
   */
  suspend fun resolve(track: Track, excludedUrls: Set<String>): Result<ResolvedStream>
}
