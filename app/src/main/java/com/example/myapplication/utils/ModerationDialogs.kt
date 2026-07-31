package com.example.myapplication.utils

import android.app.Activity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.chat.api.FindApiService
import com.example.myapplication.chat.model.BlockUserRequest
import com.example.myapplication.chat.model.BlockedUserDto
import com.example.myapplication.chat.model.ReportReason
import com.example.myapplication.chat.model.ReportRequest
import com.example.myapplication.chat.model.ReportTargetType
import kotlinx.coroutines.launch

/**
 * Reusable Report/Block dialogs shared by ListingDetailActivity and
 * ChatActivity — App Store guideline 1.2 / Play Store content-moderation
 * requirement. Built as plain Views (no new layout XML) since the content
 * is a short radio list + optional details field.
 */
object ModerationDialogs {

    private fun reasonLabel(reason: ReportReason): String = when (reason) {
        ReportReason.SPAM -> "رسائل مزعجة"
        ReportReason.FRAUD -> "احتيال"
        ReportReason.INAPPROPRIATE -> "محتوى غير لائق"
        ReportReason.HARASSMENT -> "مضايقة"
        ReportReason.OTHER -> "أخرى"
    }

    fun showReportDialog(
        activity: Activity,
        api: FindApiService,
        type: ReportTargetType,
        targetId: String,
        targetLabel: String
    ) {
        val density = activity.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val reasons = ReportReason.values()
        val radioGroup = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        reasons.forEachIndexed { index, reason ->
            radioGroup.addView(RadioButton(activity).apply {
                id = index
                text = reasonLabel(reason)
            })
        }
        radioGroup.check(0)
        container.addView(radioGroup)

        val detailsInput = EditText(activity).apply {
            hint = "تفاصيل إضافية (اختياري)"
            maxLines = 4
            setPadding(0, pad, 0, 0)
        }
        container.addView(detailsInput)

        AlertDialog.Builder(activity)
            .setTitle("إبلاغ عن: $targetLabel")
            .setView(container)
            .setPositiveButton("إرسال") { _, _ ->
                val selected = radioGroup.checkedRadioButtonId
                if (selected !in reasons.indices) return@setPositiveButton
                val details = detailsInput.text.toString().trim().ifEmpty { null }
                submitReport(activity, api, type, targetId, reasons[selected], details)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun submitReport(
        activity: Activity,
        api: FindApiService,
        type: ReportTargetType,
        targetId: String,
        reason: ReportReason,
        details: String?
    ) {
        val scope = (activity as? AppCompatActivity)?.lifecycleScope ?: return
        scope.launch {
            try {
                val response = api.report(ReportRequest(type.apiValue, targetId, reason.apiValue, details))
                Toast.makeText(
                    activity,
                    if (response.isSuccessful) "تم إرسال البلاغ، شكراً لك" else "تعذر إرسال البلاغ",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(activity, "تعذر الاتصال بالخادم", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showBlockConfirm(
        activity: Activity,
        api: FindApiService,
        userId: Int,
        userName: String?,
        userAvatar: String? = null,
        onBlocked: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(activity)
            .setTitle("حظر ${userName ?: "المستخدم"}")
            .setMessage("لن تتمكنا من التواصل بعد الحظر. يمكنك إلغاء الحظر لاحقاً من الإعدادات.")
            .setPositiveButton("حظر") { _, _ ->
                val scope = (activity as? AppCompatActivity)?.lifecycleScope ?: return@setPositiveButton
                scope.launch {
                    try {
                        val response = api.blockUser(BlockUserRequest(userId))
                        if (response.isSuccessful) {
                            ModerationState.markBlocked(
                                BlockedUserDto(id = userId, name = userName, avatar = userAvatar, blockedAt = null)
                            )
                            onBlocked?.invoke()
                            Toast.makeText(activity, "تم الحظر", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "تعذر الحظر", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(activity, "تعذر الاتصال بالخادم", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    fun unblock(activity: Activity, api: FindApiService, userId: Int, onDone: (() -> Unit)? = null) {
        val scope = (activity as? AppCompatActivity)?.lifecycleScope ?: return
        scope.launch {
            try {
                api.unblockUser(userId)
                ModerationState.markUnblocked(userId)
                onDone?.invoke()
            } catch (_: Exception) {
                Toast.makeText(activity, "تعذر إلغاء الحظر", Toast.LENGTH_SHORT).show()
            }
        }
    }
}