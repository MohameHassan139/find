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
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.Result
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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
        val myId = com.example.myapplication.auth.TokenManager.getUserId(this)
        val isBuyer = conversation.buyerId == myId
        val otherName = if (isBuyer) conversation.sellerName else conversation.buyerName
        val otherAvatar = if (isBuyer) conversation.sellerAvatar else conversation.buyerAvatar

        binding.tvChatName.text = otherName ?: "محادثة"
        binding.tvChatStatus.text = getString(R.string.kt_str_b30c86e0)

        if (!otherAvatar.isNullOrEmpty()) {
            Glide.with(this).load(otherAvatar).placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(binding.ivChatAvatar)
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(android.content.Intent(this, MenuActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter(this)
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.layoutManager = lm
        binding.rvMessages.adapter = adapter
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
                    adapter.submitList(result.data) {
                        binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
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
