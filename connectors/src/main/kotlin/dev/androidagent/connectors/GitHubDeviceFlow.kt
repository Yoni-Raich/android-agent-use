package dev.androidagent.connectors

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class ConnectorHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class ConnectorHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

fun interface ConnectorHttpClient {
    suspend fun execute(request: ConnectorHttpRequest): ConnectorHttpResponse
}

fun interface ConnectorClock {
    fun nowEpochSeconds(): Long
}

fun interface ConnectorDelay {
    suspend fun await(millis: Long)
}

object SystemConnectorClock : ConnectorClock {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000L
}

object SystemConnectorDelay : ConnectorDelay {
    override suspend fun await(millis: Long) {
        delay(millis)
    }
}

/** A fixed-endpoint HttpURLConnection implementation used by the Android app. */
class UrlConnectionHttpClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : ConnectorHttpClient {
    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
    }

    override suspend fun execute(request: ConnectorHttpRequest): ConnectorHttpResponse = withContext(Dispatchers.IO) {
        require(request.method.matches(Regex("[A-Z]+"))) { "Invalid HTTP method" }
        require(GitHubEndpoints.isAllowed(request.url)) { "GitHub egress rejected: ${request.url}" }

        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            requestMethod = request.method
            useCaches = false
            doInput = true
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (request.body != null) {
                doOutput = true
            }
        }

        try {
            if (request.body != null) {
                connection.outputStream.use { output ->
                    output.write(request.body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = readConnectorResponseBody(stream)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { (_, values) -> values.joinToString(",") }
            ConnectorHttpResponse(statusCode = status, headers = headers, body = body)
        } finally {
            connection.disconnect()
        }
    }
}

private const val MAX_CONNECTOR_RESPONSE_BYTES = 256 * 1024

/** Read a bounded response body so an unexpected endpoint cannot exhaust memory. */
internal fun readConnectorResponseBody(
    input: InputStream?,
    maxBytes: Int = MAX_CONNECTOR_RESPONSE_BYTES,
): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    if (input == null) return ""
    val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    input.use { stream ->
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (total > maxBytes - count) {
                throw GitHubOAuthException(
                    errorCode = "response_too_large",
                    message = "GitHub response exceeded the connector limit",
                )
            }
            output.write(buffer, 0, count)
            total += count
        }
    }
    return output.toString(Charsets.UTF_8.name())
}

data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val expiresAtEpochSeconds: Long,
    val pollIntervalSeconds: Long,
    val requestedScopes: Set<String>,
)

data class GitHubDeviceFlowStatus(
    val authorization: DeviceAuthorization,
    val errorCode: String,
    val nextPollIntervalSeconds: Long,
)

open class GitHubOAuthException(
    val errorCode: String,
    val httpStatus: Int? = null,
    message: String,
) : Exception(TokenRedactor.redact(message) ?: "GitHub OAuth request failed")

class DeviceFlowExpiredException : GitHubOAuthException(
    errorCode = "expired_token",
    message = "GitHub device authorization expired",
)

class DeviceFlowDeniedException : GitHubOAuthException(
    errorCode = "access_denied",
    message = "GitHub device authorization was denied",
)

/** OAuth credentials returned by GitHub's device flow. */
typealias GitHubOAuthCredentials = CredentialBundle

/**
 * GitHub OAuth App Device Flow client.
 *
 * The constructor intentionally accepts only a public client ID. A native
 * Android client must not carry an OAuth client secret.
 */
