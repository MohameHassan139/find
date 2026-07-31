package com.example.myapplication.chat.ui.chat

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.MenuActivity
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.model.ReportTargetType
import com.example.myapplication.chat.utils.Result
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.ModerationDialogs
import com.example.myapplication.utils.ModerationState
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    companion object {
        const val EXTRA_CONVERSATION = "extra_conversation"
    }

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter
    private lateinit var conversation: Conversation

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        conversation = intent.getParcelableExtra(EXTRA_CONVERSATION)!!

        lifecycleScope.launch {
            ModerationState.refresh(RetrofitClient.build(this@ChatActivity))
        }

        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        setupObservers()
        setupKeyboardHandling()
        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)

        viewModel.init(conversation.id)
    }

    private fun setupKeyboardHandling() {
        // Store original padding
        val originalPaddingBottom = binding.llMessageInputBar.paddingBottom
        
        // Handle keyboard insets to move input bar above keyboard
        ViewCompat.setOnApplyWindowInsetsListener(binding.llMessageInputBar) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Add 8dp spacing between keyboard and input bar
            val spacingDp = 8
            val spacingPx = (spacingDp * resources.displayMetrics.density).toInt()
            
            // Apply bottom padding to push input bar above keyboard with spacing
            val bottomPadding = if (imeInsets.bottom > 0) {
                imeInsets.bottom + spacingPx
            } else {
                navBarInsets.bottom + originalPaddingBottom
            }
            
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomPadding
            )
            
            // Scroll to bottom when keyboard appears
            if (imeInsets.bottom > 0 && adapter.itemCount > 0) {
                binding.rvMessages.post {
                    binding.rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
            
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        val otherName = conversation.otherUser?.name
        val otherAvatar = conversation.otherUser?.avatar

        binding.tvChatName.text = otherName ?: "محادثة"
        binding.tvChatStatus.text = getString(R.string.kt_str_b30c86e0)

        if (!otherAvatar.isNullOrEmpty()) {
            Glide.with(this).load(otherAvatar).placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(binding.ivChatAvatar)
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }

        val otherId = conversation.otherUser?.id
        if (otherId != null && otherId != 0) {
            binding.btnChatModeration.visibility = View.VISIBLE
            binding.btnChatModeration.setOnClickListener { showModerationMenu(otherId) }
        } else {
            binding.btnChatModeration.visibility = View.GONE
        }
    }

    private fun showModerationMenu(otherUserId: Int) {
        val api = RetrofitClient.build(this)
        val otherName = conversation.otherUser?.name
        val popup = android.widget.PopupMenu(this, binding.btnChatModeration)
        popup.menu.add(0, 1, 0, "الإبلاغ عن المستخدم")
        if (ModerationState.isBlocked(otherUserId)) {
            popup.menu.add(0, 2, 1, "إلغاء حظر المستخدم")
        } else {
            popup.menu.add(0, 3, 1, "حظر المستخدم")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> ModerationDialogs.showReportDialog(
                    this, api, ReportTargetType.USER, otherUserId.toString(), otherName ?: "المستخدم")
                2 -> ModerationDialogs.unblock(this, api, otherUserId)
                3 -> ModerationDialogs.showBlockConfirm(
                    this, api, otherUserId, otherName, conversation.otherUser?.avatar)
            }
            true
        }
        popup.show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finishWithPop()
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter(this)
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.layoutManager = lm
        binding.rvMessages.adapter = adapter

        // Scrolling near the top of the loaded history pages in the previous
        // window (GET .../messages?before=...) instead of assuming the
        // first load already had everything.
        binding.rvMessages.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy >= 0) return
                if (lm.findFirstVisibleItemPosition() <= 2) {
                    viewModel.loadOlderMessages()
                }
            }
        })
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                binding.etMessage.setText("")
            }
        }
        
        // Handle keyboard send action
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val text = binding.etMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    viewModel.sendMessage(text)
                    binding.etMessage.setText("")
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupObservers() {
        viewModel.messages.observe(this) { result ->
            when (result) {
                is Result.Loading -> showShimmer()
                is Result.Success -> {
                    hideShimmer()
                    showContent()
                    // loadOlderMessages() prepends history (same last message,
                    // more items, different first message) — don't yank the
                    // user back to the bottom when that happens.
                    val oldLastId = adapter.currentList.lastOrNull()?.id
                    val isPrepend = oldLastId != null &&
                        result.data.size > adapter.itemCount &&
                        result.data.lastOrNull()?.id == oldLastId
                    adapter.submitList(result.data) {
                        if (!isPrepend) {
                            binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
                is Result.Error -> {
                    hideShimmer()
                    showError(result.message)
                }
            }
        }

        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is Result.Loading -> binding.btnSend.isEnabled = false
                is Result.Success -> binding.btnSend.isEnabled = true
                is Result.Error -> {
                    binding.btnSend.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showShimmer() {
        binding.shimmerChat.visibility = View.VISIBLE
        binding.shimmerChat.startShimmer()
        binding.rvMessages.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun hideShimmer() {
        binding.shimmerChat.stopShimmer()
        binding.shimmerChat.visibility = View.GONE
    }

    private fun showContent() {
        binding.rvMessages.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.rvMessages.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMsg.text = message
        binding.btnRetry.setOnClickListener { viewModel.loadMessages() }
    }
}
