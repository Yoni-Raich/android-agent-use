package dev.androidagent.connectors

import kotlinx.serialization.Serializable

/** The amount of authority a connector is allowed to request or exercise. */
@Serializable
enum class PermissionMode {
    READ_ONLY,
    ASK_BEFORE_WRITES,
    FULL_CONTROL,
}

/** Authentication mechanism advertised by a connector definition. */
@Serializable
enum class AuthStrategy {
    NONE,
    GITHUB_OAUTH_APP_DEVICE_FLOW,
}

/** Stable, provider-neutral description of a connector. */
@Serializable
data class ConnectorDefinition(
    val id: String,
    val displayName: String,
    val permissionMode: PermissionMode,
    val authStrategy: AuthStrategy,
    val supportedScopes: Set<String> = emptySet(),
    val defaultScopes: Set<String> = emptySet(),
    val apiBaseUrl: String? = null,
    val authBaseUrl: String? = null,
    val capabilities: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "Connector id must not be blank" }
        require(displayName.isNotBlank()) { "Connector display name must not be blank" }
        require(defaultScopes.all { it in supportedScopes }) {
            "Default scopes must be a subset of supported scopes"
        }
    }
}

/** Runtime state of one connector. Tokens never belong in this object. */
@Serializable
enum class ConnectorState {
    DISCONNECTED,
    AUTHORIZING,
    CONNECTED,
    REAUTH_REQUIRED,
    ERROR,
}

/** Non-secret state that is safe to persist in app-private preferences. */
@Serializable
data class ConnectorSnapshot(
    val connectorId: String,
    val state: ConnectorState = ConnectorState.DISCONNECTED,
    val accountLogin: String? = null,
    val accountId: Long? = null,
    val repositoryIds: Set<Long> = emptySet(),
    val repositoryNames: Set<String> = emptySet(),
    val grantedScopes: Set<String> = emptySet(),
    val accessTokenExpiresAtEpochSeconds: Long? = null,
    val refreshTokenExpiresAtEpochSeconds: Long? = null,
    val toolSchemaFingerprint: String? = null,
    val lastErrorCode: String? = null,
    val lastUpdatedAtEpochSeconds: Long? = null,
)

/** An access/refresh token pair kept behind [CredentialVault]. */
@Serializable
data class CredentialBundle(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "bearer",
    val accessTokenExpiresAtEpochSeconds: Long? = null,
    val refreshTokenExpiresAtEpochSeconds: Long? = null,
    val grantedScopes: Set<String> = emptySet(),
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank" }
        require(refreshToken == null || refreshToken.isNotBlank()) {
            "Refresh token must be non-blank when present"
        }
        require(tokenType.isNotBlank()) { "Token type must not be blank" }
    }
}

interface ConnectorStateStore {
    fun get(connectorId: String): ConnectorSnapshot?
    fun put(snapshot: ConnectorSnapshot)
    fun remove(connectorId: String)
}

interface CredentialVault {
    fun read(key: String): CredentialBundle?
    fun write(key: String, credentials: CredentialBundle)
    fun clear(key: String)
}

class ConnectorStateCorruptException(
    val connectorId: String,
    cause: Throwable? = null,
) : IllegalStateException("Stored connector state is corrupt", cause)

class CredentialStoreCorruptException(
    val credentialKey: String,
    cause: Throwable? = null,
) : IllegalStateException("Stored connector credential could not be decrypted", cause)

/** In-memory state store for unit tests and short-lived connector sessions. */
class InMemoryConnectorStateStore : ConnectorStateStore {
    private val values = linkedMapOf<String, ConnectorSnapshot>()

    @Synchronized
    override fun get(connectorId: String): ConnectorSnapshot? = values[connectorId]

    @Synchronized
    override fun put(snapshot: ConnectorSnapshot) {
        require(snapshot.connectorId.isNotBlank()) { "Connector id must not be blank" }
        values[snapshot.connectorId] = snapshot
    }

    @Synchronized
    override fun remove(connectorId: String) {
        values.remove(connectorId)
    }
}

/** In-memory vault used by JVM tests; production code should use the Keystore vault. */
class InMemoryCredentialVault : CredentialVault {
    private val values = linkedMapOf<String, CredentialBundle>()

    @Synchronized
    override fun read(key: String): CredentialBundle? = values[key]

    @Synchronized
    override fun write(key: String, credentials: CredentialBundle) {
        require(key.isNotBlank()) { "Credential key must not be blank" }
        values[key] = credentials
    }

    @Synchronized
    override fun clear(key: String) {
        values.remove(key)
    }
}
