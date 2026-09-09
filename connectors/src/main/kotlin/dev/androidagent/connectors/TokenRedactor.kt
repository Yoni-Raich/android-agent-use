package dev.androidagent.connectors

/** Redacts GitHub credentials before an error or diagnostic reaches UI/logs. */
object TokenRedactor {
    private val tokenPattern = Regex(
        "(?i)(?:github_pat_|gh[opurs]_)[A-Za-z0-9_]+",
    )
    private val bearerPattern = Regex("(?i)(Bearer\\s+)[^\\s,;]+")
    private val formSecretPattern = Regex(
        "(?i)((?:access_token|refresh_token|device_code|client_secret)=)[^&\\s]+",
    )

    fun redact(value: String?, extraSecrets: Collection<String> = emptyList()): String? {
        val original = value ?: return null
        var redacted: String = original
        extraSecrets
            .asSequence()
            .filter { it.length >= MIN_SECRET_LENGTH }
            .sortedByDescending { it.length }
            .forEach { secret -> redacted = redacted.replace(secret, REDACTED) }
        redacted = tokenPattern.replace(redacted, REDACTED)
        redacted = bearerPattern.replace(redacted) { match -> "${match.groupValues[1]}$REDACTED" }
        redacted = formSecretPattern.replace(redacted) { match -> "${match.groupValues[1]}$REDACTED" }
        return redacted
    }

    fun redactToken(token: String?): String = token?.let { REDACTED } ?: "<none>"

    private const val MIN_SECRET_LENGTH = 8
    private const val REDACTED = "[REDACTED]"
}
