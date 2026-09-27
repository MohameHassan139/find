package com.example.myapplication.data

import org.json.JSONObject
import retrofit2.Response

/**
 * The server's (already localized) `message` from an error response, or null when the
 * body is missing, not JSON, or has no message. Every API error uses this envelope:
 * `{ "success": false, "message": "...", "errors": ... }`.
 */
fun Response<*>.serverMessage(): String? {
    val body = errorBody()?.string() ?: return null
    return runCatching { JSONObject(body).stringOrNull("message") }.getOrNull()?.takeIf { it.isNotBlank() }
}
