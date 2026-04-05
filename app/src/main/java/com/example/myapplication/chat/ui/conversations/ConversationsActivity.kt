package com.example.myapplication.chat.ui.conversations

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.chat.ui.chat.ChatActivity
import com.example.myapplication.chat.utils.Result
import com.example.myapplication.databinding.ActivityConversationsBinding

class ConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationsBinding
    private val viewModel: ConversationsViewModel by viewModels()
    private lateinit var adapter: ConversationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFilterChips()
        setupObservers()
        setupSwipeRefresh()
    }

    private val chatLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { viewModel.loadConversations() }

    private fun setupRecyclerView() {
        adapter = ConversationsAdapter { conv ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONVERSATION, conv)
            }
            chatLauncher.launch(intent)
        }
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            setChipSelected(ConversationsViewModel.Filter.ALL)
        }
        binding.chipUnread.setOnClickListener {
            setChipSelected(ConversationsViewModel.Filter.UNREAD)
        }
        binding.chipFavorite.setOnClickListener {
            setChipSelected(ConversationsViewModel.Filter.FAVORITE)
        }
    }

    private fun setChipSelected(filter: ConversationsViewModel.Filter) {
        binding.chipAll.isSelected = filter == ConversationsViewModel.Filter.ALL
        binding.chipUnread.isSelected = filter == ConversationsViewModel.Filter.UNREAD
        binding.chipFavorite.isSelected = filter == ConversationsViewModel.Filter.FAVORITE
        viewModel.setFilter(filter)
    }

    private fun setupObservers() {
        viewModel.conversations.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is Result.Loading -> showShimmer()
                is Result.Success -> {
                    hideShimmer()
                    if (result.data.isEmpty()) showEmpty() else {
                        showContent()
                        adapter.submitList(result.data)
                    }
                }
                is Result.Error -> {
                    hideShimmer()
                    showError(result.message)
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadConversations() }
        binding.swipeRefresh.setColorSchemeResources(R.color.find_primary)
    }

    private fun showShimmer() {
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.shimmerLayout.startShimmer()
        binding.rvConversations.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun hideShimmer() {
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
    }

    private fun showContent() {
        binding.rvConversations.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.rvConversations.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.rvConversations.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
        binding.btnRetry.setOnClickListener { viewModel.loadConversations() }
    }
}
