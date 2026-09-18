package app.rayik.music.auth

import java.security.MessageDigest

/**
 * Pure YouTube session-auth helpers. Signed-in traffic gets identity-based
 * trust from YouTube's edge instead of IP-based suspicion, which is what
 * dodges the anonymous 403-class failures this app fights — the same reason
 * ViMusic/BitChord-style clients offer Google sign-in.
 *
 * Auth follows the documented SAPISIDHASH scheme (same as NewPipe):
 * `Authorization: SAPISIDHASH <unix-sec>_<sha1(sec + " " + SAPISID)>`
 * plus the session cookies. Pure over strings/maps so unit tests cover it
 * without Android; storage and WebView harvest live in [YtSessionStore].
 */
object YtAuth {
  /** `true` when the jar holds what authenticated calls need. */
  fun isSignedIn(cookies: Map<String, String>): Boolean =
    cookies.containsKey("SAPISID") && cookies.containsKey("SID")

  /** `Cookie` header value for [cookies], or null when empty. */
  fun cookieHeader(cookies: Map<String, String>): String? =
    cookies.entries
      .filter { (name, value) -> name.isNotBlank() && !name.contains(' ') && !name.contains('=') && value.isNotBlank() }
      .joinToString("; ") { (name, value) -> "$name=$value" }
      .takeIf { it.isNotBlank() }

  /**
   * `Authorization` header value from the SAPISID cookie, or null when
   * signed out. [timestampSec] is a parameter (not `now`) so tests pin it.
   */
  fun authorizationHeader(cookies: Map<String, String>, timestampSec: Long): String? {
    val sapisid = cookies["SAPISID"]?.takeIf { it.isNotBlank() } ?: return null
    return "SAPISIDHASH ${timestampSec}_${sha1Hex("$timestampSec $sapisid")}"
  }

  /**
   * Parse a WebView-style cookie string (`"SID=x; HSID=y"`) into a jar map.
   * `CookieManager.getCookie()` returns bare pairs, but entries with blanks
   * or stray attributes are dropped defensively.
   */
  fun parseCookieString(cookieString: String): Map<String, String> =
    cookieString.split(';')
      .mapNotNull { part ->
        val name = part.substringBefore('=').trim()
        val value = part.substringAfter('=', "").trim()
        if (name.isBlank() || name.contains(' ') || name.contains('=') || value.isBlank()) {
          null
        } else {
          name to value
        }
      }
      .toMap()

  fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }
}
