package com.example.myapplication.chat.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.build(app)

    private val _conversations = MutableLiveData<Result<List<Conversation>>>()
    val conversations: LiveData<Result<List<Conversation>>> = _conversations

    private var allConversations: List<Conversation> = emptyList()
    private var currentFilter = Filter.ALL
    private var pollingStarted = false

    enum class Filter { ALL, UNREAD, FAVORITE }

    init {
        loadConversations()
        startPolling()
    }

    fun loadConversations() {
        _conversations.value = Result.Loading
        viewModelScope.launch {
            try {
                val response = api.getConversations()
                if (response.isSuccessful) {
                    allConversations = response.body()?.data ?: emptyList()
                    applyFilter(currentFilter)
                } else {
                    _conversations.value = Result.Error(
                        when (response.code()) {
                            401 -> "غير مصرح. يرجى تسجيل الدخول مجدداً"
                            404 -> "لم يتم العثور على المحادثات"
                            500 -> "خطأ في الخادم. حاول مرة أخرى"
                            else -> "حدث خطأ: ${response.code()}"
                        }, response.code()
                    )
                }
            } catch (e: Exception) {
                _conversations.value = Result.Error("تعذر الاتصال بالخادم. تحقق من اتصالك بالإنترنت")
            }
        }
    }

    fun setFilter(filter: Filter) {
        currentFilter = filter
        applyFilter(filter)
    }

    private fun applyFilter(filter: Filter) {
        val filtered = when (filter) {
            Filter.ALL -> allConversations
            Filter.UNREAD -> allConversations.filter { it.myUnread > 0 }
            Filter.FAVORITE -> allConversations // no favorites concept in API yet
        }
        _conversations.value = Result.Success(filtered)
    }

    /**
     * Polls GET /conversations/updates every 30s so unread badges/previews
     * stay fresh without a full re-fetch. Falls back to a full
     * loadConversations() only when a conversation id we don't know about
     * yet shows up (a brand-new conversation). viewModelScope is cancelled
     * automatically when this ViewModel is cleared, so the loop stops itself.
     */
    private fun startPolling() {
        if (pollingStarted) return
        pollingStarted = true
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                try {
                    val response = api.getConversationUpdates()
                    if (!response.isSuccessful) continue
                    val updates = response.body()?.data ?: continue
                    val knownIds = allConversations.map { it.id }.toSet()
                    val hasNew = updates.any { it.id !in knownIds }
                    if (hasNew) {
                        loadConversations()
                        continue
                    }
                    val byId = updates.associateBy { it.id }
                    allConversations = allConversations.map { conv ->
                        val update = byId[conv.id] ?: return@map conv
                        conv.copy(
                            lastMessageAt = update.lastMessageAt ?: conv.lastMessageAt,
                            myUnread = update.myUnread ?: conv.myUnread
                        )
                    }
                    applyFilter(currentFilter)
                } catch (_: Exception) {
                    // Offline/transient — next tick retries.
                }
            }
        }
    }
}
