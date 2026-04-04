package com.find.chat.ui.conversations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.find.chat.api.RetrofitClient
import com.find.chat.model.Conversation
import com.find.chat.utils.Result
import kotlinx.coroutines.launch

class ConversationsViewModel : ViewModel() {

    private val _conversations = MutableLiveData<Result<List<Conversation>>>()
    val conversations: LiveData<Result<List<Conversation>>> = _conversations

    private var allConversations: List<Conversation> = emptyList()
    private var currentFilter = Filter.ALL

    enum class Filter { ALL, UNREAD, FAVORITE }

    init {
        loadConversations()
    }

    fun loadConversations() {
        _conversations.value = Result.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getConversations()
                if (response.isSuccessful) {
                    val data = response.body()?.data ?: emptyList()
                    allConversations = data
                    applyFilter(currentFilter)
                } else {
                    val errMsg = when (response.code()) {
                        401 -> "غير مصرح. يرجى تسجيل الدخول مجدداً"
                        404 -> "لم يتم العثور على المحادثات"
                        500 -> "خطأ في الخادم. حاول مرة أخرى"
                        else -> "حدث خطأ: ${response.code()}"
                    }
                    _conversations.value = Result.Error(errMsg, response.code())
                }
            } catch (e: Exception) {
                _conversations.value = Result.Error(
                    "تعذر الاتصال بالخادم. تحقق من اتصالك بالإنترنت"
                )
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
            Filter.FAVORITE -> allConversations.filter { it.id % 2 == 0 } // placeholder logic
        }
        _conversations.value = Result.Success(filtered)
    }
}
