package dev.androidagent.workspace

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.androidagent.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalSessionStore(context: Context) : SessionStore {
    private val appContext = context.applicationContext
    private val base = File(context.filesDir, "sessions").apply { mkdirs() }
    private val db = Database(context).writableDatabase
    private val lock = Mutex()
    private val streams = ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()
    private val sessionStream = MutableStateFlow(loadSessions())
    override val sessions: StateFlow<List<ChatSession>> = sessionStream.asStateFlow()
    init { db.execSQL("UPDATE messages SET state='interrupted' WHERE state='streaming'") }

    override suspend fun createSession(): ChatSession = mutate {
        val now = System.currentTimeMillis()
        val session = ChatSession(UUID.randomUUID().toString(), "New chat", now, now)
        db.insertOrThrow("sessions", null, ContentValues().apply { put("id", session.id); put("title", session.title); put("created", now); put("updated", now) })
        workspace(session.id).mkdirs()
        refresh()
        session
    }
    override suspend fun getSession(id: String): ChatSession? = withContext(Dispatchers.IO) { loadSessions().firstOrNull { it.id == id } }
    override fun messages(sessionId: String): Flow<List<ChatMessage>> = streams.getOrPut(sessionId) { MutableStateFlow(loadMessages(sessionId)) }.asStateFlow()
    override suspend fun append(message: ChatMessage) = mutate {
        db.insertOrThrow("messages", null, ContentValues().apply {
            put("id", message.id); put("session", message.sessionId); put("role", message.role); put("text", message.text)
            put("created", message.createdAt); put("state", message.state); put("attachments", Json.encodeToString(message.attachmentPaths))
        })
        db.execSQL("UPDATE sessions SET updated=? WHERE id=?", arrayOf(message.createdAt, message.sessionId))
        refresh(message.sessionId)
    }
    override suspend fun updateMessage(id: String, text: String, state: String) = mutate {
        val session = db.rawQuery("SELECT session FROM messages WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) it.getString(0) else null }
        db.update("messages", ContentValues().apply { put("text", text); put("state", state) }, "id=?", arrayOf(id))
        session?.let(::refresh)
        Unit
    }
    override suspend fun setThread(sessionId: String, threadId: String) = mutate { db.execSQL("UPDATE sessions SET thread=? WHERE id=?", arrayOf(threadId, sessionId)); refresh() }
    override suspend fun rename(sessionId: String, title: String) = mutate { db.execSQL("UPDATE sessions SET title=? WHERE id=?", arrayOf(title.trim().take(80).ifBlank { "New chat" }, sessionId)); refresh() }
    override suspend fun deleteSession(sessionId: String) = mutate {
        val folder = workspace(sessionId).parentFile!!
        require(folder.canonicalFile.parentFile == base.canonicalFile)
        db.beginTransaction()
        try { db.delete("messages", "session=?", arrayOf(sessionId)); db.delete("sessions", "id=?", arrayOf(sessionId)); db.setTransactionSuccessful() } finally { db.endTransaction() }
        folder.deleteRecursively()
        streams.remove(sessionId)?.value = emptyList()
        refresh()
    }
    override fun workspace(sessionId: String): File {
        require(runCatching { UUID.fromString(sessionId).toString() == sessionId }.getOrDefault(false)) { "Invalid session ID" }
        val ws = File(base, "$sessionId/workspace").apply { mkdirs() }
        WorkspaceSeeder.seed(ws, appContext)
        runCatching {
            WorkspaceSeeder.seedToCodexHome(File(appContext.filesDir, "runtime/home/.codex"))
        }
        return ws
    }
    private suspend fun <T> mutate(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }
    private fun refresh(sessionId: String? = null) { sessionStream.value = loadSessions(); sessionId?.let { streams[it]?.value = loadMessages(it) } }
    private fun loadSessions(): List<ChatSession> = db.rawQuery("SELECT id,title,created,updated,thread FROM sessions ORDER BY updated DESC", null).use { c -> buildList { while (c.moveToNext()) add(ChatSession(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getString(4))) } }
    private fun loadMessages(sessionId: String): List<ChatMessage> = db.rawQuery("SELECT id,role,text,created,state,attachments FROM messages WHERE session=? ORDER BY created,rowid", arrayOf(sessionId)).use { c -> buildList { while (c.moveToNext()) add(ChatMessage(c.getString(0), sessionId, c.getString(1), c.getString(2), c.getLong(3), c.getString(4), runCatching { Json.decodeFromString<List<String>>(c.getString(5)) }.getOrDefault(emptyList()))) } }

    private class Database(context: Context) : SQLiteOpenHelper(context, "sessions.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,title TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,thread TEXT)")
            db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,session TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,role TEXT NOT NULL,text TEXT NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL,attachments TEXT NOT NULL)")
            db.execSQL("CREATE INDEX message_session ON messages(session,created)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unsupported database upgrade")
    }
}
