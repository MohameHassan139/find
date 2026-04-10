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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.ui.chat.ChatActivity
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ActivityListingConversationsBinding
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

class ListingConversationsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
        const val EXTRA_LISTING_TITLE = "listing_title"
    }

    private lateinit var binding: ActivityListingConversationsBinding
    private lateinit var api: com.example.myapplication.chat.api.FindApiService

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

        api = RetrofitClient.build(this)

        val listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: ""
        val listingTitle = intent.getStringExtra(EXTRA_LISTING_TITLE) ?: "رسائل الإعلان"

        binding.tvTitle.text = listingTitle
        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { loadConversations(listingId) }

        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        loadConversations(listingId)
    }

    private fun loadConversations(listingId: String = intent.getStringExtra(EXTRA_LISTING_ID) ?: "") {
        showLoading()
        lifecycleScope.launch {
            try {
                val response = api.getConversations()
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    val all = response.body()?.data ?: emptyList()
                    val filtered = all.filter { it.listingId == listingId }
                    if (filtered.isEmpty()) showEmpty() else showList(filtered)
                } else {
                    showError("تعذر التحميل: ${response.code()}")
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                showError("تعذر الاتصال بالخادم")
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
        binding.tvEmpty.text = getString(R.string.kt_str_0a896d74)
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
        val myId = TokenManager.getUserId(holder.root.context)
        val isBuyer = conv.buyerId == myId
        holder.tvName.text = if (isBuyer) conv.sellerName ?: "مجهول" else conv.buyerName ?: "مجهول"
        holder.tvLastMessage.text = conv.lastMessage ?: ""
        holder.tvTime.text = DateUtils.formatConversationTime(conv.lastMessageAt)
        val unread = if (isBuyer) conv.buyerUnread else conv.sellerUnread
        holder.tvUnreadBadge.visibility = if (unread > 0) View.VISIBLE else View.GONE
        holder.tvUnreadBadge.text = unread.toString()
        holder.root.setOnClickListener { onClick(conv) }
    }

    override fun getItemCount() = items.size
}
