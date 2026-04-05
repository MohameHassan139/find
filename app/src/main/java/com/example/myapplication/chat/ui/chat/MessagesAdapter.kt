package com.example.myapplication.chat.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.model.Message
import com.example.myapplication.chat.utils.DateUtils

class MessagesAdapter(context: Context) :
    ListAdapter<Message, MessagesAdapter.VH>(DIFF) {

    private val myUserId = TokenManager.getUserId(context)

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    private fun isMine(msg: Message): Boolean {
        if (myUserId.isEmpty() || msg.senderId.isNullOrEmpty()) return false
        return myUserId == msg.senderId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val layoutSent: LinearLayout = view.findViewById(R.id.layoutSent)
        private val layoutReceived: LinearLayout = view.findViewById(R.id.layoutReceived)
        private val tvSent: TextView = view.findViewById(R.id.tvMessageSent)
        private val tvReceived: TextView = view.findViewById(R.id.tvMessageReceived)
        private val tvTimeSent: TextView = view.findViewById(R.id.tvTimeSent)
        private val tvTimeReceived: TextView = view.findViewById(R.id.tvTimeReceived)
        private val ivRead: ImageView = view.findViewById(R.id.ivReadStatus)

        fun bind(msg: Message) {
            if (isMine(msg)) {
                layoutSent.visibility = View.VISIBLE
                layoutReceived.visibility = View.GONE
                tvSent.text = msg.text
                tvTimeSent.text = DateUtils.formatMessageTime(msg.createdAt)
                ivRead.setImageResource(
                    if (msg.isRead) R.drawable.ic_double_check else R.drawable.ic_single_check
                )
            } else {
                layoutSent.visibility = View.GONE
                layoutReceived.visibility = View.VISIBLE
                tvReceived.text = msg.text
                tvTimeReceived.text = DateUtils.formatMessageTime(msg.createdAt)
            }
        }
    }
}
