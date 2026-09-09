package dev.androidagent.connectors

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDeviceFlowClientTest {
    @Test
    fun `device request uses fixed endpoint and public client id only`() = runBlocking {
        val http = FakeHttp(
            response(
                200,
                """
                {"device_code":"device-1","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}
                """.trimIndent(),
            ),
        )
        val client = client(http)

        val authorization = client.requestDeviceAuthorization(setOf(GitHubOAuthScopes.REPO))

        assertEquals("https://github.com/login/device/code", http.requests.single().url)
        assertEquals("POST", http.requests.single().method)
        assertTrue(http.requests.single().body!!.contains("client_id=public-client"))
        assertTrue(http.requests.single().body!!.contains("scope=repo"))
        assertFalse(http.requests.single().body!!.contains("client_secret"))
        assertEquals("ABCD-EFGH", authorization.userCode)
        assertEquals(1_900L, authorization.expiresAtEpochSeconds)
    }

    @Test
    fun `authorization pending and slow down are handled with increasing waits`() = runBlocking {
        val http = FakeHttp(
            response(200, deviceJson(expiresIn = 900, interval = 5)),
            response(400, "{\"error\":\"authorization_pending\"}"),
            response(400, "{\"error\":\"slow_down\"}"),
            response(
                200,
                """
                {"access_token":"gho_access","refresh_token":"ghr_refresh","token_type":"bearer","scope":"repo","expires_in":28800,"refresh_token_expires_in":15897600}
                """.trimIndent(),
            ),
        )
        val clock = MutableClock(1_000L)
        val waits = mutableListOf<Long>()
        val statuses = mutableListOf<GitHubDeviceFlowStatus>()
        val client = GitHubOAuthDeviceFlowClient(
            clientId = "public-client",
            http = http,
            clock = clock,
            delayController = ConnectorDelay { millis -> waits += millis },
        )

        val credentials = client.authenticate(
            scopes = setOf(GitHubOAuthScopes.REPO),
            onStatus = { statuses += it },
        )

        assertEquals(listOf(5_000L, 5_000L, 10_000L), waits)
        assertEquals(listOf("authorization_pending", "slow_down"), statuses.map { it.errorCode })
        assertEquals(setOf(GitHubOAuthScopes.REPO), credentials.grantedScopes)
        assertEquals("ghr_refresh", credentials.refreshToken)
        assertEquals(29_800L, credentials.accessTokenExpiresAtEpochSeconds)
        assertEquals(15_898_600L, credentials.refreshTokenExpiresAtEpochSeconds)
    }

    @Test
    fun `expiry is checked before a poll after the wait`() = runBlocking {
        val http = FakeHttp(response(200, deviceJson(expiresIn = 2, interval = 1)))
        val clock = MutableClock(100L)
        val client = GitHubOAuthDeviceFlowClient(
            clientId = "public-client",
            http = http,
            clock = clock,
            delayController = ConnectorDelay { clock.now += 2 },
        )

        assertSuspendThrows(DeviceFlowExpiredException::class.java) {
            client.authenticate(scopes = setOf(GitHubOAuthScopes.REPO))
        }
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `coroutine cancellation stops the flow`() = runBlocking {
        val http = FakeHttp(response(200, deviceJson()))
        val client = GitHubOAuthDeviceFlowClient(
            clientId = "public-client",
            http = http,
            delayController = ConnectorDelay { throw CancellationException("test cancellation") },
        )

        assertSuspendThrows(CancellationException::class.java) {
            client.authenticate(scopes = setOf(GitHubOAuthScopes.REPO))
        }
    }

    @Test
    fun `denial is surfaced as a typed error`() = runBlocking {
        val http = FakeHttp(
            response(200, deviceJson()),
            response(400, "{\"error\":\"access_denied\"}"),
        )
        val client = client(http)

        assertSuspendThrows(DeviceFlowDeniedException::class.java) {
            client.authenticate(scopes = setOf(GitHubOAuthScopes.REPO))
        }
    }

    @Test
    fun `unsupported scope is rejected before network`() = runBlocking {
        val http = FakeHttp()
        val client = client(http)

        assertSuspendThrows(IllegalArgumentException::class.java) {
            client.requestDeviceAuthorization(setOf("not-a-github-scope"))
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `refresh uses client id and no client secret`() = runBlocking {
        val http = FakeHttp(
            response(
                200,
                """{"access_token":"gho_new","refresh_token":"ghr_new","expires_in":10,"refresh_token_expires_in":20,"scope":"repo"}""",
            ),
        )
        val client = client(http)

        val result = client.refresh("ghr_old", setOf(GitHubOAuthScopes.REPO))

        assertEquals("gho_new", result.accessToken)
        assertEquals("ghr_new", result.refreshToken)
        assertTrue(http.requests.single().body!!.contains("grant_type=refresh_token"))
        assertFalse(http.requests.single().body!!.contains("client_secret"))
    }

    @Test
    fun `unexpected verification uri is rejected`() = runBlocking {
        val http = FakeHttp(
            response(
                200,
                deviceJson().replace(
                    "https://github.com/login/device",
                    "https://evil.example/device",
                ),
            ),
        )
        val client = client(http)

        assertSuspendThrows(IllegalArgumentException::class.java) {
            client.requestDeviceAuthorization()
        }
    }

    private fun client(http: FakeHttp): GitHubOAuthDeviceFlowClient = GitHubOAuthDeviceFlowClient(
        clientId = "public-client",
        http = http,
        clock = MutableClock(1_000L),
        delayController = ConnectorDelay { },
    )

    private fun <T : Throwable> assertSuspendThrows(type: Class<T>, block: suspend () -> Unit) {
        assertThrows(type) { runBlocking { block() } }
    }

    private class FakeHttp(vararg responses: ConnectorHttpResponse) : ConnectorHttpClient {
        val requests = mutableListOf<ConnectorHttpRequest>()
        private val queuedResponses = ArrayDeque(responses.toList())

        override suspend fun execute(request: ConnectorHttpRequest): ConnectorHttpResponse {
            requests += request
            return queuedResponses.removeFirstOrNull()
                ?: error("No fake response left for ${request.url}")
        }
    }

    private class MutableClock(var now: Long) : ConnectorClock {
        override fun nowEpochSeconds(): Long = now
    }

    private companion object {
        fun response(status: Int, body: String) = ConnectorHttpResponse(status, body = body)

        fun deviceJson(expiresIn: Long = 900, interval: Long = 5): String =
            """{"device_code":"device-1","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":$expiresIn,"interval":$interval}"""
    }
}
