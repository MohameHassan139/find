package com.example.myapplication.chat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.Message
import com.example.myapplication.chat.model.SendMessageRequest
import com.example.myapplication.chat.utils.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.build(app)

    private val _messages = MutableLiveData<Result<List<Message>>>()
    val messages: LiveData<Result<List<Message>>> = _messages

    private val _sendResult = MutableLiveData<Result<Message>>()
    val sendResult: LiveData<Result<Message>> = _sendResult

    /** True while an older page exists beyond what's loaded (meta.has_more). */
    private val _hasMoreOlder = MutableLiveData(false)
    val hasMoreOlder: LiveData<Boolean> = _hasMoreOlder

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
            try {
                val response = api.getMessages(conversationId, limit = 50)
                if (response.isSuccessful) {
                    val body = response.body()
                    messageList.clear()
                    messageList.addAll(body?.data ?: emptyList())
                    _hasMoreOlder.value = body?.meta?.hasMore ?: false
                    _messages.value = Result.Success(messageList.toList())
                } else {
                    _messages.value = Result.Error(
                        when (response.code()) {
                            401 -> "غير مصرح"
                            404 -> "المحادثة غير موجودة"
                            else -> "خطأ في تحميل الرسائل"
                        }, response.code()
                    )
                }
            } catch (e: Exception) {
                _messages.value = Result.Error("تعذر الاتصال بالخادم: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** Loads the previous page of history — call when the user scrolls to the top. */
    fun loadOlderMessages() {
        val oldestId = messageList.firstOrNull()?.id ?: return
        if (isLoadingOlder || _hasMoreOlder.value != true) return
        isLoadingOlder = true
        viewModelScope.launch {
            try {
                val response = api.getMessages(conversationId, before = oldestId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val knownIds = messageList.map { it.id }.toSet()
                    val older = (body?.data ?: emptyList()).filter { it.id !in knownIds }
                    if (older.isNotEmpty()) {
                        messageList.addAll(0, older)
                        _messages.value = Result.Success(messageList.toList())
                    }
                    _hasMoreOlder.value = body?.meta?.hasMore ?: false
                }
            } catch (_: Exception) {
                // Silent — the user can just scroll up again to retry.
            } finally {
                isLoadingOlder = false
            }
        }
    }

    fun sendMessage(body: String) {
        if (body.isBlank()) return
        _sendResult.value = Result.Loading
        viewModelScope.launch {
            try {
                val response = api.sendMessage(conversationId, SendMessageRequest(body))
                if (response.isSuccessful) {
                    val msg = response.body()?.data
                    if (msg != null) {
                        messageList.add(msg)
                        _messages.value = Result.Success(messageList.toList())
                        _sendResult.value = Result.Success(msg)
                    }
                } else {
                    _sendResult.value = Result.Error("فشل إرسال الرسالة")
                }
            } catch (e: Exception) {
                _sendResult.value = Result.Error("تعذر إرسال الرسالة. تحقق من الاتصال")
            }
        }
    }

    private fun markRead() {
        viewModelScope.launch {
            try { api.markRead(conversationId) } catch (_: Exception) {}
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
                try {
                    val response = api.getMessages(conversationId, after = newestId)
                    if (response.isSuccessful) {
                        val knownIds = messageList.map { it.id }.toSet()
                        val fresh = (response.body()?.data ?: emptyList())
                            .filter { it.id !in knownIds }
                        if (fresh.isNotEmpty()) {
                            messageList.addAll(fresh)
                            _messages.value = Result.Success(messageList.toList())
                            markRead()
                        }
                    }
                } catch (_: Exception) {
                    // Offline/transient — next tick retries.
                }
            }
        }
    }
}
