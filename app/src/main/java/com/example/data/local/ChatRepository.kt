package com.example.data.local

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(sessionId)

    suspend fun getOrCreateLatestSession(defaultModelId: String): ChatSessionEntity {
        return chatDao.getLatestSession() ?: createNewSession(defaultModelId)
    }

    suspend fun createNewSession(modelId: String, title: String = "Percakapan Baru"): ChatSessionEntity {
        val now = System.currentTimeMillis()
        val id = chatDao.insertSession(
            ChatSessionEntity(
                title = title,
                modelId = modelId,
                createdAt = now,
                updatedAt = now
            )
        )
        return ChatSessionEntity(
            id = id,
            title = title,
            modelId = modelId,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun getRecentContext(sessionId: Long, limit: Int = 10): List<ChatMessageEntity> {
        return chatDao.getRecentMessagesDesc(sessionId, limit).reversed()
    }

    suspend fun appendMessage(
        sessionId: Long,
        role: String,
        content: String,
        modelUsed: String,
        source: String = "main_ui"
    ): Long {
        val now = System.currentTimeMillis()
        val msgId = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                content = content,
                modelUsed = modelUsed,
                source = source,
                timestamp = now
            )
        )
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            val updatedTitle = if (
                role == "user" &&
                (session.title == "Percakapan Baru" || session.title.isBlank())
            ) {
                content.trim().replace('\n', ' ').take(36).let {
                    if (content.trim().length > 36) "$it…" else it
                }
            } else {
                session.title
            }
            chatDao.updateSession(
                session.copy(
                    title = updatedTitle,
                    modelId = modelUsed,
                    updatedAt = now
                )
            )
        }
        return msgId
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSessionById(sessionId)
    }
}
