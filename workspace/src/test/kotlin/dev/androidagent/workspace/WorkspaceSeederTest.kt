package dev.androidagent.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceSeederTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun seedPopulatesWorkspaceHarnessWithoutSkillDuplicates() {
        val ws = tempFolder.newFolder("workspace")
        WorkspaceSeeder.seed(ws, null)

        val agentsMd = File(ws, "AGENTS.md")
        assertTrue("AGENTS.md should exist", agentsMd.isFile)
        val agentsContent = agentsMd.readText()
        assertTrue("Must describe 5-step loop", agentsContent.contains("Observe → Evaluate → Plan → Act → Verify"))
        assertTrue("Must describe bounds formula", agentsContent.contains("x = (x1 + x2) / 2"))
        assertTrue("Must state Golden Rules", agentsContent.contains("Golden Rules"))

        val recoveryMd = File(ws, "RECOVERY.md")
        assertTrue("RECOVERY.md should exist", recoveryMd.isFile)

        assertFalse("workspace must not duplicate user skills", File(ws, ".agents/skills/device-automation").exists())
        assertFalse("workspace must not use legacy .codex skills", File(ws, ".codex/skills/device-automation").exists())

        val whatsappCard = File(ws, "cards/whatsapp.md")
        assertTrue("whatsapp.md card should exist", whatsappCard.isFile)
        assertTrue(whatsappCard.readText().contains("com.whatsapp"))

        val chromeCard = File(ws, "cards/chrome.md")
        assertTrue("chrome.md card should exist", chromeCard.isFile)

        val mapsCard = File(ws, "cards/maps.md")
        assertTrue("maps.md card should exist", mapsCard.isFile)

        val settingsCard = File(ws, "cards/settings.md")
        assertTrue("settings.md card should exist", settingsCard.isFile)

        val youtubeCard = File(ws, "cards/youtube.md")
        assertTrue("youtube.md card should exist", youtubeCard.isFile)

        assertFalse(
            "preferences are global now; a workspace copy would be a second answer",
            File(ws, "preferences.json").exists(),
        )
    }

    @Test
    fun installDefaultSkillsUsesStandardUserRootAndCleansLegacyCopies() {
        val home = tempFolder.newFolder("home")
        val legacy = File(home, ".codex/skills/device-automation/SKILL.md")
        legacy.parentFile!!.mkdirs()
        legacy.writeText("legacy")
        val contents = mapOf(
            "device-automation" to "device body",
            "recovery-and-safety" to "recovery body",
            "user-preferences" to "preferences body",
            "app-cards" to "cards body",
            "personal-skills" to "authoring body",
        )

        WorkspaceSeeder.installDefaultSkills(home) { relativePath ->
            val name = relativePath.substringBefore('/')
            "---\nname: $name\ndescription: $name description\n---\n\n${contents.getValue(name)}\n".toByteArray()
        }

        for ((name, body) in contents) {
            val installed = File(home, ".agents/skills/$name/SKILL.md")
            assertTrue("$name should be installed in the standard user root", installed.isFile)
            assertTrue(installed.readText().contains(body))
        }
        assertFalse("legacy CODEX_HOME skill copy should be removed", legacy.exists())
        assertFalse("deprecated CODEX_HOME skill root must not be populated", File(home, ".codex/skills/app-cards").exists())
    }

    @Test
    fun seedRemovesOnlyManagedWorkspaceSkillDuplicates() {
        val ws = tempFolder.newFolder("workspace_cleanup")
        val oldManaged = File(ws, ".agents/skills/device-automation/SKILL.md")
        oldManaged.parentFile!!.mkdirs()
        oldManaged.writeText("old managed copy")
        val unrelated = File(ws, ".agents/skills/custom-user-skill/SKILL.md")
        unrelated.parentFile!!.mkdirs()
        unrelated.writeText("keep me")

        WorkspaceSeeder.seed(ws, null)

        assertFalse(oldManaged.exists())
        assertTrue("unrelated skills must remain untouched", unrelated.isFile)
        assertEquals("keep me", unrelated.readText())
    }

    @Test
    fun theSeededHarnessIsMarkdownRatherThanOneLongCodeBlock() {
        // trimIndent() takes the smallest indent in the string, so one block
        // pasted at column 0 left every other line indented by eight spaces -
        // which Markdown reads as a code block, and the model reads as noise.
        val ws = tempFolder.newFolder("workspace_indent")
        WorkspaceSeeder.seed(ws, null)

        val indented = File(ws, "AGENTS.md").readLines()
            .filter { it.isNotBlank() && it.startsWith("    ") }
        assertTrue("these lines would render as code: " + indented.take(3), indented.isEmpty())
    }

    @Test
    fun theHarnessTellsTheModelWhereDurableMemoryLives() {
        // The harness is the only place that says a workspace file does not
        // survive the chat. Without it the agent writes notes into a directory
        // that is rebuilt from templates the next time it is opened.
        val ws = tempFolder.newFolder("workspace_memory_doc")
        WorkspaceSeeder.seed(ws, null)

        val agents = File(ws, "AGENTS.md").readText()
        assertTrue("it should point at the skill root", agents.contains("~/.agents/skills"))
        assertTrue("it should point at the data root", agents.contains("~/memory/<skill-name>/"))
        assertTrue("it should route the user's ask to a skill", agents.contains("personal-skills"))
        assertTrue("it should say the workspace does not survive", agents.contains("rebuilt from templates"))
    }

    @Test
    fun seedLeavesAPreExistingWorkspacePreferencesFileAlone() {
        // Reseeding must not touch it: the app-start migration is what absorbs
        // it into the global store, and it can only do that if it is still here.
        val ws = tempFolder.newFolder("workspace_prefs")
        val customPrefs = """{"apps":{"messaging":"Signal"},"customKey":"preserved"}"""
        val prefsFile = File(ws, "preferences.json")
        prefsFile.writeText(customPrefs)

        WorkspaceSeeder.seed(ws, null)

        assertEquals("Existing preferences.json must not be overwritten", customPrefs, prefsFile.readText())
    }

    @Test
    fun sharedMemoryIsSeededOnceAndAbsorbsPerSessionPreferences() {
        val home = tempFolder.newFolder("home_memory")
        val legacy = File(tempFolder.newFolder("old_session"), "preferences.json")
        legacy.writeText("""{"apps":{"messaging":"Signal"}}""")

        WorkspaceSeeder.seedSharedMemory(home, null, listOf(legacy))

        val prefs = File(home, "memory/preferences.json")
        assertTrue("global preferences should exist", prefs.isFile)
        assertTrue("the user's own choice must survive the move", prefs.readText().contains("Signal"))
        assertFalse("the per-session copy is removed once absorbed", legacy.exists())
        assertTrue("the layout carries a signpost the user can read", File(home, "memory/README.md").isFile)
    }

    @Test
    fun seedingSharedMemoryAgainDoesNotOverwriteWhatTheAgentLearned() {
        val home = tempFolder.newFolder("home_memory_twice")
        WorkspaceSeeder.seedSharedMemory(home, null)
        val prefs = File(home, "memory/preferences.json")
        prefs.writeText("""{"apps":{"messaging":"Signal"}}""")
        // Standing in for a skill's data file, which the agent writes itself.
        val note = File(home, "memory/contacts/contacts.json")
        note.parentFile!!.mkdirs()
        note.writeText("something worth keeping")

        WorkspaceSeeder.seedSharedMemory(home, null)

        assertTrue(prefs.readText().contains("Signal"))
        assertEquals("something worth keeping", note.readText())
    }

    @Test
    fun installingBundledSkillsLeavesALearnedSkillAlone() {
        // The whole point of a learned skill is that it is still there after the
        // app restarts and reinstalls its own.
        val home = tempFolder.newFolder("home_skills")
        val learned = File(home, ".agents/skills/order-coffee/SKILL.md")
        learned.parentFile!!.mkdirs()
        learned.writeText(skillFile("order-coffee", "mine", "steps"))

        WorkspaceSeeder.installDefaultSkills(home) { relativePath ->
            skillFile(relativePath.substringBefore('/'), "d", "body").toByteArray()
        }

        assertTrue("a learned skill must survive an app restart", learned.isFile)
        assertTrue(learned.readText().contains("steps"))
    }

    private fun skillFile(name: String, description: String, body: String): String =
        buildString {
            appendLine("---")
            appendLine("name: " + name)
            appendLine("description: " + description)
            appendLine("---")
            appendLine()
            appendLine(body)
        }
}
