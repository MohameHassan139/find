package com.example.myapplication.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PushEvents {
    private val _messageReceived = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val messageReceived: SharedFlow<String> = _messageReceived.asSharedFlow()

    fun notifyMessageReceived(conversationId: String) {
        _messageReceived.tryEmit(conversationId)
    }
}
