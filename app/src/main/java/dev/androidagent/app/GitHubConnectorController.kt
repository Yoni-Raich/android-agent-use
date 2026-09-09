package dev.androidagent.app

import android.app.Application
import dev.androidagent.app.ui.GitHubConnectorUiState
import dev.androidagent.connectors.AndroidKeystoreCredentialVault
import dev.androidagent.connectors.ConnectorHttpRequest
import dev.androidagent.connectors.ConnectorSnapshot
import dev.androidagent.connectors.ConnectorState
import dev.androidagent.connectors.ConnectorStateCorruptException
import dev.androidagent.connectors.CredentialBundle
import dev.androidagent.connectors.CredentialStoreCorruptException
import dev.androidagent.connectors.GitHubConnectorCatalog
import dev.androidagent.connectors.GitHubEndpoints
import dev.androidagent.connectors.GitHubOAuthDeviceFlowClient
import dev.androidagent.connectors.GitHubOAuthException
import dev.androidagent.connectors.PermissionMode
import dev.androidagent.connectors.SharedPreferencesConnectorStateStore
import dev.androidagent.connectors.ToolSchemaFingerprint
import dev.androidagent.connectors.ConnectorToolSchema
import dev.androidagent.connectors.UrlConnectionHttpClient
import dev.androidagent.core.AgentEngine
import dev.androidagent.core.ConnectorEngineControl
import dev.androidagent.core.McpHttpServerConfig
import dev.androidagent.core.McpRuntimePhase
import dev.androidagent.core.McpServerSnapshot
import dev.androidagent.core.McpToolApprovalMode
import dev.androidagent.core.RuntimeConnector
import dev.androidagent.core.SecretRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Bridges provider auth and secure storage to the app-server MCP control plane. */
class GitHubConnectorController(
    app: Application,
    private val clientId: String,
) : RuntimeConnector {
    private val preferences = app.getSharedPreferences("connectors", 0)
    private val vault = AndroidKeystoreCredentialVault(app)
    private val stateStore = SharedPreferencesConnectorStateStore(app)
    private val http = UrlConnectionHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var engine: AgentEngine? = null
    private var control: ConnectorEngineControl? = null
    private val lifecycleLock = Mutex()
    @Volatile private var cachedPermissionMode = PermissionMode.ASK_BEFORE_WRITES
    private val mutable = MutableStateFlow(initialState())

    /** Loaded on IO before the app-server is started; runtime reads only memory. */
    @Volatile private var cachedCredentials: CredentialBundle? = null
    @Volatile private var cachedSnapshot: ConnectorSnapshot? = null

    val state: StateFlow<GitHubConnectorUiState> = mutable.asStateFlow()

    override fun snapshot(): Map<String, String> = cachedCredentials
        ?.takeIf { cachedSnapshot?.state == ConnectorState.CONNECTED && tokenIsUsable(it) }
        ?.let { mapOf(GitHubEndpoints.REMOTE_MCP_TOKEN_ENVIRONMENT to it.accessToken) }
        .orEmpty()

    override fun allowedHttpsHosts(): Set<String> = if (
        cachedCredentials?.let(::tokenIsUsable) == true && cachedSnapshot?.state == ConnectorState.CONNECTED
    ) {
        setOf("api.githubcopilot.com")
    } else {
        emptySet()
    }

    fun attach(engine: AgentEngine, control: ConnectorEngineControl) {
        this.engine = engine
        this.control = control
    }

    suspend fun synchronize(forceConfiguration: Boolean = true) = lifecycleLock.withLock {
        loadPermissionMode()
        val saved = try {
            withContext(Dispatchers.IO) { stateStore.get(GitHubConnectorCatalog.ID) }
        } catch (_: ConnectorStateCorruptException) {
            // A damaged non-secret snapshot must not destroy a still-readable
            // credential. Keep it sealed, but force a fresh connector state so
            // the token is never used without an explicit re-authentication.
            val preservedCredentials = try {
                withContext(Dispatchers.IO) { vault.read(CREDENTIAL_KEY) }
            } catch (_: CredentialStoreCorruptException) {
                withContext(Dispatchers.IO) { vault.clear(CREDENTIAL_KEY) }
                null
            }
            val reset = preservedCredentials?.let {
                ConnectorSnapshot(
                    connectorId = GitHubConnectorCatalog.ID,
                    state = ConnectorState.REAUTH_REQUIRED,
                    grantedScopes = it.grantedScopes,
                    accessTokenExpiresAtEpochSeconds = it.accessTokenExpiresAtEpochSeconds,
                    refreshTokenExpiresAtEpochSeconds = it.refreshTokenExpiresAtEpochSeconds,
                    lastErrorCode = "state_corrupt",
                    lastUpdatedAtEpochSeconds = nowSeconds(),
                )
            }
            withContext(Dispatchers.IO) { reset?.let { stateStore.put(it) } }
            cachedCredentials = null
            cachedSnapshot = reset
            mutable.value = mutable.value.copy(
                connected = false,
                accountLogin = null,
                status = if (reset == null) {
                    "GitHub connector state was reset. Connect again."
                } else {
                    "GitHub connector state was reset. Connect again; stored credentials were kept safe."
                },
            )
            return@withLock
        }
        val stored = try {
            withContext(Dispatchers.IO) { vault.read(CREDENTIAL_KEY) }
        } catch (_: CredentialStoreCorruptException) {
            cachedCredentials = null
            withContext(Dispatchers.IO) { vault.clear(CREDENTIAL_KEY) }
            val reauth = saved?.copy(
                state = ConnectorState.REAUTH_REQUIRED,
                lastErrorCode = "credential_corrupt",
                lastUpdatedAtEpochSeconds = nowSeconds(),
            )
            withContext(Dispatchers.IO) {
                reauth?.let { stateStore.put(it) }
            }
            cachedSnapshot = reauth
            mutable.value = mutable.value.copy(
                connected = false,
                accountLogin = saved?.accountLogin,
                status = "GitHub credentials could not be opened. Connect again.",
            )
            return@withLock
        }
        if (stored == null) {
            cachedCredentials = null
            val missing = saved?.takeIf { it.state != ConnectorState.DISCONNECTED }?.copy(
                state = ConnectorState.REAUTH_REQUIRED,
                lastErrorCode = "credential_missing",
                lastUpdatedAtEpochSeconds = nowSeconds(),
            )
            withContext(Dispatchers.IO) {
                missing?.let { stateStore.put(it) }
            }
            cachedSnapshot = missing ?: saved
            mutable.value = mutable.value.copy(
                connected = false,
                accountLogin = saved?.accountLogin,
                status = if (missing == null) "Not connected" else "GitHub credential missing. Connect again.",
            )
            return@withLock
        }
        if (saved != null && saved.state != ConnectorState.CONNECTED) {
            cachedCredentials = null
            val attention = if (saved.state == ConnectorState.AUTHORIZING) {
                val interrupted = saved.copy(
                    state = ConnectorState.REAUTH_REQUIRED,
                    lastErrorCode = "authorization_interrupted",
                    lastUpdatedAtEpochSeconds = nowSeconds(),
                )
                withContext(Dispatchers.IO) {
                    vault.clear(CREDENTIAL_KEY)
                    stateStore.put(interrupted)
                }
                interrupted
            } else {
                saved
            }
            cachedSnapshot = attention
            mutable.value = mutable.value.copy(
                connected = false,
                accountLogin = attention.accountLogin,
                status = "GitHub sign-in needs attention. Connect again.",
            )
            return@withLock
        }

        var snapshot = saved ?: ConnectorSnapshot(
            connectorId = GitHubConnectorCatalog.ID,
            state = ConnectorState.CONNECTED,
            grantedScopes = stored.grantedScopes,
            accessTokenExpiresAtEpochSeconds = stored.accessTokenExpiresAtEpochSeconds,
            refreshTokenExpiresAtEpochSeconds = stored.refreshTokenExpiresAtEpochSeconds,
            lastUpdatedAtEpochSeconds = nowSeconds(),
        )
        if (saved == null) {
            withContext(Dispatchers.IO) { stateStore.put(snapshot) }
        }
        var refreshWarning: String? = null
        val refreshed = when (val outcome = withContext(Dispatchers.IO) { refreshIfNeeded(stored) }) {
            is RefreshOutcome.Ready -> outcome.credentials
            is RefreshOutcome.Reauthenticate -> {
                cachedCredentials = null
                val expired = snapshot.copy(
                    state = ConnectorState.REAUTH_REQUIRED,
                    lastErrorCode = outcome.errorCode,
                    lastUpdatedAtEpochSeconds = nowSeconds(),
                )
                withContext(Dispatchers.IO) {
                    vault.clear(CREDENTIAL_KEY)
                    stateStore.put(expired)
                }
                cachedSnapshot = expired
                mutable.value = mutable.value.copy(
                    connected = false,
                    accountLogin = snapshot.accountLogin,
                    status = "GitHub sign-in expired. Connect again.",
                )
                return@withLock
            }
            is RefreshOutcome.Retryable -> {
                // Keep the still-valid access/refresh pair. A network error
                // must not turn a temporary outage into forced re-auth.
                refreshWarning = "GitHub token refresh is temporarily unavailable. Retry shortly."
                if (!tokenIsUsable(stored)) {
                    cachedCredentials = stored
                    val blocked = snapshot.copy(
                        state = ConnectorState.CONNECTED,
                        lastErrorCode = "token_refresh_retry",
                        lastUpdatedAtEpochSeconds = nowSeconds(),
                    )
                    withContext(Dispatchers.IO) { stateStore.put(blocked) }
                    cachedSnapshot = blocked
                    mutable.value = mutable.value.copy(
                        connected = false,
                        accountLogin = snapshot.accountLogin,
                        status = "GitHub token refresh is required before use. Retry shortly.",
                    )
                    return@withLock
                }
                stored
            }
        }

        val hadConnectedRuntime = cachedSnapshot?.state == ConnectorState.CONNECTED
        val tokenChanged = refreshed.accessToken != stored.accessToken
        cachedCredentials = refreshed
        snapshot = snapshot.copy(
            state = ConnectorState.CONNECTED,
            grantedScopes = refreshed.grantedScopes,
            accessTokenExpiresAtEpochSeconds = refreshed.accessTokenExpiresAtEpochSeconds,
            refreshTokenExpiresAtEpochSeconds = refreshed.refreshTokenExpiresAtEpochSeconds,
            lastErrorCode = if (refreshWarning == null) null else "token_refresh_retry",
            lastUpdatedAtEpochSeconds = nowSeconds(),
        )
        withContext(Dispatchers.IO) { stateStore.put(snapshot) }
        cachedSnapshot = snapshot
        if (forceConfiguration || tokenChanged || !hadConnectedRuntime) {
            try {
                configureAndInspect(restart = tokenChanged)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                markConfigurationFailure(failure, snapshot)
            }
        } else if (refreshWarning != null) {
            mutable.value = mutable.value.copy(
                connected = true,
                accountLogin = snapshot.accountLogin,
                status = refreshWarning,
            )
        }
    }

    suspend fun connect() = lifecycleLock.withLock {
        loadPermissionMode()
        check(clientId.isNotBlank()) {
            "This build has no GitHub OAuth client ID. Set githubOAuthClientId in Gradle properties."
        }
        mutable.value = mutable.value.copy(
            connecting = true,
            userCode = null,
            verificationUrl = null,
            status = "Starting GitHub sign-in…",
        )
        try {
            val credentials = GitHubOAuthDeviceFlowClient(clientId, http).authenticate(
                scopes = GitHubConnectorCatalog.definition.defaultScopes,
                onAuthorization = { authorization ->
                    mutable.value = mutable.value.copy(
                        userCode = authorization.userCode,
                        verificationUrl = authorization.verificationUriComplete ?: authorization.verificationUri,
                        status = "Enter this code on GitHub",
                    )
                },
                onStatus = { mutable.value = mutable.value.copy(status = "Waiting for GitHub approval…") },
            )

            // Persist first so a successful grant is recoverable even if the
            // identity request is temporarily rate-limited or offline. If the
            // verification fails, the provisional grant is cleared below.
            val provisional = ConnectorSnapshot(
                connectorId = GitHubConnectorCatalog.ID,
                state = ConnectorState.AUTHORIZING,
                grantedScopes = credentials.grantedScopes,
                accessTokenExpiresAtEpochSeconds = credentials.accessTokenExpiresAtEpochSeconds,
                refreshTokenExpiresAtEpochSeconds = credentials.refreshTokenExpiresAtEpochSeconds,
                lastUpdatedAtEpochSeconds = nowSeconds(),
            )
            withContext(Dispatchers.IO) {
                vault.write(CREDENTIAL_KEY, credentials)
                stateStore.put(provisional)
            }
            cachedSnapshot = provisional
            cachedCredentials = credentials

            val identity = try {
                loadIdentity(credentials.accessToken)
            } catch (failure: Throwable) {
                withContext(Dispatchers.IO) {
                    vault.clear(CREDENTIAL_KEY)
                    stateStore.remove(GitHubConnectorCatalog.ID)
                }
                cachedCredentials = null
                cachedSnapshot = null
                throw failure
            }
            val snapshot = provisional.copy(
                state = ConnectorState.CONNECTED,
                accountLogin = identity.first,
                accountId = identity.second,
            )
            withContext(Dispatchers.IO) { stateStore.put(snapshot) }
            cachedSnapshot = snapshot
            mutable.value = mutable.value.copy(
                connected = true,
                connecting = false,
                accountLogin = identity.first,
                userCode = null,
                verificationUrl = null,
                status = "Connected",
            )
            // The token is part of the app-server process environment. Restart
            // once when replacing credentials so the old value cannot remain.
            try {
                configureAndInspect(restart = true)
            } catch (failure: Throwable) {
                markConfigurationFailure(failure, snapshot)
                throw failure
            }
        } catch (cancelled: CancellationException) {
            mutable.value = mutable.value.copy(
                connecting = false,
                userCode = null,
                verificationUrl = null,
                status = if (mutable.value.connected) "Connected" else "Sign-in cancelled",
            )
            throw cancelled
        } catch (failure: Throwable) {
            mutable.value = mutable.value.copy(
                connecting = false,
                userCode = null,
                verificationUrl = null,
                status = safeFailure(failure, "GitHub sign-in failed"),
            )
            throw failure
        }
    }

    suspend fun disconnect() = lifecycleLock.withLock {
        var removedFromMcp = control != null
        control?.let { connector ->
            try {
                connector.removeMcpServer(SERVER_NAME)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                removedFromMcp = false
            }
            try {
                connector.reloadMcpServers()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                removedFromMcp = false
            }
        }
        if (!removedFromMcp) {
            try { engine?.close() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        }
        cachedCredentials = null
        cachedSnapshot = null
        withContext(Dispatchers.IO) {
            vault.clear(CREDENTIAL_KEY)
            stateStore.remove(GitHubConnectorCatalog.ID)
        }
        mutable.value = GitHubConnectorUiState(
            available = clientId.isNotBlank(),
            permissionMode = permissionMode(),
            status = "Not connected",
        )
    }

    suspend fun setPermissionMode(mode: PermissionMode) = lifecycleLock.withLock {
        withContext(Dispatchers.IO) {
            check(preferences.edit().putString(PERMISSION_KEY, mode.name).commit()) {
                "Could not persist GitHub permission mode"
            }
        }
        cachedPermissionMode = mode
        mutable.value = mutable.value.copy(permissionMode = mode)
        if (mutable.value.connected) {
            val snapshot = cachedSnapshot
            try {
                configureAndInspect(restart = false)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                snapshot?.let { markConfigurationFailure(failure, it) }
                throw failure
            }
        }
    }

    private suspend fun configureAndInspect(restart: Boolean) {
        val currentEngine = engine ?: return
        val connector = control ?: return
        if (restart) currentEngine.close()
        currentEngine.connect()

        var mode = permissionMode()
        var server: McpServerSnapshot? = null
        var fingerprint: String? = null
        var saved = withContext(Dispatchers.IO) { stateStore.get(GitHubConnectorCatalog.ID) }
            ?: cachedSnapshot?.also { fallback ->
                withContext(Dispatchers.IO) { stateStore.put(fallback) }
            }
        var downgraded = false
        repeat(2) {
            connector.configureMcpServer(
                McpHttpServerConfig(
                    name = SERVER_NAME,
                    url = GitHubEndpoints.REMOTE_MCP,
                    bearerTokenEnvironmentVariable = GitHubEndpoints.REMOTE_MCP_TOKEN_ENVIRONMENT,
                    approvalMode = approvalModeFor(mode),
                    // The header is a provider hint, not a local security
                    // boundary. PROMPT is the local guarantee: no tool can
                    // run in this mode without the user's approval.
                    httpHeaders = if (mode == PermissionMode.READ_ONLY) {
                        mapOf("X-MCP-Readonly" to "true")
                    } else emptyMap(),
                ),
            )
            connector.reloadMcpServers()
            val listed = connector.listMcpServers().firstOrNull { it.name == SERVER_NAME }
            server = listed
            fingerprint = listed?.let {
                ToolSchemaFingerprint.forTools(
                    GitHubConnectorCatalog.ID,
                    "mcp-http",
                    it.tools.map { tool ->
                        ConnectorToolSchema(tool.name, tool.description, tool.inputSchema, tool.readOnly)
                    },
                )
            }
            if (
                saved?.toolSchemaFingerprint != null &&
                fingerprint != null &&
                saved?.toolSchemaFingerprint != fingerprint &&
                mode == PermissionMode.FULL_CONTROL
            ) {
                mode = PermissionMode.ASK_BEFORE_WRITES
                downgraded = true
                withContext(Dispatchers.IO) {
                    check(preferences.edit().putString(PERMISSION_KEY, mode.name).commit()) {
                        "Could not persist GitHub permission mode"
                    }
                }
                cachedPermissionMode = mode
                mutable.value = mutable.value.copy(permissionMode = mode)
            } else {
                return@repeat
            }
        }

        val current = server
        val savedFingerprint = saved?.toolSchemaFingerprint
        val needsAuth = current?.phase == McpRuntimePhase.AUTHENTICATION_REQUIRED ||
            current?.authStatus?.lowercase() in setOf("expired", "unauthenticated", "authentication_required")
        val connected = current?.phase == McpRuntimePhase.CONNECTED && !needsAuth && current.error == null
        val nextState = when {
            connected -> saved?.copy(
                state = ConnectorState.CONNECTED,
                lastErrorCode = null,
                toolSchemaFingerprint = fingerprint ?: savedFingerprint,
                lastUpdatedAtEpochSeconds = nowSeconds(),
            )
            needsAuth -> saved?.copy(
                state = ConnectorState.REAUTH_REQUIRED,
                lastErrorCode = "authentication_required",
                toolSchemaFingerprint = fingerprint ?: savedFingerprint,
                lastUpdatedAtEpochSeconds = nowSeconds(),
            )
            else -> saved?.copy(
                state = ConnectorState.ERROR,
                lastErrorCode = if (current == null) "mcp_status_missing" else "mcp_${current.phase.name.lowercase()}",
                toolSchemaFingerprint = fingerprint ?: savedFingerprint,
                lastUpdatedAtEpochSeconds = nowSeconds(),
            )
        }
        if (nextState != null) {
            withContext(Dispatchers.IO) { stateStore.put(nextState) }
            saved = nextState
        }
        cachedSnapshot = saved
        if (!connected && (needsAuth || current?.phase == McpRuntimePhase.FAILED || current?.error != null)) {
            try { currentEngine.close() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        }
        mutable.value = mutable.value.copy(
            connected = connected,
            connecting = false,
            accountLogin = saved?.accountLogin ?: mutable.value.accountLogin,
            toolCount = current?.tools?.size ?: 0,
            status = when {
                downgraded -> "GitHub tools changed. Write approvals were turned back on."
                needsAuth -> "GitHub sign-in expired. Connect again."
                current == null -> "GitHub MCP status is unavailable"
                current.error != null -> safeFailure(IllegalStateException(current.error), "GitHub MCP reported an error")
                current.phase != McpRuntimePhase.CONNECTED -> "GitHub MCP is ${current.phase.name.lowercase()}"
                else -> "Connected · ${current.tools.size} tools"
            },
        )
    }

    private suspend fun refreshIfNeeded(credentials: CredentialBundle): RefreshOutcome {
        val expiresAt = credentials.accessTokenExpiresAtEpochSeconds
            ?: return RefreshOutcome.Reauthenticate("access_token_expiry_missing")
        if (expiresAt > nowSeconds() + TOKEN_REFRESH_LEEWAY_SECONDS) {
            return RefreshOutcome.Ready(credentials)
        }
        val refreshToken = credentials.refreshToken
            ?: return RefreshOutcome.Reauthenticate("refresh_token_missing")
        if (clientId.isBlank()) return RefreshOutcome.Reauthenticate("oauth_client_missing")
        return try {
            val refreshed = GitHubOAuthDeviceFlowClient(clientId, http)
                .refresh(refreshToken, credentials.grantedScopes)
            vault.write(CREDENTIAL_KEY, refreshed)
            RefreshOutcome.Ready(refreshed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: GitHubOAuthException) {
            if (failure.errorCode in REAUTH_ERROR_CODES) {
                RefreshOutcome.Reauthenticate(failure.errorCode)
            } else {
                RefreshOutcome.Retryable(failure)
            }
        } catch (failure: Exception) {
            // Network failures, malformed responses, and rate limits are
            // retryable. Never delete a refresh token for those cases.
            RefreshOutcome.Retryable(failure)
        }
    }

    private suspend fun loadIdentity(token: String): Pair<String, Long?> {
        val response = http.execute(
            ConnectorHttpRequest(
                method = "GET",
                url = "${GitHubEndpoints.API_BASE}user",
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to GitHubEndpoints.API_VERSION,
                ),
            ),
        )
        check(response.statusCode in 200..299) {
            "GitHub account verification failed (${response.statusCode})."
        }
        val body = json.parseToJsonElement(response.body).jsonObject
        val login = body["login"]?.jsonPrimitive?.contentOrNull.orEmpty()
        check(login.isNotBlank()) { "GitHub account response did not include a login." }
        return login to body["id"]?.jsonPrimitive?.longOrNull
    }

    private suspend fun markConfigurationFailure(failure: Throwable, snapshot: ConnectorSnapshot) {
        try { engine?.close() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        val safe = safeFailure(failure, "GitHub MCP configuration failed")
        val failed = snapshot.copy(
            state = ConnectorState.ERROR,
            lastErrorCode = "mcp_configuration_failed",
            lastUpdatedAtEpochSeconds = nowSeconds(),
        )
        cachedSnapshot = failed
        mutable.value = mutable.value.copy(connected = false, connecting = false, status = safe)
        withContext(Dispatchers.IO) { runCatching { stateStore.put(failed) } }
    }

    private fun initialState(): GitHubConnectorUiState = GitHubConnectorUiState(
        available = clientId.isNotBlank(),
        permissionMode = permissionMode(),
        status = if (clientId.isBlank()) {
            "GitHub OAuth is not configured in this build"
        } else {
            "Not connected"
        },
    )

    private fun permissionMode(): PermissionMode = cachedPermissionMode

    private suspend fun loadPermissionMode() {
        cachedPermissionMode = withContext(Dispatchers.IO) {
            runCatching {
                PermissionMode.valueOf(preferences.getString(PERMISSION_KEY, null).orEmpty())
            }.getOrDefault(PermissionMode.ASK_BEFORE_WRITES)
        }
        mutable.value = mutable.value.copy(permissionMode = cachedPermissionMode)
    }

    private fun safeFailure(failure: Throwable, fallback: String): String =
        SecretRedactor.redact(failure.message ?: fallback).take(500).ifBlank { fallback }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1_000L

    private fun tokenIsUsable(credentials: CredentialBundle): Boolean =
        credentials.accessTokenExpiresAtEpochSeconds?.let { it > nowSeconds() } == true

    private sealed interface RefreshOutcome {
        data class Ready(val credentials: CredentialBundle) : RefreshOutcome
        data class Reauthenticate(val errorCode: String) : RefreshOutcome
        data class Retryable(val failure: Throwable) : RefreshOutcome
    }

    internal companion object {
        const val SERVER_NAME = "github"
        const val CREDENTIAL_KEY = "github.oauth"
        const val PERMISSION_KEY = "github.permission"
        const val TOKEN_REFRESH_LEEWAY_SECONDS = 60L
        val REAUTH_ERROR_CODES = setOf(
            "access_denied",
            "expired_token",
            "invalid_client",
            "invalid_grant",
            "unauthorized_client",
        )

        internal fun approvalModeFor(mode: PermissionMode): McpToolApprovalMode = when (mode) {
            PermissionMode.READ_ONLY -> McpToolApprovalMode.PROMPT
            PermissionMode.ASK_BEFORE_WRITES -> McpToolApprovalMode.WRITES
            PermissionMode.FULL_CONTROL -> McpToolApprovalMode.APPROVE
        }
    }
}
