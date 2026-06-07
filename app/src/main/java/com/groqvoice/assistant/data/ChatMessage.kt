package com.groqvoice.assistant.data

import androidx.room.*

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "default"
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String = "default"): List<ChatMessage>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String = "default")

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
abstract class KaiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var INSTANCE: KaiDatabase? = null

        fun getInstance(context: android.content.Context): KaiDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, KaiDatabase::class.java, "kai_db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
