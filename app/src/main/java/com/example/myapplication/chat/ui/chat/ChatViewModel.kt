package com.example.myapplication.chat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.model.Message
import com.example.myapplication.chat.utils.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val chat = AppContainer.chat

    private val _messages = MutableLiveData<Result<List<Message>>>()
    val messages: LiveData<Result<List<Message>>> = _messages

    private val _sendResult = MutableLiveData<Result<Message>>()
    val sendResult: LiveData<Result<Message>> = _sendResult

    /** True while an older page exists beyond what's loaded (meta.has_more). */
    private val _hasMoreOlder = MutableLiveData(false)

    private var conversationId: String = ""
    private val messageList = mutableListOf<Message>()
    private var isLoadingOlder = false
    private var pollingStarted = false

    fun init(convId: String) {
        conversationId = convId
        loadMessages()
        markRead()
        startPolling()
    }

    /** Initial/replace load — newest `limit` messages, oldest-first. */
    fun loadMessages() {
        _messages.value = Result.Loading
        viewModelScope.launch {
            when (val result = chat.messages(conversationId, limit = INITIAL_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    messageList.clear()
                    messageList.addAll(result.data?.data.orEmpty())
                    _hasMoreOlder.value = result.data?.meta?.hasMore ?: false
                    _messages.value = Result.Success(messageList.toList())
                }
                is ApiResult.HttpError -> _messages.value = Result.Error(
                    when (result.code) {
                        401 -> "غير مصرح"
                        404 -> "المحادثة غير موجودة"
                        else -> "خطأ في تحميل الرسائل"
                    }, result.code
                )
                is ApiResult.NetworkError -> _messages.value = Result.Error("تعذر الاتصال بالخادم")
            }
        }
    }

    /** Loads the previous page of history — call when the user scrolls to the top. */
    fun loadOlderMessages() {
        val oldestId = messageList.firstOrNull()?.id ?: return
        if (isLoadingOlder || _hasMoreOlder.value != true) return
        isLoadingOlder = true
        viewModelScope.launch {
            // On failure stay silent — the user can just scroll up again to retry.
            chat.messages(conversationId, before = oldestId).getOrNull()?.let { body ->
                val knownIds = messageList.map { it.id }.toSet()
                val older = body.data.orEmpty().filter { it.id !in knownIds }
                if (older.isNotEmpty()) {
                    messageList.addAll(0, older)
                    _messages.value = Result.Success(messageList.toList())
                }
                _hasMoreOlder.value = body.meta?.hasMore ?: false
            }
            isLoadingOlder = false
        }
    }

    fun sendMessage(body: String) {
        if (body.isBlank()) return
        _sendResult.value = Result.Loading
        viewModelScope.launch {
            when (val result = chat.send(conversationId, body)) {
                is ApiResult.Success -> result.data?.let { msg ->
                    messageList.add(msg)
                    _messages.value = Result.Success(messageList.toList())
                    _sendResult.value = Result.Success(msg)
                }
                is ApiResult.HttpError -> _sendResult.value = Result.Error("فشل إرسال الرسالة")
                is ApiResult.NetworkError -> _sendResult.value = Result.Error("تعذر إرسال الرسالة. تحقق من الاتصال")
            }
        }
    }

    private fun markRead() {
        viewModelScope.launch {
            chat.markRead(conversationId)
        }
    }

    /**
     * Polls for new messages every 5s while this room is open. Uses `after`
     * so it only fetches what's actually new instead of re-fetching
     * everything (see docs/API_REFERENCE.md's recommended client flow).
     * viewModelScope is cancelled automatically when the Activity finishes,
     * so this loop stops itself — no explicit teardown needed.
     */
    private fun startPolling() {
        if (pollingStarted) return
        pollingStarted = true
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val newestId = messageList.lastOrNull()?.id ?: continue
                // Offline/transient failures are ignored — the next tick retries.
                val body = chat.messages(conversationId, after = newestId).getOrNull() ?: continue
                val knownIds = messageList.map { it.id }.toSet()
                val fresh = body.data.orEmpty().filter { it.id !in knownIds }
                if (fresh.isNotEmpty()) {
                    messageList.addAll(fresh)
                    _messages.value = Result.Success(messageList.toList())
                    markRead()
                }
            }
        }
    }

    suspend fun setFavorite(isFavorite: Boolean): Boolean =
        chat.setFavorite(conversationId, isFavorite).isSuccess

    suspend fun deleteConversation(): Boolean =
        chat.delete(conversationId).isSuccess

    private companion object {
        const val INITIAL_PAGE_SIZE = 50
    }
}
