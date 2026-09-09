package dev.androidagent.connectors

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.security.MessageDigest

data class ConnectorToolSchema(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val readOnly: Boolean? = null,
)

object ToolSchemaFingerprint {
    private val canonicalJson = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = true
    }

    /** Canonical JSON with object keys sorted recursively and array order preserved. */
    fun canonicalize(element: JsonElement): String = canonicalJson.encodeToString(
        JsonElement.serializer(),
        canonicalElement(element),
    )

    fun sha256(element: JsonElement): String = sha256(canonicalize(element))

    fun sha256(canonicalJsonText: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(canonicalJsonText.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    fun forTools(
        connectorId: String,
        protocolVersion: String,
        tools: Collection<ConnectorToolSchema>,
    ): String {
        val manifest = buildJsonObject {
            put("connector", connectorId)
            put("protocol", protocolVersion)
            putJsonArray("tools") {
                tools.sortedBy { it.name }.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            tool.readOnly?.let { put("readOnly", it) }
                            put("inputSchema", tool.inputSchema)
                        },
                    )
                }
            }
        }
        return sha256(manifest)
    }

    private fun canonicalElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            element.keys.sorted().forEach { key ->
                put(key, canonicalElement(element.getValue(key)))
            }
        }

        is JsonArray -> buildJsonArray {
            element.forEach { add(canonicalElement(it)) }
        }

        is JsonPrimitive -> element
    }
}
