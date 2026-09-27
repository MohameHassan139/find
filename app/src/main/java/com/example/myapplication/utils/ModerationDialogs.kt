package com.example.myapplication.utils

import android.app.Activity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.chat.model.BlockedUserDto
import com.example.myapplication.chat.model.ReportReason
import com.example.myapplication.chat.model.ReportTargetType
import kotlinx.coroutines.launch
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

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
                submitReport(activity, type, targetId, reasons[selected], details)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun submitReport(
        activity: Activity,
        type: ReportTargetType,
        targetId: String,
        reason: ReportReason,
        details: String?
    ) {
        val scope = (activity as? AppCompatActivity)?.lifecycleScope ?: return
        scope.launch {
            when (AppContainer.moderation.report(type, targetId, reason, details)) {
                is ApiResult.Success -> activity.toast("تم إرسال البلاغ، شكراً لك")
                is ApiResult.HttpError -> activity.toast("تعذر إرسال البلاغ")
                is ApiResult.NetworkError -> activity.toast("تعذر الاتصال بالخادم")
            }
        }
    }

    fun showBlockConfirm(
        activity: Activity,
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
                    when (AppContainer.moderation.block(userId)) {
                        is ApiResult.Success -> {
                            ModerationState.markBlocked(
                                BlockedUserDto(id = userId, name = userName, avatar = userAvatar, blockedAt = null)
                            )
                            onBlocked?.invoke()
                            activity.toast("تم الحظر")
                        }
                        is ApiResult.HttpError -> activity.toast("تعذر الحظر")
                        is ApiResult.NetworkError -> activity.toast("تعذر الاتصال بالخادم")
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    fun unblock(activity: Activity, userId: Int, onDone: (() -> Unit)? = null) {
        val scope = (activity as? AppCompatActivity)?.lifecycleScope ?: return
        scope.launch {
            // As before: any server answer counts as done; only a network failure is reported.
            if (AppContainer.moderation.unblock(userId) is ApiResult.NetworkError) {
                activity.toast("تعذر إلغاء الحظر")
                return@launch
            }
            ModerationState.markUnblocked(userId)
            onDone?.invoke()
        }
    }
}