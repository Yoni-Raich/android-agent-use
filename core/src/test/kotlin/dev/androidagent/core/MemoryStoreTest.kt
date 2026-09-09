package dev.androidagent.core

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): MemoryStore = MemoryStore(temp.root)

    @Test fun theDefaultsAreWrittenOnceAndTheUsersOwnValuesWin() {
        val memory = store()
        memory.ensurePreferences("""{"apps":{"browser":"Chrome"}}""")
        // Standing in for the agent editing the file with its own shell.
        File(temp.root, MemoryStore.PREFERENCES_FILE).writeText("""{"apps":{"browser":"Firefox"}}""")
        memory.ensurePreferences("""{"apps":{"browser":"Chrome"}}""")

        assertEquals(
            "Firefox",
            memory.preferences()["apps"]!!.jsonObject["browser"]!!.jsonPrimitive.content,
        )
    }

    @Test fun aDefaultAddedByALaterReleaseReachesAPhoneThatAlreadyHasTheFile() {
        // Write-if-absent would strand it: the file exists, so it would never
        // be looked at again.
        val memory = store()
        memory.ensurePreferences("""{"apps":{"browser":"Chrome"}}""")
        memory.ensurePreferences("""{"apps":{"browser":"Chrome"},"defaults":{"confirm_destructive":true}}""")

        val defaults = memory.preferences()["defaults"]!!.jsonObject
        assertEquals("true", defaults["confirm_destructive"]!!.jsonPrimitive.content)
    }

    @Test fun malformedDefaultsAreRefusedRatherThanWritten() {
        runCatching { store().ensurePreferences("not json") }
            .onSuccess { org.junit.Assert.fail("malformed defaults should be refused") }
        assertFalse(File(temp.root, MemoryStore.PREFERENCES_FILE).exists())
    }

    @Test fun aCorruptPreferencesFileReadsAsEmptyInsteadOfThrowing() {
        File(temp.root, MemoryStore.PREFERENCES_FILE).writeText("{ this is not json")
        assertTrue(store().preferences().isEmpty())
    }

    @Test fun legacyPerSessionPreferencesAreMergedNewestFirstAndThenRemoved() {
        val older = temp.newFile("older.json").apply {
            writeText("""{"apps":{"browser":"Firefox"},"addresses":{"work":"Office"}}""")
            setLastModified(1_000L)
        }
        val newer = temp.newFile("newer.json").apply {
            writeText("""{"apps":{"browser":"Chrome"}}""")
            setLastModified(9_000L)
        }

        val memory = store()
        memory.absorbLegacyPreferences(listOf(older, newer))

        val prefs = memory.preferences()
        // The newest file wins the conflict; a key only the older one had is
        // still carried across rather than dropped.
        assertEquals("Chrome", prefs["apps"]!!.jsonObject["browser"]!!.jsonPrimitive.content)
        assertEquals("Office", prefs["addresses"]!!.jsonObject["work"]!!.jsonPrimitive.content)
        assertFalse("absorbed copies are removed", older.exists())
        assertFalse("absorbed copies are removed", newer.exists())
    }

    @Test fun aPreferenceAlreadySetGloballyBeatsALegacyFile() {
        File(temp.root, MemoryStore.PREFERENCES_FILE).writeText("""{"apps":{"browser":"Chrome"}}""")
        val legacy = temp.newFile("legacy.json").apply { writeText("""{"apps":{"browser":"Firefox"}}""") }

        val memory = store()
        memory.absorbLegacyPreferences(listOf(legacy))

        assertEquals(
            "Chrome",
            memory.preferences()["apps"]!!.jsonObject["browser"]!!.jsonPrimitive.content,
        )
    }

    @Test fun anUnreadableLegacyFileIsLeftAloneRatherThanDeletedOnAGuess() {
        val corrupt = temp.newFile("corrupt.json").apply { writeText("{ this is not json") }
        store().absorbLegacyPreferences(listOf(corrupt))
        assertTrue(corrupt.exists())
    }

    @Test fun absorbingNothingDoesNotCreateAnEmptyPreferencesFile() {
        store().absorbLegacyPreferences(listOf(File(temp.root, "does-not-exist.json")))
        assertFalse(File(temp.root, MemoryStore.PREFERENCES_FILE).exists())
    }

    @Test fun ensureLayoutIsIdempotentAndLeavesTheReadmeTheUserEdited() {
        val memory = store()
        memory.ensureLayout()
        val readme = File(temp.root, "README.md")
        assertTrue(readme.isFile)
        readme.writeText("edited by the user")
        memory.ensureLayout()
        assertEquals("edited by the user", readme.readText())
    }

    @Test fun theReadmeExplainsWhyDataIsNotKeptInsideASkill() {
        store().ensureLayout()
        val readme = File(temp.root, "README.md").readText()
        assertTrue(readme.contains(".agents/skills"))
        assertTrue(readme.contains("<skill-name>/"))
    }
}
