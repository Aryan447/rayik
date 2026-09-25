package app.rayik.music.innertube.auth

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OAuth 2.0 device flow (RFC 8628) against Google's shared Smart-TV client.
 *
 * The client id/secret below are public constants baked into every Smart-TV
 * YouTube install and shipped openly by ytmusicapi, pytube and SmartTube —
 * they are not private credentials, just the well-known TV client identity.
 * Using them means rāyik needs no Google Cloud project, no consent-screen
 * verification, and forks/F-Droid builds keep working.
 *
 * UX: the app shows a short user code, the user approves once on
 * google.com/device (no ID/password typing when the browser is signed in),
 * then [awaitTokens] polls until Google issues tokens. The refresh token is
 * long-lived: sign in once, silent refresh afterwards.
 *
 * Tokens are Bearer credentials for InnerTube (`Authorization: Bearer …`,
 * no API-key query param, no empty visitor header — per ytmusicapi's rules).
 * Network I/O goes through [OAuthTransport] so the poll state machine is
 * unit-testable with fakes; the default transport is plain HttpURLConnection
 * to avoid new dependencies in core.
 */
const val TV_CLIENT_ID =
    "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
const val TV_CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
const val OAUTH_SCOPE_YOUTUBE = "https://www.googleapis.com/auth/youtube"
const val OAUTH_DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
const val OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
const val OAUTH_REVOKE_URL = "https://oauth2.googleapis.com/revoke"
const val OAUTH_VERIFICATION_URL = "https://www.google.com/device"

/** Response of the device/code endpoint. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSec: Long,
    val intervalSec: Long,
)

/** Issued token pair. [obtainedAtMs] anchors [isExpired]. */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val obtainedAtMs: Long = System.currentTimeMillis(),
) {
    /** True when the access token should no longer be sent (30s leeway). */
    fun isExpired(nowMs: Long = System.currentTimeMillis(), leewaySec: Long = 30L): Boolean =
        nowMs >= obtainedAtMs + (expiresInSec - leewaySec).coerceAtLeast(0L) * 1000L
}

/** Terminal device-flow failures. */
sealed class OAuthException(message: String) : Exception(message) {
    /** User pressed Deny on Google's page. */
    class AccessDenied : OAuthException("Sign-in was denied on Google's page")
    /** The code expired before approval (codes live ~30 min). */
    class CodeExpired : OAuthException("The sign-in code expired — request a fresh one")
    /** Unexpected payload or transport failure; safe to retry. */
    class Transient(details: String) : OAuthException(details)
}

/** Minimal form-POST transport; faked in unit tests. */
fun interface OAuthTransport {
    suspend fun postForm(url: String, params: Map<String, String>): String
}

/** Plain HttpURLConnection transport — no extra dependencies. */
val urlConnectionTransport = OAuthTransport { url, params ->
    val body = params.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
    }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    }
    try {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        // Error payloads carry the OAuth error code — surface them, don't drop.
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    } finally {
        connection.disconnect()
    }
}

class TvOAuthClient(
    private val transport: OAuthTransport = urlConnectionTransport,
    private val clientId: String = TV_CLIENT_ID,
    private val clientSecret: String = TV_CLIENT_SECRET,
) {
    /** Starts the flow: returns the code the user approves on Google's page. */
    suspend fun requestDeviceCode(): DeviceCode {
        val json = transport.postForm(
            OAUTH_DEVICE_CODE_URL,
            mapOf("client_id" to clientId, "scope" to OAUTH_SCOPE_YOUTUBE),
        )
        return DeviceCode(
            deviceCode = json.flatString("device_code")
                ?: throw OAuthException.Transient("device/code gave no device_code"),
            userCode = json.flatString("user_code")
                ?: throw OAuthException.Transient("device/code gave no user_code"),
            verificationUrl = json.flatString("verification_url") ?: OAUTH_VERIFICATION_URL,
            expiresInSec = json.flatLong("expires_in") ?: 1800L,
            intervalSec = json.flatLong("interval") ?: 5L,
        )
    }

    /**
     * Polls until the user approves (returns tokens) or a terminal error
     * occurs. [onWait] sleeps between attempts — injectable for tests and
     * cancellable by the caller's coroutine scope.
     */
    suspend fun awaitTokens(
        device: DeviceCode,
        onWait: suspend (seconds: Long) -> Unit = { kotlinx.coroutines.delay(it * 1000L) },
    ): OAuthTokens {
        var interval = device.intervalSec.coerceAtLeast(1L)
        val deadlineMs = System.currentTimeMillis() + device.expiresInSec * 1000L
        while (true) {
            val json = transport.postForm(
                OAUTH_TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "device_code" to device.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
            val access = json.flatString("access_token")
            if (access != null) {
                return OAuthTokens(
                    accessToken = access,
                    refreshToken = json.flatString("refresh_token")
                        ?: throw OAuthException.Transient("token response gave no refresh_token"),
                    expiresInSec = json.flatLong("expires_in") ?: 3600L,
                )
            }
            when (json.flatString("error")) {
                "authorization_pending" -> Unit
                // Google asks us to back off — honor it, don't hammer.
                "slow_down" -> interval += 5L
                "access_denied" -> throw OAuthException.AccessDenied()
                "expired_token" -> throw OAuthException.CodeExpired()
                else -> throw OAuthException.Transient(
                    json.flatString("error_description")
                        ?: json.flatString("error")
                        ?: "token poll failed",
                )
            }
            if (System.currentTimeMillis() >= deadlineMs) throw OAuthException.CodeExpired()
            onWait(interval)
        }
    }

    /** Exchanges a stored refresh token for a fresh pair. */
    suspend fun refresh(refreshToken: String): OAuthTokens {
        val json = transport.postForm(
            OAUTH_TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            ),
        )
        return OAuthTokens(
            accessToken = json.flatString("access_token")
                ?: throw OAuthException.Transient(
                    json.flatString("error_description")
                        ?: json.flatString("error")
                        ?: "refresh failed",
                ),
            // Rotation: keep the new refresh token when issued, else the old one.
            refreshToken = json.flatString("refresh_token") ?: refreshToken,
            expiresInSec = json.flatLong("expires_in") ?: 3600L,
        )
    }

    /** Best-effort server-side revoke on sign-out. Never throws. */
    suspend fun revoke(token: String): Boolean =
        runCatching {
            transport.postForm(OAUTH_REVOKE_URL, mapOf("token" to token))
            true
        }.getOrDefault(false)
}

/**
 * Minimal extractor for Google's flat OAuth JSON objects. Hand-rolled on
 * purpose: core avoids a JSON dependency for five string/long fields, and
 * the shape is stable and covered by unit tests.
 */
internal fun String.flatString(key: String): String? =
    Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")

internal fun String.flatLong(key: String): Long? =
    Regex("\"$key\"\\s*:\\s*(\\d+)")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
