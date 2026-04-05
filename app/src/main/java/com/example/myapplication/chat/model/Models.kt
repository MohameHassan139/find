package com.example.myapplication.chat.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

// ─── Conversations ────────────────────────────────────────────────────────────

data class ConversationsResponse(
    val data: List<Conversation>?,
    val message: String?,
    val status: Boolean?
)

data class Conversation(
    val id: String,
    @SerializedName("listing_id") val listingId: String?,
    @SerializedName("listing_title") val listingTitle: String?,
    @SerializedName("buyer_id") val buyerId: String?,
    @SerializedName("seller_id") val sellerId: String?,
    @SerializedName("seller_name") val sellerName: String?,
    @SerializedName("seller_avatar") val sellerAvatar: String?,
    @SerializedName("buyer_name") val buyerName: String?,
    @SerializedName("buyer_avatar") val buyerAvatar: String?,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("buyer_unread") val buyerUnread: Int = 0,
    @SerializedName("seller_unread") val sellerUnread: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(listingId)
        parcel.writeString(listingTitle)
        parcel.writeString(buyerId)
        parcel.writeString(sellerId)
        parcel.writeString(sellerName)
        parcel.writeString(sellerAvatar)
        parcel.writeString(buyerName)
        parcel.writeString(buyerAvatar)
        parcel.writeString(lastMessage)
        parcel.writeString(lastMessageAt)
        parcel.writeInt(buyerUnread)
        parcel.writeInt(sellerUnread)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<Conversation> {
        override fun createFromParcel(parcel: Parcel) = Conversation(parcel)
        override fun newArray(size: Int) = arrayOfNulls<Conversation>(size)
    }
}

// ─── Messages ─────────────────────────────────────────────────────────────────

data class MessagesResponse(
    val data: List<Message>?,
    val message: String?,
    val status: Boolean?
)

data class Message(
    val id: String,
    val text: String?,
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("is_mine") val isMine: Boolean = false,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("is_read") val isRead: Boolean = false
)

// ─── Send Message ──────────────────────────────────────────────────────────────

data class SendMessageRequest(val text: String)

data class SendMessageResponse(
    val data: Message?,
    val message: String?,
    val status: Boolean?
)

// ─── Create Conversation ───────────────────────────────────────────────────────

data class CreateConversationRequest(
    @SerializedName("listing_id") val listingId: String
)

data class CreateConversationResponse(
    val data: Conversation?,
    val message: String?,
    val status: Boolean?
)

// ─── Notifications ────────────────────────────────────────────────────────────

data class NotificationsResponse(
    val data: List<AppNotification>?,
    val meta: NotificationsMeta?
)

data class AppNotification(
    val id: String,
    val type: String?,
    @SerializedName("title_ar") val titleAr: String?,
    @SerializedName("body_ar") val bodyAr: String?,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String?
)

data class NotificationsMeta(
    @SerializedName("current_page") val currentPage: Int = 1,
    val total: Int = 0,
    @SerializedName("unread_count") val unreadCount: Int = 0
)
