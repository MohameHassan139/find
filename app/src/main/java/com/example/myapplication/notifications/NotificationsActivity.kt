package com.example.myapplication.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ListingDetailActivity
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.chat.model.AppNotification
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ActivityNotificationsBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.SwipeRefreshHelper
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import kotlinx.coroutines.launch
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class NotificationsActivity : BaseActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter
    private val sharedVm: SharedCategoriesViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)
        BottomNavHelper.setup(this, NavScreen.NONE)

        adapter = NotificationsAdapter { notification ->
            when (notification.targetType?.lowercase()) {
                "listing", "ad" -> {
                    notification.targetId?.let { listingId ->
                        val intent = Intent(this, ListingDetailActivity::class.java).apply {
                            putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listingId)
                        }
                        startWithPush(intent)
                    }
                }
                "conversation", "chat" -> {
                    val intent = Intent(this, ConversationsActivity::class.java)
                    startWithPush(intent)
                }
            }
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }

        binding.btnMarkAllRead.setOnClickListener {
            lifecycleScope.launch {
                if (AppContainer.notifications.markAllRead() is ApiResult.NetworkError) {
                    toast(R.string.error_update_failed)
                } else {
                    loadNotifications()
                    toast(R.string.notifications_marked_read)
                }
            }
        }

        SwipeRefreshHelper.setup(binding.swipeRefresh) { loadNotifications() }

        loadNotifications()
    }

    private fun loadNotifications() {
        showLoading()
        lifecycleScope.launch {
            // 1. Send request to server to mark all notifications as read
            launch {
                AppContainer.notifications.markAllRead()
            }

            // 2. Load notifications
            val result = AppContainer.notifications.userData()
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is ApiResult.Success -> {
                    val payload = result.data
                    val rawItems = payload?.notifications.orEmpty()

                    // 3. Handle locally: Mark all items as read and clear unread count
                    val items = rawItems.map { it.copy(isRead = true) }
                    binding.tvUnreadCount.text = ""
                    binding.tvUnreadCount.visibility = View.GONE

                    if (items.isEmpty()) showEmpty() else showList(items)
                }
                is ApiResult.HttpError -> showError("تعذر التحميل: ${result.code}")
                is ApiResult.NetworkError -> showError("تعذر الاتصال بالخادم")
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvNotifications.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
    }

    private fun showList(items: List<AppNotification>) {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.rvNotifications.visibility = View.VISIBLE
        adapter.submitList(items)
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvNotifications.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = getString(R.string.notifications_empty)
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvNotifications.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
    }
}

class NotificationsAdapter(
    private val onItemClick: ((AppNotification) -> Unit)? = null
) : ListAdapter<AppNotification, NotificationsAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppNotification>() {
            override fun areItemsTheSame(a: AppNotification, b: AppNotification) = a.id == b.id
            override fun areContentsTheSame(a: AppNotification, b: AppNotification) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvBody: TextView = view.findViewById(R.id.tvBody)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val viewUnreadDot: View = view.findViewById(R.id.viewUnreadDot)
        private val viewUnreadBar: View = view.findViewById(R.id.viewUnreadBar)

        fun bind(n: AppNotification) {
            tvTitle.text = n.titleAr ?: ""
            tvBody.text = n.bodyAr ?: ""
            tvTime.text = DateUtils.formatConversationTime(
                n.createdAt,
                tvTime.context.getString(R.string.yesterday)
            )
            val unreadVisibility = if (!n.isRead) View.VISIBLE else View.INVISIBLE
            viewUnreadDot.visibility = unreadVisibility
            viewUnreadBar.visibility = unreadVisibility

            itemView.setOnClickListener {
                onItemClick?.invoke(n)
            }
        }
    }
}
