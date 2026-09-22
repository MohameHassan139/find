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
        adapter = ConversationsAdapter(
            onClick = { conv ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CONVERSATION, conv)
                }
                chatLauncher.launch(intent)
                applyPushTransition()
            },
            onLongClick = { anchorView, conv ->
                val pos = adapter.currentList.indexOf(conv)
                showConversationOptions(anchorView, conv, pos)
                true
            }
        )
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = adapter
        setupSwipeToDelete()
    }

    private fun showConversationOptions(anchorView: View, conv: com.example.myapplication.chat.model.Conversation, position: Int) {
        val popup = android.widget.PopupMenu(this, anchorView)
        val favTitle = if (conv.isFavorite) {
            getString(R.string.chat_favorite_remove)
        } else {
            getString(R.string.chat_favorite_add)
        }
        popup.menu.add(0, 1, 0, favTitle)

        val deleteItem = popup.menu.add(0, 2, 1, getString(R.string.chat_delete))
        val redTitle = android.text.SpannableString(deleteItem.title)
        redTitle.setSpan(
            android.text.style.ForegroundColorSpan(
                androidx.core.content.ContextCompat.getColor(this, R.color.error_red)
            ),
            0,
            redTitle.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        deleteItem.title = redTitle

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    viewModel.toggleFavorite(conv.id)
                    true
                }
                2 -> {
                    confirmDeleteConversation(conv, if (position >= 0) position else null)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val conv = adapter.getItemAt(pos)
                if (conv != null) {
                    confirmDeleteConversation(conv, pos)
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvConversations)
    }

    private fun confirmDeleteConversation(conv: com.example.myapplication.chat.model.Conversation, position: Int? = null) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.chat_delete_confirm_title))
            .setMessage(getString(R.string.chat_delete_confirm_body))
            .setPositiveButton(getString(R.string.chat_delete)) { _, _ ->
                viewModel.deleteConversation(conv.id) { success ->
                    if (!success) {
                        android.widget.Toast.makeText(this, R.string.chat_delete_failed, android.widget.Toast.LENGTH_SHORT).show()
                        if (position != null) {
                            adapter.notifyItemChanged(position)
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.logout_confirm_no)) { _, _ ->
                if (position != null) {
                    adapter.notifyItemChanged(position)
                }
            }
            .setOnCancelListener {
                if (position != null) {
                    adapter.notifyItemChanged(position)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.error_red)
            )
        }
        dialog.show()
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
        if (viewModel.getCurrentFilter() == ConversationsViewModel.Filter.FAVORITE) {
            binding.tvEmptyMessage.setText(R.string.chat_favorites_empty)
        } else {
            binding.tvEmptyMessage.text = "لا توجد محادثات"
        }
    }

    private fun showError(message: String) {
        binding.rvConversations.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
        binding.btnRetry.setOnClickListener { viewModel.loadConversations() }
    }
}
