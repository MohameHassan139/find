package com.example.myapplication.chat.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

// ─── Conversations ────────────────────────────────────────────────────────────

data class ConversationsResponse(
    val data: List<Conversation>?,
    val message: String?
)

// `other_user` is always relative to the caller (never separate buyer/seller
// fields — see docs/API_REFERENCE.md § GET /conversations).
data class Conversation(
    val id: String,
    @SerializedName("listing_id") val listingId: String?,
    @SerializedName("listing_title") val listingTitle: String?,
    @SerializedName("other_user") val otherUser: OtherUser?,
    @SerializedName("last_message") val lastMessage: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("my_unread") val myUnread: Int = 0
) : Parcelable {
    @Suppress("DEPRECATION")
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readString(),
        parcel.readParcelable(OtherUser::class.java.classLoader),
        parcel.readString(),
        parcel.readString(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(listingId)
        parcel.writeString(listingTitle)
        parcel.writeParcelable(otherUser, flags)
        parcel.writeString(lastMessage)
        parcel.writeString(lastMessageAt)
        parcel.writeInt(myUnread)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<Conversation> {
        override fun createFromParcel(parcel: Parcel) = Conversation(parcel)
        override fun newArray(size: Int) = arrayOfNulls<Conversation>(size)
    }
}

data class OtherUser(
    val id: Int = 0,
    val name: String?,
    val avatar: String?,
    @SerializedName("last_seen_at") val lastSeenAt: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(), parcel.readString(), parcel.readString(), parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
        parcel.writeString(avatar)
        parcel.writeString(lastSeenAt)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<OtherUser> {
        override fun createFromParcel(parcel: Parcel) = OtherUser(parcel)
        override fun newArray(size: Int) = arrayOfNulls<OtherUser>(size)
    }
}

// ─── Conversation updates (lightweight poll target) ────────────────────────────

data class ConversationUpdatesResponse(val data: List<ConversationUpdate>?)

data class ConversationUpdate(
    val id: String,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    @SerializedName("my_unread") val myUnread: Int? = null,
    @SerializedName("other_user_last_seen_at") val otherUserLastSeenAt: String? = null
)

// ─── Messages ─────────────────────────────────────────────────────────────────

data class MessagesResponse(
    val data: List<Message>?,
    val message: String?,
    val meta: MessagesMeta?
)

data class MessagesMeta(
    @SerializedName("has_more") val hasMore: Boolean? = null,
    @SerializedName("oldest_id") val oldestId: String? = null,
    @SerializedName("newest_id") val newestId: String? = null
)

data class Message(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String? = null,
    val text: String?,
    // Deprecated but unchanged — kept alongside sender_user_id so old and new
    // backend builds both decode. See docs/API_REFERENCE.md "Deprecations".
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("sender_user_id") val senderUserId: Int? = null,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String?
) {
    val isRead: Boolean get() = status == "read"
}

// ─── Send Message ──────────────────────────────────────────────────────────────

data class SendMessageRequest(val text: String)

data class SendMessageResponse(
    val data: Message?,
    val message: String?
)

// ─── Create Conversation ───────────────────────────────────────────────────────

data class CreateConversationRequest(
    @SerializedName("listing_id") val listingId: String
)

data class CreateConversationResponse(
    val data: Conversation?,
    val message: String?
)

// ─── Notifications ────────────────────────────────────────────────────────────

// GET /notifications was removed server-side (never had a route — the
// notification list is served from GET /user-data, see docs/CHANGELOG.md's
// "hardening pass": "Removed NotificationController::index() — unreachable
// dead code"). Notifications + their unread count come from UserDataResponse
// below instead of a standalone endpoint.

data class UserDataResponse(val data: UserDataPayload?)

data class UserDataPayload(
    val notifications: List<AppNotification>?,
    @SerializedName("unread_notifications") val unreadNotifications: Int? = null
)

data class AppNotification(
    val id: String,
    val type: String?,
    @SerializedName("title_ar") val titleAr: String?,
    @SerializedName("body_ar") val bodyAr: String?,
    @SerializedName("target_type") val targetType: String? = null,
    @SerializedName("target_id") val targetId: String? = null,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String?
)
