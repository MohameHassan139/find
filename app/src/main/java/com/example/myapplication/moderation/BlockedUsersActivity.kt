package com.example.myapplication.moderation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.BaseActivity
import com.example.myapplication.R
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.BlockedUserDto
import com.example.myapplication.chat.utils.DateUtils
import com.example.myapplication.databinding.ActivityBlockedUsersBinding
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.ModerationDialogs
import com.example.myapplication.utils.ModerationState
import kotlinx.coroutines.launch

class BlockedUsersActivity : BaseActivity() {

    private lateinit var binding: ActivityBlockedUsersBinding
    private lateinit var adapter: BlockedUsersAdapter

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finishWithPop() }

        adapter = BlockedUsersAdapter { user -> unblock(user) }
        binding.rvBlockedUsers.layoutManager = LinearLayoutManager(this)
        binding.rvBlockedUsers.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadBlockedUsers() }
        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        showLoading()
        lifecycleScope.launch {
            val api = RetrofitClient.build(this@BlockedUsersActivity)
            ModerationState.refresh(api)
            binding.swipeRefresh.isRefreshing = false
            val items = ModerationState.cached()
            if (items.isEmpty()) showEmpty() else showList(items)
        }
    }

    private fun unblock(user: BlockedUserDto) {
        val api = RetrofitClient.build(this)
        ModerationDialogs.unblock(this, api, user.id) {
            val items = ModerationState.cached()
            if (items.isEmpty()) showEmpty() else showList(items)
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvBlockedUsers.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
    }

    private fun showList(items: List<BlockedUserDto>) {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.rvBlockedUsers.visibility = View.VISIBLE
        adapter.submitList(items)
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.rvBlockedUsers.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = getString(R.string.blocked_empty)
    }
}

class BlockedUsersAdapter(
    private val onUnblock: (BlockedUserDto) -> Unit
) : ListAdapter<BlockedUserDto, BlockedUsersAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockedUserDto>() {
            override fun areItemsTheSame(a: BlockedUserDto, b: BlockedUserDto) = a.id == b.id
            override fun areContentsTheSame(a: BlockedUserDto, b: BlockedUserDto) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), onUnblock)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvBlockedAt: TextView = view.findViewById(R.id.tvBlockedAt)
        private val btnUnblock: TextView = view.findViewById(R.id.btnUnblock)

        fun bind(user: BlockedUserDto, onUnblock: (BlockedUserDto) -> Unit) {
            tvName.text = user.name ?: itemView.context.getString(R.string.blocked_unknown_user)
            tvBlockedAt.text = DateUtils.formatConversationTime(user.blockedAt)
            if (!user.avatar.isNullOrEmpty()) {
                Glide.with(ivAvatar.context)
                    .load(user.avatar)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }
            btnUnblock.setOnClickListener { onUnblock(user) }
        }
    }
}