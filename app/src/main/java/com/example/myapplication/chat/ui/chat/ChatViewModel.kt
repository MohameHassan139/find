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
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val api = RetrofitClient.build(app)

    private val _messages = MutableLiveData<Result<List<Message>>>()
    val messages: LiveData<Result<List<Message>>> = _messages

    private val _sendResult = MutableLiveData<Result<Message>>()
    val sendResult: LiveData<Result<Message>> = _sendResult

    private var conversationId: String = ""
    private val messageList = mutableListOf<Message>()

    fun init(convId: String) {
        conversationId = convId
        loadMessages()
        markRead()
    }

    fun loadMessages() {
        _messages.value = Result.Loading
        viewModelScope.launch {
            try {
                val response = api.getMessages(conversationId)
                if (response.isSuccessful) {
                    val data = response.body()?.data ?: emptyList()
                    messageList.clear()
                    messageList.addAll(data)
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
}
