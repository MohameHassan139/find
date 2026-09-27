package com.example.myapplication.chat.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val chat = AppContainer.chat

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
            when (val result = chat.conversations()) {
                is ApiResult.Success -> {
                    allConversations = result.data
                    applyFilter(currentFilter)
                }
                is ApiResult.HttpError -> _conversations.value = Result.Error(
                    when (result.code) {
                        401 -> getApplication<Application>().getString(R.string.error_unauthorized_relogin)
                        404 -> getApplication<Application>().getString(R.string.chat_conversations_not_found)
                        500 -> getApplication<Application>().getString(R.string.error_server_try_again)
                        else -> getApplication<Application>().getString(R.string.error_failed_to_load, result.code)
                    }, result.code
                )
                is ApiResult.NetworkError ->
                    _conversations.value = Result.Error(getApplication<Application>().getString(R.string.error_network_check_connection))
            }
        }
    }

    fun setFilter(filter: Filter) {
        currentFilter = filter
        applyFilter(filter)
    }

    fun getCurrentFilter(): Filter = currentFilter

    fun toggleFavorite(conversationId: String) {
        val original = allConversations.find { it.id == conversationId } ?: return
        val nextFav = !original.isFavorite

        // Optimistic update
        allConversations = allConversations.map {
            if (it.id == conversationId) it.copy(isFavorite = nextFav) else it
        }
        applyFilter(currentFilter)

        viewModelScope.launch {
            val result = chat.setFavorite(conversationId, nextFav)
            // Take the server's copy on success, otherwise revert the optimistic change.
            val replacement = if (result is ApiResult.Success) result.data else original
            if (replacement != null) {
                allConversations = allConversations.map { if (it.id == conversationId) replacement else it }
                applyFilter(currentFilter)
            }
        }
    }

    fun deleteConversation(conversationId: String, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val deleted = chat.delete(conversationId).isSuccess
            if (deleted) {
                allConversations = allConversations.filter { it.id != conversationId }
                applyFilter(currentFilter)
            }
            onResult?.invoke(deleted)
        }
    }

    private fun applyFilter(filter: Filter) {
        val filtered = when (filter) {
            Filter.ALL -> allConversations
            Filter.UNREAD -> allConversations.filter { it.myUnread > 0 }
            Filter.FAVORITE -> allConversations.filter { it.isFavorite }
        }
        _conversations.value = Result.Success(filtered)
    }

    /**
     * Polls GET /conversations/updates every 30s so unread badges/previews
     * stay fresh without a full re-fetch. Drops conversations missing from
     * updates (deleted on another device) and syncs is_favorite.
     * Falls back to a full loadConversations() when a brand-new conversation shows up.
     */
    private fun startPolling() {
        if (pollingStarted) return
        pollingStarted = true
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                // Offline/transient failures are ignored — the next tick retries.
                val updates = chat.conversationUpdates().getOrNull() ?: continue
                val liveIds = updates.map { it.id }.toSet()
                val knownIds = allConversations.map { it.id }.toSet()
                val hasNew = updates.any { it.id !in knownIds }
                if (hasNew) {
                    loadConversations()
                    continue
                }
                val byId = updates.associateBy { it.id }
                allConversations = allConversations
                    .filter { liveIds.contains(it.id) }
                    .map { conv ->
                        val update = byId[conv.id] ?: return@map conv
                        conv.copy(
                            lastMessageAt = update.lastMessageAt ?: conv.lastMessageAt,
                            myUnread = update.myUnread ?: conv.myUnread,
                            isFavorite = update.isFavorite ?: conv.isFavorite
                        )
                    }
                applyFilter(currentFilter)
            }
        }
    }
}
