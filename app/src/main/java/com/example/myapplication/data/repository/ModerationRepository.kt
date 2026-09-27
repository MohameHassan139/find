package com.example.myapplication.data.repository

import com.example.myapplication.chat.model.BlockUserRequest
import com.example.myapplication.chat.model.BlockedUserDto
import com.example.myapplication.chat.model.ReportReason
import com.example.myapplication.chat.model.ReportRequest
import com.example.myapplication.chat.model.ReportTargetType
import com.example.myapplication.data.ApiResult
import com.example.myapplication.data.apiCall
import com.example.myapplication.data.remote.FindApiService

/** Blocking users and reporting users/ads. */
class ModerationRepository(private val api: FindApiService) {

    suspend fun blockedUsers(): ApiResult<List<BlockedUserDto>> =
        apiCall({ api.getBlocks() }) { it.body()?.data.orEmpty() }

    suspend fun block(userId: Int): ApiResult<Unit> = apiCall { api.blockUser(BlockUserRequest(userId)) }

    suspend fun unblock(userId: Int): ApiResult<Unit> = apiCall { api.unblockUser(userId) }

    suspend fun report(type: ReportTargetType, targetId: String, reason: ReportReason, details: String?): ApiResult<Unit> =
        apiCall { api.report(ReportRequest(type.apiValue, targetId, reason.apiValue, details)) }
}
