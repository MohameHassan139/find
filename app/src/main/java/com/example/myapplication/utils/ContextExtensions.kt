package com.example.myapplication.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/** Short/long toast in one call instead of `Toast.makeText(...).show()` everywhere. */
fun Context.toast(message: CharSequence, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Context.toast(@StringRes message: Int, long: Boolean = false) {
    toast(getString(message), long)
}
