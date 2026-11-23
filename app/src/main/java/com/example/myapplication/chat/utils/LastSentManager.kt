package com.example.myapplication.chat.utils

import android.content.Context

object LastSentManager {
    private const val PREFS = "last_sent_messages"

    fun recordSent(context: Context, conversationId: String, text: String, isRead: Boolean = false) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("${conversationId}_text", text.trim())
            .putBoolean("${conversationId}_is_mine", true)
            .putBoolean("${conversationId}_is_read", isRead)
            // Keep legacy key for compatibility
            .putString(conversationId, text.trim())
            .apply()
    }

    fun recordReceived(context: Context, conversationId: String, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("${conversationId}_text", text.trim())
            .putBoolean("${conversationId}_is_mine", false)
            .putBoolean("${conversationId}_is_read", false)
            .remove(conversationId)
            .apply()
    }

    fun recordReadStatus(context: Context, conversationId: String, isRead: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("${conversationId}_is_read", isRead)
            .apply()
    }

    fun isLastMessageMine(context: Context, conversationId: String, lastMessageText: String?): Boolean {
        if (lastMessageText.isNullOrBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trimmed = lastMessageText.trim()
        
        // If explicitly recorded as not mine
        if (prefs.contains("${conversationId}_is_mine")) {
            val isMine = prefs.getBoolean("${conversationId}_is_mine", false)
            if (!isMine) return false
            val savedText = prefs.getString("${conversationId}_text", null) ?: return false
            return savedText == trimmed
        }
        
        // Legacy fallback
        val legacySaved = prefs.getString(conversationId, null)
        return legacySaved == trimmed
    }

    fun isLastMessageRead(context: Context, conversationId: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("${conversationId}_is_read", false)
    }
}
