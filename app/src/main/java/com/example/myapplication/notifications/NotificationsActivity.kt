package com.example.myapplication.notifications

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.MenuActivity
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.AppNotification
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ActivityNotificationsBinding
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.launch

class NotificationsActivity : BaseActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        val api = RetrofitClient.build(this)

        adapter = NotificationsAdapter()
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(android.content.Intent(this, MenuActivity::class.java))
        }

        binding.btnMarkAllRead.setOnClickListener {
            lifecycleScope.launch {
                try {
                    api.markAllNotificationsRead()
                    // Refresh list after marking all read
                    loadNotifications(api)
                    Toast.makeText(this@NotificationsActivity, getString(R.string.kt_str_17c3b29d), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@NotificationsActivity, getString(R.string.kt_str_e066498f), Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener { loadNotifications(api) }

        loadNotifications(api)
    }

    private fun loadNotifications(api: com.example.myapplication.chat.api.FindApiService) {
        showLoading()
        lifecycleScope.launch {
            try {
                val response = api.getNotifications()
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    val body = response.body()
                    val items = body?.data ?: emptyList()
                    val unread = body?.meta?.unreadCount
                        ?: body?.unreadCount
                        ?: items.count { !it.isRead }
                    binding.tvUnreadCount.text = if (unread > 0) "($unread غير مقروء)" else ""
                    binding.tvUnreadCount.visibility = if (unread > 0) View.VISIBLE else View.GONE
                    if (items.isEmpty()) showEmpty() else showList(items)
                } else {
                    showError("تعذر التحميل: ${response.code()}")
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                showError("تعذر الاتصال بالخادم")
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
        binding.tvEmpty.text = getString(R.string.kt_str_e9867f64)
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvNotifications.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
    }
}

class NotificationsAdapter : ListAdapter<AppNotification, NotificationsAdapter.VH>(DIFF) {

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
        private val viewUnread: View = view.findViewById(R.id.viewUnreadDot)

        fun bind(n: AppNotification) {
            tvTitle.text = n.titleAr ?: ""
            tvBody.text = n.bodyAr ?: ""
            tvTime.text = DateUtils.formatConversationTime(n.createdAt)
            viewUnread.visibility = if (!n.isRead) View.VISIBLE else View.INVISIBLE
            val ctx = itemView.context
            itemView.setBackgroundColor(
                if (!n.isRead)
                    ctx.getColor(R.color.notif_unread_bg)
                else
                    ctx.getColor(R.color.notif_read_bg)
            )
        }
    }
}
