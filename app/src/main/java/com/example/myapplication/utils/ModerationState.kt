package com.example.myapplication.utils

import com.example.myapplication.chat.api.FindApiService
import com.example.myapplication.chat.model.BlockedUserDto

/**
 * Process-wide, in-memory cache of the caller's blocked-user ids — lets
 * ListingDetailActivity/ChatActivity show "Block" vs "Unblock" without a
 * network round-trip on every screen open. Not persisted; refreshed on
 * demand (see refresh()) and cleared on logout.
 */
object ModerationState {
    @Volatile private var blockedUsers: List<BlockedUserDto> = emptyList()
    @Volatile private var blockedIds: Set<Int> = emptySet()

    fun isBlocked(userId: Int?): Boolean = userId != null && blockedIds.contains(userId)

    fun cached(): List<BlockedUserDto> = blockedUsers

    suspend fun refresh(api: FindApiService) {
        try {
            val response = api.getBlocks()
            if (response.isSuccessful) {
                val list = response.body()?.data ?: emptyList()
                blockedUsers = list
                blockedIds = list.map { it.id }.toSet()
            }
        } catch (_: Exception) {
            // Keep whatever was cached before — next refresh() retries.
        }
    }

    fun markBlocked(user: BlockedUserDto) {
        blockedUsers = listOf(user) + blockedUsers.filterNot { it.id == user.id }
        blockedIds = blockedIds + user.id
    }

    fun markUnblocked(userId: Int) {
        blockedUsers = blockedUsers.filterNot { it.id == userId }
        blockedIds = blockedIds - userId
    }

    fun reset() {
        blockedUsers = emptyList()
        blockedIds = emptySet()
    }
}