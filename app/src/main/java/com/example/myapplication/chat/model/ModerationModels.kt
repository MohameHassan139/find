package com.example.myapplication.chat.model

import com.google.gson.annotations.SerializedName

// ─── Blocks ─────────────────────────────────────────────────────────────────

data class BlockedUserDto(
    val id: Int,
    val name: String?,
    val avatar: String?,
    @SerializedName("blocked_at") val blockedAt: String?
)

data class BlocksResponse(val data: List<BlockedUserDto>?)

data class BlockUserRequest(@SerializedName("user_id") val userId: Int)

data class BlockResponse(val data: BlockedUserDto?, val message: String?)

// ─── Reports ────────────────────────────────────────────────────────────────

enum class ReportTargetType(val apiValue: String) {
    LISTING("listing"), MESSAGE("message"), USER("user")
}

enum class ReportReason(val apiValue: String) {
    SPAM("spam"), FRAUD("fraud"), INAPPROPRIATE("inappropriate"),
    HARASSMENT("harassment"), OTHER("other")
}

data class ReportRequest(
    val type: String,
    val id: String,
    val reason: String,
    val details: String?
)