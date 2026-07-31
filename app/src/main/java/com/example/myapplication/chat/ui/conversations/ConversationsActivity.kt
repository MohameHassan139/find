package com.example.myapplication.chat.ui.conversations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.chat.ui.chat.ChatActivity
import com.example.myapplication.chat.utils.Result
import com.example.myapplication.databinding.ActivityConversationsBinding
import com.example.myapplication.push.FcmService
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper

class ConversationsActivity : BaseActivity() {

    private lateinit var binding: ActivityConversationsBinding
    private val viewModel: ConversationsViewModel by viewModels()
    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private lateinit var adapter: ConversationsAdapter

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(
            appBarId = R.id.llAppBar,
            bottomNavId = R.id.cvBottomNav
        )

        setupRecyclerView()
        setupFilterChips()
        setupObservers()
        setupSwipeRefresh()
        BottomNavHelper.setup(this, NavScreen.CHAT)
        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)

        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }
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
            applyPushTransition()
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
        setupChip(binding.chipAll, filter == ConversationsViewModel.Filter.ALL)
        setupChip(binding.chipUnread, filter == ConversationsViewModel.Filter.UNREAD)
        setupChip(binding.chipFavorite, filter == ConversationsViewModel.Filter.FAVORITE)
        viewModel.setFilter(filter)
    }

    private fun setupChip(chip: android.widget.TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected)
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_unselected)
        }
        chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.chats_chip_text))
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
                        openConversationFromPushIfPending(result.data)
                    }
                }
                is Result.Error -> {
                    hideShimmer()
                    showError(result.message)
                }
            }
        }
    }

    /** Deep-link from a tapped push notification (see FcmService) — one-shot: the extra is
     * removed immediately so it doesn't re-fire on later list refreshes/rotations. */
    private fun openConversationFromPushIfPending(conversations: List<com.example.myapplication.chat.model.Conversation>) {
        val openId = intent.getStringExtra(FcmService.EXTRA_OPEN_CONVERSATION_ID) ?: return
        intent.removeExtra(FcmService.EXTRA_OPEN_CONVERSATION_ID)
        val conversation = conversations.find { it.id == openId } ?: return
        val chatIntent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CONVERSATION, conversation)
        }
        chatLauncher.launch(chatIntent)
        applyPushTransition()
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
