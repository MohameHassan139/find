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
    val id: Int,
    @SerializedName("other_user") val otherUser: OtherUser?,
    @SerializedName("last_message") val lastMessage: LastMessage?,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("created_at") val createdAt: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readParcelable(OtherUser::class.java.classLoader),
        parcel.readParcelable(LastMessage::class.java.classLoader),
        parcel.readInt(),
        parcel.readString()
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeParcelable(otherUser, flags)
        parcel.writeParcelable(lastMessage, flags)
        parcel.writeInt(unreadCount)
        parcel.writeString(createdAt)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<Conversation> {
        override fun createFromParcel(parcel: Parcel) = Conversation(parcel)
        override fun newArray(size: Int) = arrayOfNulls<Conversation>(size)
    }
}

data class OtherUser(
    val id: Int,
    val name: String?,
    val avatar: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString(),
        parcel.readString()
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
        parcel.writeString(avatar)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<OtherUser> {
        override fun createFromParcel(parcel: Parcel) = OtherUser(parcel)
        override fun newArray(size: Int) = arrayOfNulls<OtherUser>(size)
    }
}

data class LastMessage(
    val body: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("is_read") val isRead: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString(),
        parcel.readByte() != 0.toByte()
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(body)
        parcel.writeString(createdAt)
        parcel.writeByte(if (isRead) 1 else 0)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<LastMessage> {
        override fun createFromParcel(parcel: Parcel) = LastMessage(parcel)
        override fun newArray(size: Int) = arrayOfNulls<LastMessage>(size)
    }
}

// ─── Messages ─────────────────────────────────────────────────────────────────

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

// ─── Send Message ──────────────────────────────────────────────────────────────

data class SendMessageRequest(val body: String)

data class SendMessageResponse(
    val data: Message?,
    val message: String?,
    val status: Boolean?
)

// ─── Create Conversation ───────────────────────────────────────────────────────

data class CreateConversationRequest(
    @SerializedName("receiver_id") val receiverId: Int
)

data class CreateConversationResponse(
    val data: Conversation?,
    val message: String?,
    val status: Boolean?
)
