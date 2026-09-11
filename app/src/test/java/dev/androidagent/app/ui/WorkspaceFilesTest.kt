package dev.androidagent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilesTest {
    private val items = listOf(
        WorkspaceFileItem("AGENTS.md", 900, modifiedAt = 500),
        WorkspaceFileItem("attachments/3f2b1c4d-1234-4abc-8def-0123456789ab-receipt.jpg", 204_800, modifiedAt = 200),
        WorkspaceFileItem("cards/whatsapp.md", 400, modifiedAt = 500),
        WorkspaceFileItem("notes/summary.md", 1_200, modifiedAt = 300),
        WorkspaceFileItem("preferences.json", 300, modifiedAt = 500),
        WorkspaceFileItem("attachments", isDirectory = true),
    )

    @Test fun theUsersFilesComeFirstNewestOnTop() {
        val listing = workspaceListing(items)
        assertEquals(listOf("summary.md", "receipt.jpg"), listing.yours.map { it.name })
        assertEquals(listOf("notes", "attachments"), listing.yours.map { it.folder })
    }

    @Test fun whatTheAppPlantsIsKeptApartInPathOrder() {
        val listing = workspaceListing(items)
        assertEquals(listOf("AGENTS.md", "cards/whatsapp.md", "preferences.json"), listing.agent.map { it.item.path })
        assertEquals("", listing.agent.first().folder)
    }

    @Test fun hiddenFoldersAndSeededRootsAreAgentFiles() {
        assertTrue(isAgentFile(".agents/skills/x/SKILL.md"))
        assertTrue(isAgentFile("cards/maps.md"))
        assertTrue(isAgentFile("RECOVERY.md"))
        assertFalse(isAgentFile("attachments/photo.png"))
        assertFalse(isAgentFile("report.md"))
    }

    @Test fun anAttachmentShowsTheNameItWasGiven() {
        assertEquals("receipt.jpg", displayName("3f2b1c4d-1234-4abc-8def-0123456789ab-receipt.jpg"))
        assertEquals("plain.txt", displayName("plain.txt"))
        assertEquals("2024-report.pdf", displayName("2024-report.pdf"))
    }

    @Test fun kindsFollowTheExtension() {
        assertEquals(FileKind.IMAGE, kindOf("photo.JPG"))
        assertEquals(FileKind.PDF, kindOf("bill.pdf"))
        assertEquals(FileKind.TEXT, kindOf("notes.md"))
        assertEquals(FileKind.OTHER, kindOf("blob"))
    }

    @Test fun notesGoOutAsPlainTextAndUnknownFilesToAnyApp() {
        assertEquals("text/plain", mimeTypeFor("notes.md") { null })
        assertEquals("application/pdf", mimeTypeFor("bill.pdf") { if (it == "pdf") "application/pdf" else null })
        assertEquals("*/*", mimeTypeFor("blob") { null })
        assertEquals("*/*", mimeTypeFor("data.bin") { null })
    }
}
