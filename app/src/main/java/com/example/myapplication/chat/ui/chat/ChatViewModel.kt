package com.example.myapplication.chat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
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

    private val _otherUserLastSeen = MutableLiveData<String?>()
    val otherUserLastSeen: LiveData<String?> = _otherUserLastSeen

    private var conversationId: String = ""
    private val messageList = mutableListOf<Message>()
    private var isLoadingOlder = false
    private var pollingStarted = false

    fun init(convId: String, initialLastSeen: String? = null) {
        conversationId = convId
        if (!initialLastSeen.isNullOrEmpty()) {
            _otherUserLastSeen.value = initialLastSeen
        }
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
                        401 -> getApplication<Application>().getString(R.string.error_unauthorized)
                        404 -> getApplication<Application>().getString(R.string.chat_not_found)
                        else -> getApplication<Application>().getString(R.string.chat_load_messages_failed)
                    }, result.code
                )
                is ApiResult.NetworkError -> _messages.value = Result.Error(getApplication<Application>().getString(R.string.error_network_check_connection))
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
                is ApiResult.HttpError -> _sendResult.value = Result.Error(getApplication<Application>().getString(R.string.chat_send_failed))
                is ApiResult.NetworkError -> _sendResult.value = Result.Error(getApplication<Application>().getString(R.string.chat_send_network_failed))
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
                // Also poll for online/last-seen status
                try {
                    val updates = chat.conversationUpdates().getOrNull()
                    val myUpdate = updates?.find { it.id == conversationId }
                    if (myUpdate?.otherUserLastSeenAt != null) {
                        _otherUserLastSeen.postValue(myUpdate.otherUserLastSeenAt)
                    }
                } catch (_: Exception) {}

                // Polling re-reads the newest page so the ticks on
                // my messages already on screen move sent → delivered → read (matching iOS ChatService).
                val body = chat.messages(conversationId, limit = INITIAL_PAGE_SIZE).getOrNull() ?: continue
                val responseMessages = body.data.orEmpty()
                if (responseMessages.isNotEmpty()) {
                    var hasChanges = false
                    val knownIds = messageList.map { it.id }.toSet()
                    val newMessages = responseMessages.filter { it.id !in knownIds }
                    if (newMessages.isNotEmpty()) {
                        messageList.addAll(newMessages)
                        hasChanges = true
                        markRead()
                    }
                    // Sync sent→delivered→read status flips on existing messages
                    for (updated in responseMessages) {
                        val idx = messageList.indexOfFirst { it.id == updated.id }
                        if (idx != -1 && messageList[idx].status != updated.status) {
                            messageList[idx] = updated
                            hasChanges = true
                        }
                    }
                    if (hasChanges) {
                        _messages.value = Result.Success(messageList.toList())
                    }
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
