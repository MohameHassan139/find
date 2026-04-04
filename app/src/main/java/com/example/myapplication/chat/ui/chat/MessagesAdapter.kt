package com.example.myapplication.chat.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.chat.model.Message
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ItemMessageReceivedBinding
import com.example.myapplication.databinding.ItemMessageSentBinding

class MessagesAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        const val VIEW_TYPE_SENT = 1
        const val VIEW_TYPE_RECEIVED = 2

        val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            SentVH(ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            ReceivedVH(ItemMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is SentVH -> holder.bind(msg)
            is ReceivedVH -> holder.bind(msg)
        }
    }

    class SentVH(private val b: ItemMessageSentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            b.tvMessage.text = msg.body
            b.tvTime.text = DateUtils.formatMessageTime(msg.createdAt)
            b.ivReadStatus.setImageResource(
                if (msg.isRead) R.drawable.ic_double_check
                else R.drawable.ic_single_check
            )
        }
    }

    class ReceivedVH(private val b: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            b.tvMessage.text = msg.body
            b.tvTime.text = DateUtils.formatMessageTime(msg.createdAt)
        }
    }
}
