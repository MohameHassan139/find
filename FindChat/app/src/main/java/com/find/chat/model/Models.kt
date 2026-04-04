package com.find.chat.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// ─── Conversations ───────────────────────────────────────────────────────────

data class ConversationsResponse(
    val data: List<Conversation>?,
    val message: String?,
    val status: Boolean?
)

@Parcelize
data class Conversation(
    val id: Int,
    @SerializedName("other_user") val otherUser: OtherUser?,
    @SerializedName("last_message") val lastMessage: LastMessage?,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("created_at") val createdAt: String?
) : Parcelable

@Parcelize
data class OtherUser(
    val id: Int,
    val name: String?,
    val avatar: String?
) : Parcelable

@Parcelize
data class LastMessage(
    val body: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("is_read") val isRead: Boolean = false
) : Parcelable

// ─── Messages ────────────────────────────────────────────────────────────────

data class MessagesResponse(
    val data: List<Message>?,
    val message: String?,
    val status: Boolean?
)

data class Message(
    val id: Int,
    val body: String?,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("is_mine") val isMine: Boolean = false,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("is_read") val isRead: Boolean = false
)

// ─── Send Message ─────────────────────────────────────────────────────────────

data class SendMessageRequest(
    val body: String
)

data class SendMessageResponse(
    val data: Message?,
    val message: String?,
    val status: Boolean?
)

// ─── Create Conversation ──────────────────────────────────────────────────────

data class CreateConversationRequest(
    @SerializedName("receiver_id") val receiverId: Int
)

data class CreateConversationResponse(
    val data: Conversation?,
    val message: String?,
    val status: Boolean?
)
