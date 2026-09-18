package app.rayik.music.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds session authentication to YouTube API calls on the shared OkHttp
 * client: `Authorization: SAPISIDHASH …` on `youtubei` endpoints (cookies
 * themselves ride via [YtSessionStore]'s CookieJar, which also covers
 * ExoPlayer media fetches on googlevideo edges).
 *
 * Signed-out: passes requests through untouched — anonymous behavior is
 * exactly what it is today. No cookie values are ever logged.
 */
class YtAuthInterceptor(
  private val sessions: YtSessionStore,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val host = request.url.host.lowercase()
    if (!host.endsWith("youtubei.googleapis.com") && host != "www.youtube.com" && !host.endsWith(".youtube.com")) {
      return chain.proceed(request)
    }
    val jar = sessions.current()
    val auth = YtAuth.authorizationHeader(jar, System.currentTimeMillis() / 1_000L) ?: return chain.proceed(request)
    // Only Authorization: an empty X-YouTube-Identity-Token would hurt more
    // than help — identity tokens come from login flows, not cookies.
    return chain.proceed(
      request.newBuilder()
        .header("Authorization", auth)
        .build(),
    )
  }
}
