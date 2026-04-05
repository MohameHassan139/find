package com.example.myapplication.chat.ui.chat

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.Result
import com.example.myapplication.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION = "extra_conversation"
    }

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter
    private lateinit var conversation: Conversation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversation = intent.getParcelableExtra(EXTRA_CONVERSATION)!!

        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        setupObservers()

        viewModel.init(conversation.id)
    }

    private fun setupToolbar() {
        val myId = com.example.myapplication.auth.TokenManager.getUserId(this)
        val isBuyer = conversation.buyerId == myId
        val otherName = if (isBuyer) conversation.sellerName else conversation.buyerName
        val otherAvatar = if (isBuyer) conversation.sellerAvatar else conversation.buyerAvatar

        binding.tvChatName.text = otherName ?: "محادثة"
        binding.tvChatStatus.text = "آخر ظهور الساعة 1:00PM"

        if (!otherAvatar.isNullOrEmpty()) {
            Glide.with(this).load(otherAvatar).placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(binding.ivChatAvatar)
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter()
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
