package com.rama.okapi

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Message(
    val id: Long,
    val text: String,
    val updatedAt: Long,
    val sortOrder: Int,
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "okapi.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
            // Backfill existing rows with a stable order matching their old (most-recent-first) sort.
            db.rawQuery("SELECT id FROM messages ORDER BY updated_at DESC", null).use { cursor ->
                var order = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val values = ContentValues().apply { put("sort_order", order) }
                    db.update("messages", values, "id = ?", arrayOf(id.toString()))
                    order++
                }
            }
        }
    }

    /** Manual sort order first (ascending); ties broken by most recently updated. */
    fun getAll(): List<Message> {
        val messages = mutableListOf<Message>()
        readableDatabase.rawQuery(
            "SELECT id, text, updated_at, sort_order FROM messages ORDER BY sort_order ASC, updated_at DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(
                    Message(
                        id = cursor.getLong(0),
                        text = cursor.getString(1),
                        updatedAt = cursor.getLong(2),
                        sortOrder = cursor.getInt(3)
                    )
                )
            }
        }
        return messages
    }

    fun getById(id: Long): Message? {
        readableDatabase.rawQuery(
            "SELECT id, text, updated_at, sort_order FROM messages WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return Message(
                    id = cursor.getLong(0),
                    text = cursor.getString(1),
                    updatedAt = cursor.getLong(2),
                    sortOrder = cursor.getInt(3)
                )
            }
        }
        return null
    }

    /** Inserts a new message when [id] is null, otherwise updates the existing row. Returns the row id. */
    fun save(id: Long?, text: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("text", text)
            put("updated_at", System.currentTimeMillis())
        }
        return if (id == null) {
            values.put("sort_order", nextSortOrder(db))
            db.insert("messages", null, values)
        } else {
            db.update("messages", values, "id = ?", arrayOf(id.toString()))
            id
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("messages", "id = ?", arrayOf(id.toString()))
    }

    /** Swaps this task with the one directly above it (lower sort_order). No-op if already first. */
    fun moveUp(id: Long) = swapWithNeighbor(id, moveUpward = true)

    /** Swaps this task with the one directly below it (higher sort_order). No-op if already last. */
    fun moveDown(id: Long) = swapWithNeighbor(id, moveUpward = false)

    private fun nextSortOrder(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT MAX(sort_order) FROM messages", null).use { cursor ->
            return if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) + 1 else 0
        }
    }

    private fun swapWithNeighbor(id: Long, moveUpward: Boolean) {
        val db = writableDatabase
        val current = getById(id) ?: return
        val comparator = if (moveUpward) "<" else ">"
        val order = if (moveUpward) "DESC" else "ASC"

        db.rawQuery(
            "SELECT id, sort_order FROM messages WHERE sort_order $comparator ? ORDER BY sort_order $order LIMIT 1",
            arrayOf(current.sortOrder.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return // already first/last, nothing to swap with
            val neighborId = cursor.getLong(0)
            val neighborOrder = cursor.getInt(1)

            db.update(
                "messages",
                ContentValues().apply { put("sort_order", neighborOrder) },
                "id = ?",
                arrayOf(id.toString())
            )
            db.update(
                "messages",
                ContentValues().apply { put("sort_order", current.sortOrder) },
                "id = ?",
                arrayOf(neighborId.toString())
            )
        }
    }
}