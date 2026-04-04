package com.example.myapplication.chat.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
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
            b.tvName.text = conv.otherUser?.name ?: "مجهول"
            b.tvLastMessage.text = conv.lastMessage?.body ?: ""
            b.tvTime.text = DateUtils.formatConversationTime(conv.lastMessage?.createdAt)

            if (conv.unreadCount > 0) {
                b.tvUnreadBadge.text = conv.unreadCount.toString()
                b.tvUnreadBadge.visibility = android.view.View.VISIBLE
                b.tvUnreadDay.visibility = android.view.View.VISIBLE
            } else {
                b.tvUnreadBadge.visibility = android.view.View.GONE
                b.tvUnreadDay.visibility = android.view.View.GONE
            }

            val avatar = conv.otherUser?.avatar
            if (!avatar.isNullOrEmpty()) {
                Glide.with(b.ivAvatar.context)
                    .load(avatar)
                    .placeholder(R.drawable.ic_avatar_placeholder)
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
