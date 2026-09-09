package dev.androidagent.connectors

/** OAuth scopes documented by GitHub for GitHub.com OAuth Apps. */
object GitHubOAuthScopes {
    const val REPO = "repo"
    const val REPO_STATUS = "repo:status"
    const val REPO_DEPLOYMENT = "repo_deployment"
    const val PUBLIC_REPO = "public_repo"
    const val REPO_INVITE = "repo:invite"
    const val SECURITY_EVENTS = "security_events"
    const val ADMIN_REPO_HOOK = "admin:repo_hook"
    const val WRITE_REPO_HOOK = "write:repo_hook"
    const val READ_REPO_HOOK = "read:repo_hook"
    const val ADMIN_ORG = "admin:org"
    const val WRITE_ORG = "write:org"
    const val READ_ORG = "read:org"
    const val ADMIN_PUBLIC_KEY = "admin:public_key"
    const val WRITE_PUBLIC_KEY = "write:public_key"
    const val READ_PUBLIC_KEY = "read:public_key"
    const val ADMIN_ORG_HOOK = "admin:org_hook"
    const val GIST = "gist"
    const val NOTIFICATIONS = "notifications"
    const val USER = "user"
    const val READ_USER = "read:user"
    const val USER_EMAIL = "user:email"
    const val USER_FOLLOW = "user:follow"
    const val PROJECT = "project"
    const val READ_PROJECT = "read:project"
    const val DELETE_REPO = "delete_repo"
    const val WRITE_PACKAGES = "write:packages"
    const val READ_PACKAGES = "read:packages"
    const val DELETE_PACKAGES = "delete:packages"
    const val ADMIN_GPG_KEY = "admin:gpg_key"
    const val WRITE_GPG_KEY = "write:gpg_key"
    const val READ_GPG_KEY = "read:gpg_key"
    const val CODESPACE = "codespace"
    const val WORKFLOW = "workflow"
    const val READ_AUDIT_LOG = "read:audit_log"
    const val OFFLINE_ACCESS = "offline_access"

    /** All non-empty scopes accepted by the GitHub.com OAuth Apps docs. */
    val supported: Set<String> = linkedSetOf(
        REPO,
        REPO_STATUS,
        REPO_DEPLOYMENT,
        PUBLIC_REPO,
        REPO_INVITE,
        SECURITY_EVENTS,
        ADMIN_REPO_HOOK,
        WRITE_REPO_HOOK,
        READ_REPO_HOOK,
        ADMIN_ORG,
        WRITE_ORG,
        READ_ORG,
        ADMIN_PUBLIC_KEY,
        WRITE_PUBLIC_KEY,
        READ_PUBLIC_KEY,
        ADMIN_ORG_HOOK,
        GIST,
        NOTIFICATIONS,
        USER,
        READ_USER,
        USER_EMAIL,
        USER_FOLLOW,
        PROJECT,
        READ_PROJECT,
        DELETE_REPO,
        WRITE_PACKAGES,
        READ_PACKAGES,
        DELETE_PACKAGES,
        ADMIN_GPG_KEY,
        WRITE_GPG_KEY,
        READ_GPG_KEY,
        CODESPACE,
        WORKFLOW,
        READ_AUDIT_LOG,
        OFFLINE_ACCESS,
    )

    /** OAuth App scope set needed by the initial private-repository Issue flow. */
    val issueWrite: Set<String> = linkedSetOf(REPO, OFFLINE_ACCESS)
}

object GitHubEndpoints {
    const val DEVICE_CODE = "https://github.com/login/device/code"
    const val DEVICE_VERIFICATION = "https://github.com/login/device"
    const val ACCESS_TOKEN = "https://github.com/login/oauth/access_token"
    const val API_BASE = "https://api.github.com/"
    const val REMOTE_MCP = "https://api.githubcopilot.com/mcp/x/all"
    const val REMOTE_MCP_TOKEN_ENVIRONMENT = "GITHUB_PERSONAL_ACCESS_TOKEN"
    const val API_VERSION = "2022-11-28"

    /** Fixed egress boundary for the GitHub connector. */
    fun isAllowed(url: String): Boolean = when {
        url == DEVICE_CODE || url == ACCESS_TOKEN || url == DEVICE_VERIFICATION || url == REMOTE_MCP -> true
        url.startsWith(API_BASE) -> true
        else -> false
    }
}

object GitHubConnectorCatalog {
    const val ID = "github"

    val definition = ConnectorDefinition(
        id = ID,
        displayName = "GitHub",
        permissionMode = PermissionMode.ASK_BEFORE_WRITES,
        authStrategy = AuthStrategy.GITHUB_OAUTH_APP_DEVICE_FLOW,
        supportedScopes = GitHubOAuthScopes.supported,
        defaultScopes = GitHubOAuthScopes.issueWrite,
        apiBaseUrl = GitHubEndpoints.API_BASE,
        authBaseUrl = "https://github.com/",
        capabilities = setOf("repository_read", "issue_read", "issue_create"),
    )
}