class GitHubOAuthDeviceFlowClient(
    clientId: String,
    private val http: ConnectorHttpClient,
    private val clock: ConnectorClock = SystemConnectorClock,
    private val delayController: ConnectorDelay = SystemConnectorDelay,
    private val json: Json = ConnectorJson.default,
) {
    private val clientId = clientId.trim()

    init {
        require(this.clientId.isNotEmpty()) { "GitHub OAuth client ID must not be blank" }
        require(!this.clientId.contains(' ')) { "GitHub OAuth client ID must not contain spaces" }
    }

    suspend fun requestDeviceAuthorization(
        scopes: Set<String> = GitHubConnectorCatalog.definition.defaultScopes,
    ): DeviceAuthorization {
        validateScopes(scopes)
        val request = ConnectorHttpRequest(
            method = "POST",
            url = GitHubEndpoints.DEVICE_CODE,
            headers = oauthHeaders(),
            body = formBody(
                "client_id" to clientId,
                "scope" to scopes.sorted().joinToString(" "),
            ),
        )
        val response = http.execute(request)
        val payload = parsePayload(response)
        if (response.statusCode !in 200..299) {
            throw oauthError(response, payload, fallbackCode = "device_code_request_failed")
        }

        val deviceCode = payload.requiredString("device_code")
        val userCode = payload.requiredString("user_code")
        val verificationUri = payload.requiredString("verification_uri")
        require(verificationUri == GitHubEndpoints.DEVICE_VERIFICATION) {
            "GitHub returned an unexpected verification URI"
        }
        val expiresIn = payload.requiredPositiveLong("expires_in")
        val interval = (payload.optionalPositiveLong("interval") ?: DEFAULT_POLL_INTERVAL_SECONDS)
            .coerceIn(1L, MAX_POLL_INTERVAL_SECONDS)
        val issuedAt = clock.nowEpochSeconds()
        val expiresAt = try {
            Math.addExact(issuedAt, expiresIn)
        } catch (_: ArithmeticException) {
            throw GitHubOAuthException("invalid_response", message = "GitHub response has invalid expires_in")
        }
        return DeviceAuthorization(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
                verificationUriComplete = payload.optionalString("verification_uri_complete")
                ?.takeIf(::isSafeVerificationUri),
            expiresAtEpochSeconds = expiresAt,
            pollIntervalSeconds = interval,
            requestedScopes = scopes,
        )
    }

    suspend fun authenticate(
        scopes: Set<String> = GitHubConnectorCatalog.definition.defaultScopes,
        onAuthorization: suspend (DeviceAuthorization) -> Unit = {},
        onStatus: suspend (GitHubDeviceFlowStatus) -> Unit = {},
    ): GitHubOAuthCredentials {
        val authorization = requestDeviceAuthorization(scopes)
        onAuthorization(authorization)
        return pollForToken(authorization, onStatus)
    }

    suspend fun pollForToken(
        authorization: DeviceAuthorization,
        onStatus: suspend (GitHubDeviceFlowStatus) -> Unit = {},
    ): GitHubOAuthCredentials {
        var intervalSeconds = authorization.pollIntervalSeconds.coerceIn(1L, MAX_POLL_INTERVAL_SECONDS)
        while (true) {
            currentCoroutineContext().ensureActive()
            if (clock.nowEpochSeconds() >= authorization.expiresAtEpochSeconds) {
                throw DeviceFlowExpiredException()
            }
            delayController.await(intervalSeconds * 1_000L)
            currentCoroutineContext().ensureActive()
            if (clock.nowEpochSeconds() >= authorization.expiresAtEpochSeconds) {
                throw DeviceFlowExpiredException()
            }

            val request = ConnectorHttpRequest(
                method = "POST",
                url = GitHubEndpoints.ACCESS_TOKEN,
                headers = oauthHeaders(),
                body = formBody(
                    "client_id" to clientId,
                    "device_code" to authorization.deviceCode,
                    "grant_type" to DEVICE_GRANT_TYPE,
                ),
            )
            val response = http.execute(request)
            val payload = parsePayload(response)
            if (response.statusCode in 200..299 && payload.optionalString("access_token") != null) {
                // The requested set is not proof of what GitHub granted. A missing
                // scope response is represented as an empty set for first login.
                return credentialsFrom(payload, emptySet())
            }

            val errorCode = payload.optionalString("error")
                ?: if (response.statusCode in 200..299) "missing_access_token" else "token_request_failed"
            when (errorCode) {
                "authorization_pending" -> {
                    onStatus(
                        GitHubDeviceFlowStatus(
                            authorization = authorization,
                            errorCode = errorCode,
                            nextPollIntervalSeconds = intervalSeconds,
                        ),
                    )
                }

                "slow_down" -> {
                    intervalSeconds = maxOf(
                        intervalSeconds + SLOW_DOWN_INCREMENT_SECONDS,
                        payload.optionalPositiveLong("interval") ?: 0L,
                    ).coerceAtMost(MAX_POLL_INTERVAL_SECONDS)
                    onStatus(
                        GitHubDeviceFlowStatus(
                            authorization = authorization,
                            errorCode = errorCode,
                            nextPollIntervalSeconds = intervalSeconds,
                        ),
                    )
                }

                "expired_token", "token_expired" -> throw DeviceFlowExpiredException()
                "access_denied" -> throw DeviceFlowDeniedException()
                else -> throw oauthError(response, payload, fallbackCode = errorCode)
            }
        }
    }

    /** Refresh a device-flow token pair without a client secret. */
    suspend fun refresh(
        refreshToken: String,
        grantedScopes: Set<String> = emptySet(),
    ): GitHubOAuthCredentials {
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank" }
        val response = http.execute(
            ConnectorHttpRequest(
                method = "POST",
                url = GitHubEndpoints.ACCESS_TOKEN,
                headers = oauthHeaders(),
                body = formBody(
                    "client_id" to clientId,
                    "grant_type" to REFRESH_GRANT_TYPE,
                    "refresh_token" to refreshToken,
                ),
            ),
        )
        val payload = parsePayload(response)
        if (response.statusCode !in 200..299 || payload.optionalString("access_token") == null) {
            throw oauthError(response, payload, fallbackCode = "refresh_failed")
        }
        return credentialsFrom(payload, grantedScopes)
    }

    private fun validateScopes(scopes: Set<String>) {
        require(scopes.isNotEmpty() && scopes.all { it in GitHubConnectorCatalog.definition.defaultScopes }) {
            "GitHub OAuth scopes must stay within the connector's least-privilege defaults"
        }
    }

    private fun oauthHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Content-Type" to "application/x-www-form-urlencoded",
        "X-GitHub-Api-Version" to GitHubEndpoints.API_VERSION,
        "User-Agent" to "Android-Agent-Connectors",
    )

    private fun credentialsFrom(payload: JsonObject, fallbackScopes: Set<String>): GitHubOAuthCredentials {
        val now = clock.nowEpochSeconds()
        val accessToken = payload.requiredString("access_token")
        val refreshToken = payload.optionalString("refresh_token")
        val grantedScopes = parseScopes(payload.optionalString("scope"), fallbackScopes)
        return CredentialBundle(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = payload.optionalString("token_type") ?: "bearer",
            accessTokenExpiresAtEpochSeconds = expiresAt(payload, "expires_in", now),
            refreshTokenExpiresAtEpochSeconds = payload.optionalPositiveLong("refresh_token_expires_in")?.let {
                addExpiry(now, it, "refresh_token_expires_in")
            },
            grantedScopes = grantedScopes,
        )
    }

    private fun expiresAt(payload: JsonObject, key: String, now: Long): Long =
        addExpiry(now, payload.requiredPositiveLong(key), key)

    private fun addExpiry(now: Long, lifetimeSeconds: Long, key: String): Long = try {
        Math.addExact(now, lifetimeSeconds)
    } catch (_: ArithmeticException) {
        throw GitHubOAuthException("invalid_response", message = "GitHub response has invalid $key")
    }

    private fun parsePayload(response: ConnectorHttpResponse): JsonObject {
        return runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrElse {
            throw GitHubOAuthException(
                errorCode = "invalid_json",
                httpStatus = response.statusCode,
                message = "GitHub returned invalid OAuth JSON",
            )
        }
    }

    private fun oauthError(
        response: ConnectorHttpResponse,
        payload: JsonObject,
        fallbackCode: String,
    ): GitHubOAuthException {
        val code = payload.optionalString("error") ?: fallbackCode
        val description = payload.optionalString("error_description")
            ?: payload.optionalString("message")
            ?: "GitHub OAuth request failed"
        return GitHubOAuthException(code, response.statusCode, description)
    }

    private fun formBody(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${formEncode(key)}=${formEncode(value)}"
    }

    private fun formEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun parseScopes(raw: String?, fallback: Set<String>): Set<String> {
        val parsed = raw.orEmpty().split(',', ' ').map(String::trim).filter(String::isNotEmpty).toSet()
        return if (parsed.isEmpty()) fallback else parsed
    }

    private fun isSafeVerificationUri(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null &&
            (uri.path == GitHubEndpoints.DEVICE_VERIFICATION.removePrefix("https://github.com") ||
                uri.path.startsWith("${GitHubEndpoints.DEVICE_VERIFICATION.removePrefix("https://github.com")}/"))
    }.getOrDefault(false)

    private fun JsonObject.requiredString(key: String): String =
        optionalString(key) ?: throw GitHubOAuthException("invalid_response", message = "GitHub response missing $key")

    private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredPositiveLong(key: String): Long =
        optionalPositiveLong(key) ?: throw GitHubOAuthException("invalid_response", message = "GitHub response has invalid $key")

    private fun JsonObject.optionalPositiveLong(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        const val SLOW_DOWN_INCREMENT_SECONDS = 5L
        const val MAX_POLL_INTERVAL_SECONDS = 60L
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val REFRESH_GRANT_TYPE = "refresh_token"
    }
}
