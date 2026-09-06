package dev.androidagent.runtime

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeHostConfigTest {
    @Test
    fun `fresh config enables realtime feature`() {
        val directory = Files.createTempDirectory("android-agent-config").toFile()
        try {
            val config = directory.resolve("config.toml")

            assertTrue(AndroidRuntimeHost.ensureRealtimeFeatureConfig(config))
            val text = config.readText()
            assertTrue(text.contains("[features]"))
            assertTrue(text.contains("realtime_conversation = true"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `existing config preserves content and migration is idempotent`() {
        val directory = Files.createTempDirectory("android-agent-config").toFile()
        try {
            val config = directory.resolve("config.toml")
            config.writeText(
                """
                # Keep this setting and its comment.
                [auth]
                credential_reference = "keep-me"
                [features]
                realtime_conversation = false # keep this inline comment
                """.trimIndent() + "\n"
            )

            assertTrue(AndroidRuntimeHost.ensureRealtimeFeatureConfig(config))
            val migrated = config.readText()
            assertTrue(migrated.contains("credential_reference = \"keep-me\""))
            assertTrue(migrated.contains("realtime_conversation = true # keep this inline comment"))

            assertFalse(AndroidRuntimeHost.ensureRealtimeFeatureConfig(config))
            assertEquals(migrated, config.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `partial config without feature keeps existing sections`() {
        val directory = Files.createTempDirectory("android-agent-config").toFile()
        try {
            val config = directory.resolve("config.toml")
            config.writeText(
                """
                # User supplied config.
                [model]
                name = "keep-model"
                """.trimIndent() + "\n"
            )

            assertTrue(AndroidRuntimeHost.ensureRealtimeFeatureConfig(config))
            val text = config.readText()
            assertTrue(text.contains("name = \"keep-model\""))
            assertTrue(text.contains("[features]"))
            assertTrue(text.contains("realtime_conversation = true"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
