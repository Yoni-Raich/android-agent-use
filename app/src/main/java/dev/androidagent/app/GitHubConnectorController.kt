package dev.androidagent.app

import android.app.Application
import dev.androidagent.app.ui.GitHubConnectorUiState
import dev.androidagent.connectors.AndroidKeystoreCredentialVault
import dev.androidagent.connectors.ConnectorHttpRequest
import dev.androidagent.connectors.ConnectorSnapshot
import dev.androidagent.connectors.ConnectorState
import dev.androidagent.connectors.GitHubConnectorCatalog
import dev.androidagent.connectors.GitHubEndpoints
import dev.androidagent.connectors.GitHubOAuthDeviceFlowClient
import dev.androidagent.connectors.GitHubOAuthScopes
import dev.androidagent.connectors.PermissionMode
import dev.androidagent.connectors.SharedPreferencesConnectorStateStore
import dev.androidagent.connectors.ToolSchemaFingerprint
import dev.androidagent.connectors.ConnectorToolSchema
import dev.androidagent.connectors.UrlConnectionHttpClient
import dev.androidagent.core.AgentEngine
import dev.androidagent.core.ConnectorEngineControl
import dev.androidagent.core.McpHttpServerConfig
import dev.androidagent.core.McpToolApprovalMode
import dev.androidagent.core.RuntimeEgressPolicy
import dev.androidagent.core.RuntimeEnvironmentOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Bridges provider auth and secure storage to the app-server MCP control plane. */
class GitHubConnectorController(
    app: Application,
    private val clientId: String,
) : RuntimeEnvironmentOverlay, RuntimeEgressPolicy {
    private val preferences = app.getSharedPreferences("connectors", 0)
    private val vault = AndroidKeystoreCredentialVault(app)
    private val stateStore = SharedPreferencesConnectorStateStore(app)
    private val http = UrlConnectionHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var engine: AgentEngine? = null
    private var control: ConnectorEngineControl? = null
    private val mutable = MutableStateFlow(initialState())
    val state: StateFlow<GitHubConnectorUiState> = mutable.asStateFlow()

    override fun snapshot(): Map<String, String> = vault.read(CREDENTIAL_KEY)?.let { credentials ->
        mapOf(GitHubEndpoints.REMOTE_MCP_TOKEN_ENVIRONMENT to credentials.accessToken)
    }.orEmpty()

    override fun allowedHttpsHosts(): Set<String> =
        if (vault.read(CREDENTIAL_KEY) == null) emptySet() else setOf("api.githubcopilot.com")

    fun attach(engine: AgentEngine, control: ConnectorEngineControl) {
        this.engine = engine
        this.control = control
    }

    suspend fun synchronize() {
        if (vault.read(CREDENTIAL_KEY) != null) configureAndInspect(restart = false)
    }

    suspend fun connect() {
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
                scopes = GitHubOAuthScopes.supported,
                onAuthorization = { authorization ->
                    mutable.value = mutable.value.copy(
                        userCode = authorization.userCode,
                        verificationUrl = authorization.verificationUriComplete ?: authorization.verificationUri,
                        status = "Enter this code on GitHub",
                    )
                },
                onStatus = { mutable.value = mutable.value.copy(status = "Waiting for GitHub approval…") },
            )
            val identity = loadIdentity(credentials.accessToken)
            vault.write(CREDENTIAL_KEY, credentials)
            stateStore.put(
                ConnectorSnapshot(
                    connectorId = GitHubConnectorCatalog.ID,
                    state = ConnectorState.CONNECTED,
                    accountLogin = identity.first,
                    accountId = identity.second,
                    grantedScopes = credentials.grantedScopes,
                    accessTokenExpiresAtEpochSeconds = credentials.accessTokenExpiresAtEpochSeconds,
                    refreshTokenExpiresAtEpochSeconds = credentials.refreshTokenExpiresAtEpochSeconds,
                    lastUpdatedAtEpochSeconds = nowSeconds(),
                ),
            )
            mutable.value = mutable.value.copy(
                connected = true,
                connecting = false,
                accountLogin = identity.first,
                userCode = null,
                verificationUrl = null,
                status = "Connected",
            )
            configureAndInspect(restart = true)
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
                status = failure.message ?: "GitHub sign-in failed",
            )
            throw failure
        }
    }

    suspend fun disconnect() {
        control?.let { connector ->
            runCatching { connector.removeMcpServer(SERVER_NAME) }
            runCatching { connector.reloadMcpServers() }
        }
        engine?.close()
        vault.clear(CREDENTIAL_KEY)
        stateStore.remove(GitHubConnectorCatalog.ID)
        mutable.value = GitHubConnectorUiState(
            available = clientId.isNotBlank(),
            permissionMode = permissionMode(),
            status = "Not connected",
        )
    }

    suspend fun setPermissionMode(mode: PermissionMode) {
        preferences.edit().putString(PERMISSION_KEY, mode.name).apply()
        mutable.value = mutable.value.copy(permissionMode = mode)
        if (mutable.value.connected) configureAndInspect(restart = false)
    }

    private suspend fun configureAndInspect(restart: Boolean) {
        val currentEngine = engine ?: return
        val connector = control ?: return
        if (restart) currentEngine.close()
        currentEngine.connect()
        val mode = permissionMode()
        connector.configureMcpServer(
            McpHttpServerConfig(
                name = SERVER_NAME,
                url = GitHubEndpoints.REMOTE_MCP,
                bearerTokenEnvironmentVariable = GitHubEndpoints.REMOTE_MCP_TOKEN_ENVIRONMENT,
                approvalMode = when (mode) {
                    PermissionMode.READ_ONLY -> McpToolApprovalMode.APPROVE
                    PermissionMode.ASK_BEFORE_WRITES -> McpToolApprovalMode.WRITES
                    PermissionMode.FULL_CONTROL -> McpToolApprovalMode.APPROVE
                },
                httpHeaders = if (mode == PermissionMode.READ_ONLY) {
                    mapOf("X-MCP-Readonly" to "true")
                } else emptyMap(),
            ),
        )
        connector.reloadMcpServers()
        val server = connector.listMcpServers().firstOrNull { it.name == SERVER_NAME }
        val fingerprint = server?.let {
            ToolSchemaFingerprint.forTools(
                GitHubConnectorCatalog.ID,
                "mcp-http",
                it.tools.map { tool ->
                    ConnectorToolSchema(tool.name, tool.description, tool.inputSchema, tool.readOnly)
                },
            )
        }
        val saved = stateStore.get(GitHubConnectorCatalog.ID)
        if (
            saved?.toolSchemaFingerprint != null &&
            fingerprint != null &&
            saved.toolSchemaFingerprint != fingerprint &&
            mode == PermissionMode.FULL_CONTROL
        ) {
            preferences.edit().putString(PERMISSION_KEY, PermissionMode.ASK_BEFORE_WRITES.name).apply()
            mutable.value = mutable.value.copy(
                permissionMode = PermissionMode.ASK_BEFORE_WRITES,
                status = "GitHub tools changed. Write approvals were turned back on.",
            )
            configureAndInspect(restart = false)
            return
        }
        if (saved != null) stateStore.put(saved.copy(toolSchemaFingerprint = fingerprint ?: saved.toolSchemaFingerprint))
        mutable.value = mutable.value.copy(
            connected = true,
            connecting = false,
            toolCount = server?.tools?.size ?: 0,
            status = server?.error ?: if (server == null) "Connected; tool status is pending" else "Connected · ${server.tools.size} tools",
        )
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
        check(response.statusCode in 200..299) { "GitHub account verification failed (${response.statusCode})." }
        val body = json.parseToJsonElement(response.body).jsonObject
        val login = body["login"]?.jsonPrimitive?.contentOrNull.orEmpty()
        check(login.isNotBlank()) { "GitHub account response did not include a login." }
        return login to body["id"]?.jsonPrimitive?.longOrNull
    }

    private fun initialState(): GitHubConnectorUiState {
        val saved = stateStore.get(GitHubConnectorCatalog.ID)
        val connected = saved?.state == ConnectorState.CONNECTED && vault.read(CREDENTIAL_KEY) != null
        return GitHubConnectorUiState(
            available = clientId.isNotBlank(),
            connected = connected,
            accountLogin = saved?.accountLogin,
            permissionMode = permissionMode(),
            status = when {
                clientId.isBlank() -> "GitHub OAuth is not configured in this build"
                connected -> "Connected"
                else -> "Not connected"
            },
        )
    }

    private fun permissionMode(): PermissionMode = runCatching {
        PermissionMode.valueOf(preferences.getString(PERMISSION_KEY, null).orEmpty())
    }.getOrDefault(PermissionMode.ASK_BEFORE_WRITES)

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1_000L

    private companion object {
        const val SERVER_NAME = "github"
        const val CREDENTIAL_KEY = "github.oauth"
        const val PERMISSION_KEY = "github.permission"
    }
}
