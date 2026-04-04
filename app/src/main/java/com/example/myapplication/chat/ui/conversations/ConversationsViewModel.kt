package com.example.myapplication.chat.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.chat.model.Conversation
import com.example.myapplication.chat.utils.Result
import kotlinx.coroutines.launch

class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.build(app)

    private val _conversations = MutableLiveData<Result<List<Conversation>>>()
    val conversations: LiveData<Result<List<Conversation>>> = _conversations

    private var allConversations: List<Conversation> = emptyList()
    private var currentFilter = Filter.ALL

    enum class Filter { ALL, UNREAD, FAVORITE }

    init { loadConversations() }

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
            Filter.UNREAD -> allConversations.filter { it.unreadCount > 0 }
            Filter.FAVORITE -> allConversations.filter { it.id % 2 == 0 }
        }
        _conversations.value = Result.Success(filtered)
    }
}
