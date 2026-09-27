package com.example.myapplication.chat.ui.conversations

import com.example.myapplication.R
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.ui.chat.ChatActivity
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ActivityListingConversationsBinding
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class ListingConversationsActivity : BaseActivity() {

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
        const val EXTRA_LISTING_TITLE = "listing_title"
    }

    private lateinit var binding: ActivityListingConversationsBinding

    private val chatLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadConversations() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityListingConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        val listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: ""
        val listingTitle = intent.getStringExtra(EXTRA_LISTING_TITLE) ?: getString(R.string.ad_messages_title)

        binding.tvTitle.text = listingTitle
        binding.btnBack.setOnClickListener { finishWithPop() }
        binding.swipeRefresh.setOnRefreshListener { loadConversations(listingId) }

        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        loadConversations(listingId)
    }

    private fun loadConversations(listingId: String = intent.getStringExtra(EXTRA_LISTING_ID) ?: "") {
        showLoading()
        lifecycleScope.launch {
            val result = AppContainer.chat.conversations()
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is ApiResult.Success -> {
                    val filtered = result.data.filter { it.listingId == listingId }
                    if (filtered.isEmpty()) showEmpty() else showList(filtered)
                }
                is ApiResult.HttpError -> showError(getString(R.string.error_failed_to_load, result.code))
                is ApiResult.NetworkError -> showError(getString(R.string.error_server_unreachable))
            }
        }
    }

    private fun showList(conversations: List<Conversation>) {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.rvConversations.visibility = View.VISIBLE
        binding.rvConversations.adapter = ListingConvAdapter(conversations) { conv ->
            chatLauncher.launch(
                Intent(this, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CONVERSATION, conv)
                }
            )
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.rvConversations.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = getString(R.string.no_conversations_for_ad)
        binding.rvConversations.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
        binding.rvConversations.visibility = View.GONE
    }
}

class ListingConvAdapter(
    private val items: List<Conversation>,
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ListingConvAdapter.VH>() {

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val tvName: TextView = root.findViewById(com.example.myapplication.R.id.tvName)
        val tvLastMessage: TextView = root.findViewById(com.example.myapplication.R.id.tvLastMessage)
        val tvTime: TextView = root.findViewById(com.example.myapplication.R.id.tvTime)
        val tvUnreadBadge: TextView = root.findViewById(com.example.myapplication.R.id.tvUnreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.example.myapplication.R.layout.item_conversation, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conv = items[position]
        holder.tvName.text = conv.otherUser?.name ?: holder.tvName.context.getString(R.string.blocked_unknown_user)
        holder.tvLastMessage.text = conv.lastMessage ?: ""
        holder.tvTime.text = DateUtils.formatConversationTime(
            conv.lastMessageAt,
            holder.tvTime.context.getString(com.example.myapplication.R.string.yesterday)
        )
        val unread = conv.myUnread
        holder.tvUnreadBadge.visibility = if (unread > 0) View.VISIBLE else View.GONE
        holder.tvUnreadBadge.text = unread.toString()
        holder.root.setOnClickListener { onClick(conv) }
    }

    override fun getItemCount() = items.size
}
