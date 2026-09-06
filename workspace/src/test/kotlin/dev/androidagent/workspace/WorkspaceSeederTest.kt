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
    fun seedPopulatesCompleteHarnessAndSkills() {
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

        val deviceSkill = File(ws, ".agents/skills/device-automation/SKILL.md")
        assertTrue("device-automation skill should exist", deviceSkill.isFile)
        assertTrue("device-automation should have frontmatter", deviceSkill.readText().contains("name: device-automation"))

        val recoverySkill = File(ws, ".agents/skills/recovery-and-safety/SKILL.md")
        assertTrue("recovery-and-safety skill should exist", recoverySkill.isFile)

        val prefSkill = File(ws, ".agents/skills/user-preferences/SKILL.md")
        assertTrue("user-preferences skill should exist", prefSkill.isFile)

        val appCardsSkill = File(ws, ".agents/skills/app-cards/SKILL.md")
        assertTrue("app-cards skill should exist", appCardsSkill.isFile)

        val codexDeviceSkill = File(ws, ".codex/skills/device-automation/SKILL.md")
        assertTrue(".codex device-automation skill should exist", codexDeviceSkill.isFile)

        val codexRecoverySkill = File(ws, ".codex/skills/recovery-and-safety/SKILL.md")
        assertTrue(".codex recovery-and-safety skill should exist", codexRecoverySkill.isFile)

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
    fun seedToCodexHomePopulatesSkills() {
        val codexHome = tempFolder.newFolder("codex_home")
        WorkspaceSeeder.seedToCodexHome(codexHome)

        val deviceSkill = File(codexHome, "skills/device-automation/SKILL.md")
        assertTrue(deviceSkill.isFile)
        assertTrue(deviceSkill.readText().contains("name: device-automation"))

        val recoverySkill = File(codexHome, "skills/recovery-and-safety/SKILL.md")
        assertTrue(recoverySkill.isFile)

        val userPrefsSkill = File(codexHome, "skills/user-preferences/SKILL.md")
        assertTrue(userPrefsSkill.isFile)

        val appCardsSkill = File(codexHome, "skills/app-cards/SKILL.md")
        assertTrue(appCardsSkill.isFile)
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
