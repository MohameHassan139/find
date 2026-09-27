package com.example.myapplication.data.repository

import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.model.ConversationUpdate
import com.example.myapplication.chat.model.CreateConversationRequest
import com.example.myapplication.chat.model.Message
import com.example.myapplication.chat.model.MessagesResponse
import com.example.myapplication.chat.model.SendMessageRequest
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService

/** Conversations and messages. */
class ChatRepository(private val api: FindApiService) {

    suspend fun conversations(): ApiResult<List<Conversation>> =
        apiCall({ api.getConversations() }) { it.body()?.data.orEmpty() }

    /** Lightweight poll: last-message time, unread count and favorite flag per conversation. */
    suspend fun conversationUpdates(): ApiResult<List<ConversationUpdate>?> =
        apiCall({ api.getConversationUpdates() }) { it.body()?.data }

    /** Opens (or reuses) the conversation about a listing. */
    suspend fun startConversation(listingId: String): ApiResult<Conversation?> =
        apiCall({ api.createConversation(CreateConversationRequest(listingId)) }) { it.body()?.data }

    /** Newest [limit] messages, or the page before/after a message id. */
    suspend fun messages(
        conversationId: String,
        limit: Int? = null,
        before: String? = null,
        after: String? = null
    ): ApiResult<MessagesResponse?> =
        apiCall({ api.getMessages(conversationId, limit, before, after) }) { it.body() }

    suspend fun send(conversationId: String, text: String): ApiResult<Message?> =
        apiCall({ api.sendMessage(conversationId, SendMessageRequest(text)) }) { it.body()?.data }

    suspend fun markRead(conversationId: String): ApiResult<Unit> = apiCall { api.markRead(conversationId) }

    suspend fun delete(conversationId: String): ApiResult<Unit> = apiCall { api.deleteConversation(conversationId) }

    /** Returns the server's updated conversation. */
    suspend fun setFavorite(conversationId: String, favorite: Boolean): ApiResult<Conversation?> =
        if (favorite) apiCall({ api.favoriteConversation(conversationId) }) { it.body()?.data }
        else apiCall({ api.unfavoriteConversation(conversationId) }) { it.body()?.data }
}
