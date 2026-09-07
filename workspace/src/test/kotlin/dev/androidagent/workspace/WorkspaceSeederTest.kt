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

        val prefs = File(ws, "preferences.json")
        assertTrue("preferences.json should exist", prefs.isFile)
        assertTrue("preferences.json should have default structure", prefs.readText().contains("\"messaging\": \"WhatsApp\""))
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
    fun seedPreservesExistingUserPreferences() {
        val ws = tempFolder.newFolder("workspace_prefs")
        val customPrefs = """{"apps":{"messaging":"Signal"},"customKey":"preserved"}"""
        val prefsFile = File(ws, "preferences.json")
        prefsFile.writeText(customPrefs)

        WorkspaceSeeder.seed(ws, null)

        assertEquals("Existing preferences.json must not be overwritten", customPrefs, prefsFile.readText())
    }
}
