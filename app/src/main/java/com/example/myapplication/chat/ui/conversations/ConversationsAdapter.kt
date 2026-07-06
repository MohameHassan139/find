package com.example.myapplication.chat.ui.conversations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ItemConversationBinding

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.VH>(DIFF) {

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
            val myId = TokenManager.getUserId(b.root.context)
            val isBuyer = conv.buyerId == myId

            // Show the other party's info
            val otherName = if (isBuyer) conv.sellerName else conv.buyerName
            val otherAvatar = if (isBuyer) conv.sellerAvatar else conv.buyerAvatar
            val unreadCount = if (isBuyer) conv.buyerUnread else conv.sellerUnread
            val otherUnread = if (isBuyer) conv.sellerUnread else conv.buyerUnread

            b.tvName.text = otherName ?: "مجهول"
            b.tvLastMessage.text = conv.lastMessage ?: ""
            val timeStr = DateUtils.formatConversationTime(conv.lastMessageAt)
            val isSentByMe = if (!conv.lastSenderId.isNullOrEmpty()) {
                conv.lastSenderId == myId
            } else {
                otherUnread > 0
            }

            if (unreadCount > 0) {
                b.tvTime.visibility = View.GONE
                b.tvUnreadDay.visibility = View.VISIBLE
                b.tvUnreadDay.text = if (timeStr.isNotEmpty()) timeStr else "الأحد"
                b.tvUnreadBadge.text = unreadCount.toString()
                b.tvUnreadBadge.visibility = View.VISIBLE
                b.ivCheck.visibility = View.GONE
            } else {
                b.tvTime.visibility = View.VISIBLE
                b.tvTime.text = if (timeStr.isNotEmpty()) timeStr else "9:00 م"
                b.tvTime.setTextColor(
                    androidx.core.content.ContextCompat.getColor(b.root.context, R.color.chats_item_time)
                )
                b.tvUnreadDay.visibility = View.GONE
                b.tvUnreadBadge.visibility = View.GONE

                if (isSentByMe) {
                    b.ivCheck.visibility = View.VISIBLE
                    val checkColor = if (otherUnread > 0) {
                        android.graphics.Color.parseColor("#AAAAAA")
                    } else {
                        android.graphics.Color.parseColor("#007AFF")
                    }
                    b.ivCheck.setColorFilter(checkColor, android.graphics.PorterDuff.Mode.SRC_IN)
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

            b.root.setOnClickListener { onClick(conv) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(a: Conversation, b: Conversation) = a.id == b.id
            override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
        }
    }
}
