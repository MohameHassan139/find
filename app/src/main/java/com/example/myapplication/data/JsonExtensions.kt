package com.example.myapplication.data

import org.json.JSONObject

/*
 * Null-safe readers for the API's JSON. `optString` returns "" for a missing key and
 * the literal "null" for a JSON null, so every screen used to repeat the same
 * `has(...) && !isNull(...)` / `ifEmpty { null }` dance — it now lives here.
 */

/** The value as a non-empty string, or null when missing, JSON null or empty. */
fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifEmpty { null }

fun JSONObject.intOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

fun JSONObject.doubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

/** A JSON array of strings (e.g. image URLs); empty when missing. */
fun JSONObject.stringList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifEmpty { null } }
}

/** `city` is sent either as a plain string or as `{ name_ar, name }`. */
fun JSONObject.cityName(): String? {
    if (isNull("city")) return null
    val obj = optJSONObject("city") ?: return stringOrNull("city")
    return obj.stringOrNull("name_ar") ?: obj.stringOrNull("name")
}
