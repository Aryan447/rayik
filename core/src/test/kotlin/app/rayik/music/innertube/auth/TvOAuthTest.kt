package app.rayik.music.innertube.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val DEVICE_JSON =
    """{"device_code":"dc","user_code":"HRW-VBL-KWB","verification_url":"https://www.google.com/device","expires_in":1800,"interval":5}"""
private const val PENDING_JSON = """{"error":"authorization_pending","error_description":"pending"}"""
private const val TOKEN_JSON =
    """{"access_token":"ya29.at","expires_in":3599,"refresh_token":"1//rt","scope":"https://www.googleapis.com/auth/youtube","token_type":"Bearer"}"""

class TvOAuthTest {
    @Test
    fun `device code parses all fields`() = runBlocking {
        val client = TvOAuthClient(transport = OAuthTransport { _, _ -> DEVICE_JSON })
        val device = client.requestDeviceCode()
        assertEquals("dc", device.deviceCode)
        assertEquals("HRW-VBL-KWB", device.userCode)
        assertEquals("https://www.google.com/device", device.verificationUrl)
        assertEquals(1800L, device.expiresInSec)
        assertEquals(5L, device.intervalSec)
    }

    @Test
    fun `device code missing fields throws transient`() = runBlocking {
        val client = TvOAuthClient(transport = OAuthTransport { _, _ -> "{}" })
        try {
            client.requestDeviceCode()
            fail("expected Transient")
        } catch (e: OAuthException.Transient) {
            // expected
        }
    }

    @Test
    fun `poll succeeds after pending without sleeping`() = runBlocking {
        val calls = mutableListOf<String>()
        var waits = 0
        val client = TvOAuthClient(
            transport = OAuthTransport { _, _ ->
                calls += "poll"
                if (calls.size < 3) PENDING_JSON else TOKEN_JSON
            },
        )
        val tokens = client.awaitTokens(
            DeviceCode("dc", "HRW-VBL-KWB", OAUTH_VERIFICATION_URL, 1800L, 5L),
            onWait = { waits++ },
        )
        assertEquals("ya29.at", tokens.accessToken)
        assertEquals("1//rt", tokens.refreshToken)
        assertEquals(3599L, tokens.expiresInSec)
        assertEquals(2, waits)
    }

    @Test
    fun `poll maps slow_down denial expiry`() = runBlocking {
        // slow_down backs off but still succeeds
        var polls = 0
        val backoff = TvOAuthClient(
            transport = OAuthTransport { _, _ ->
                polls++
                if (polls == 1) """{"error":"slow_down"}""" else TOKEN_JSON
            },
        )
        val seenWaits = mutableListOf<Long>()
        backoff.awaitTokens(
            DeviceCode("dc", "HRW-VBL-KWB", OAUTH_VERIFICATION_URL, 1800L, 5L),
            onWait = { seenWaits += it },
        )
        assertEquals(listOf(10L), seenWaits)

        val denied = TvOAuthClient(
            transport = OAuthTransport { _, _ -> """{"error":"access_denied"}""" },
        )
        try {
            denied.awaitTokens(
                DeviceCode("dc", "HRW-VBL-KWB", OAUTH_VERIFICATION_URL, 1800L, 5L),
                onWait = {},
            )
            fail("expected AccessDenied")
        } catch (e: OAuthException.AccessDenied) {
            // expected
        }

        val expired = TvOAuthClient(
            transport = OAuthTransport { _, _ -> """{"error":"expired_token"}""" },
        )
        try {
            expired.awaitTokens(
                DeviceCode("dc", "HRW-VBL-KWB", OAUTH_VERIFICATION_URL, 1800L, 5L),
                onWait = {},
            )
            fail("expected CodeExpired")
        } catch (e: OAuthException.CodeExpired) {
            // expected
        }
    }

    @Test
    fun `refresh keeps old token when none rotated`() = runBlocking {
        val client = TvOAuthClient(
            transport = OAuthTransport { _, _ ->
                """{"access_token":"ya29.new","expires_in":3600}"""
            },
        )
        val tokens = client.refresh("1//old")
        assertEquals("ya29.new", tokens.accessToken)
        assertEquals("1//old", tokens.refreshToken)
    }

    @Test
    fun `expiry honors leeway`() {
        val tokens = OAuthTokens("a", "r", expiresInSec = 3600L, obtainedAtMs = 0L)
        assertFalse(tokens.isExpired(nowMs = 3_569_999L))
        assertTrue(tokens.isExpired(nowMs = 3_570_000L))
        assertTrue(tokens.isExpired(nowMs = 3_600_000L))
    }

    @Test
    fun `flat json extractor handles escapes`() {
        val json = """{"error_description":"line \"quoted\" \\ back"}"""
        assertEquals("line \"quoted\" \\ back", json.flatString("error_description"))
    }
}
