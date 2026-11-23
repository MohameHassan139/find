package com.example.myapplication.chat.ui.conversations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.chat.utils.LastSentManager
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ItemConversationBinding

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: (View, Conversation) -> Boolean = { _, _ -> false }
) : ListAdapter<Conversation, ConversationsAdapter.VH>(DIFF) {

    fun getItemAt(position: Int): Conversation? =
        if (position in 0 until itemCount) getItem(position) else null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemConversationBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(conv: Conversation) {
            // Show the other party's info — the server already resolves
            // "other_user" relative to the caller, no buyer/seller split needed.
            val otherName = conv.otherUser?.name
            val otherAvatar = conv.otherUser?.avatar
            val unreadCount = conv.myUnread

            b.tvName.text = otherName ?: itemView.context.getString(R.string.user_default_name)
            b.tvLastMessage.text = conv.lastMessage ?: ""
            val timeStr = DateUtils.formatConversationTime(
                conv.lastMessageAt,
                itemView.context.getString(R.string.yesterday)
            )
            // Only display checkmarks for messages sent by the user, never for incoming messages (matching iOS ConversationRow).
            val myUserId = TokenManager.getUserId(itemView.context)
            val myUserIdInt = myUserId.toIntOrNull()
            val isSentByMe = when {
                unreadCount > 0 -> false
                myUserIdInt != null && conv.lastMessageSenderUserId != null -> conv.lastMessageSenderUserId == myUserIdInt
                !conv.lastSenderId.isNullOrEmpty() && myUserId.isNotEmpty() -> conv.lastSenderId == myUserId
                !conv.lastMessageSenderId.isNullOrEmpty() && myUserId.isNotEmpty() -> conv.lastMessageSenderId == myUserId
                conv.isLastMessageFromMe != null -> conv.isLastMessageFromMe
                else -> LastSentManager.isLastMessageMine(itemView.context, conv.id, conv.lastMessage)
            }

            if (unreadCount > 0) {
                b.tvTime.visibility = View.GONE
                b.tvUnreadDay.visibility = View.VISIBLE
                b.tvUnreadDay.text = timeStr
                b.tvUnreadBadge.text = unreadCount.toString()
                b.tvUnreadBadge.visibility = View.VISIBLE
                b.ivCheck.visibility = View.GONE
            } else {
                b.tvTime.visibility = View.VISIBLE
                b.tvTime.text = timeStr
                b.tvTime.setTextColor(
                    androidx.core.content.ContextCompat.getColor(b.root.context, R.color.chats_item_time)
                )
                b.tvUnreadDay.visibility = View.GONE
                b.tvUnreadBadge.visibility = View.GONE

                if (isSentByMe) {
                    b.ivCheck.visibility = View.VISIBLE
                    val status = conv.lastMessageStatus ?: if (LastSentManager.isLastMessageRead(itemView.context, conv.id)) "read" else "sent"
                    when (status) {
                        "read" -> {
                            b.ivCheck.setImageResource(R.drawable.ic_double_check)
                            b.ivCheck.setColorFilter(
                                android.graphics.Color.parseColor("#007AFF"),
                                android.graphics.PorterDuff.Mode.SRC_IN
                            )
                        }
                        "delivered" -> {
                            b.ivCheck.setImageResource(R.drawable.ic_double_check)
                            b.ivCheck.setColorFilter(
                                android.graphics.Color.parseColor("#8E8E93"),
                                android.graphics.PorterDuff.Mode.SRC_IN
                            )
                        }
                        else -> { // "sent"
                            b.ivCheck.setImageResource(R.drawable.ic_single_check)
                            b.ivCheck.setColorFilter(
                                android.graphics.Color.parseColor("#8E8E93"),
                                android.graphics.PorterDuff.Mode.SRC_IN
                            )
                        }
                    }
                } else {
                    b.ivCheck.visibility = View.GONE
                }
            }

            if (!otherAvatar.isNullOrEmpty()) {
                Glide.with(b.ivAvatar.context)
                    .load(otherAvatar)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(b.ivAvatar)
            } else {
                b.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }

            b.ivFavoriteStar.visibility = if (conv.isFavorite) View.VISIBLE else View.GONE

            b.root.setOnClickListener { onClick(conv) }
            b.root.setOnLongClickListener { onLongClick(it, conv) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(a: Conversation, b: Conversation) = a.id == b.id
            override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
        }
    }
}
