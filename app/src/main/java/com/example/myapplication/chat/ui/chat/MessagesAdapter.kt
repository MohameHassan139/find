package com.example.myapplication.chat.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.model.Message
import com.example.myapplication.chat.utils.DateUtils

class MessagesAdapter(private val context: Context) :
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

    private fun isRtl(): Boolean {
        val config = context.resources.configuration
        return config.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
    )

    override fun submitList(list: List<Message>?) {
        submitList(list, null)
    }

    override fun submitList(list: List<Message>?, commitCallback: Runnable?) {
        val oldList = currentList.toList()
        super.submitList(list) {
            val newList = currentList
            if (oldList.isNotEmpty()) {
                for (i in 0 until newList.size) {
                    val oldNextSenderId = oldList.getOrNull(i + 1)?.senderId
                    val newNextSenderId = newList.getOrNull(i + 1)?.senderId
                    if (oldNextSenderId != newNextSenderId) {
                        notifyItemChanged(i)
                    }
                }
            }
            commitCallback?.run()
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = getItem(position)
        val nextMsg = if (position < itemCount - 1) getItem(position + 1) else null
        val showTail = nextMsg == null || nextMsg.senderId != msg.senderId
        holder.bind(msg, showTail)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val layoutSent: View = view.findViewById(R.id.layoutSent)
        private val layoutReceived: View = view.findViewById(R.id.layoutReceived)
        private val tvSent: TextView = view.findViewById(R.id.tvMessageSent)
        private val tvReceived: TextView = view.findViewById(R.id.tvMessageReceived)
        private val tvTimeSent: TextView = view.findViewById(R.id.tvTimeSent)
        private val tvTimeReceived: TextView = view.findViewById(R.id.tvTimeReceived)
        private val ivRead: ImageView = view.findViewById(R.id.ivReadStatus)

        fun bind(msg: Message, showTail: Boolean) {
            val rtl = isRtl()
            val density = context.resources.displayMetrics.density

            val fillColorSent = ContextCompat.getColor(context, R.color.bubble_sent)
            val strokeColorSent = ContextCompat.getColor(context, R.color.bubble_stroke_sent)
            val fillColorReceived = ContextCompat.getColor(context, R.color.bubble_received)
            val strokeColorReceived = ContextCompat.getColor(context, R.color.bubble_stroke_received)

            if (isMine(msg)) {
                layoutSent.visibility = View.VISIBLE
                layoutReceived.visibility = View.GONE
                tvSent.text = msg.text
                tvTimeSent.text = DateUtils.formatMessageTime(msg.createdAt)
                ivRead.setImageResource(
                    if (msg.isRead) R.drawable.ic_double_check else R.drawable.ic_single_check
                )

                // Sent message bubble tail: bottom-left in RTL (AR), bottom-right in LTR (EN)
                val tailOnLeft = rtl
                val drawable = BubbleDrawable(
                    fillColor = fillColorSent,
                    strokeColor = strokeColorSent,
                    strokeWidth = 1f * density,
                    cornerRadius = 16f * density,
                    tailWidth = 10f * density,
                    tailHeight = 10f * density,
                    isTailOnLeft = tailOnLeft,
                    drawTail = showTail
                )
                layoutSent.background = drawable

            } else {
                layoutSent.visibility = View.GONE
                layoutReceived.visibility = View.VISIBLE
                tvReceived.text = msg.text
                tvTimeReceived.text = DateUtils.formatMessageTime(msg.createdAt)

                // Received message bubble tail: bottom-right in RTL (AR), bottom-left in LTR (EN)
                val tailOnLeft = !rtl
                val drawable = BubbleDrawable(
                    fillColor = fillColorReceived,
                    strokeColor = strokeColorReceived,
                    strokeWidth = 1f * density,
                    cornerRadius = 16f * density,
                    tailWidth = 10f * density,
                    tailHeight = 10f * density,
                    isTailOnLeft = tailOnLeft,
                    drawTail = showTail
                )
                layoutReceived.background = drawable
            }
        }
    }
}
