package org.libre.search.core

import org.json.JSONArray
import org.json.JSONObject

/** Small helpers so parsing never crashes on missing fields. */
fun JSONObject?.str(key: String): String? {
    if (this == null || !has(key) || isNull(key)) return null
    val v = optString(key, "")
    return v.ifBlank { null }
}

fun JSONObject?.obj(key: String): JSONObject? = this?.optJSONObject(key)
fun JSONObject?.arr(key: String): JSONArray? = this?.optJSONArray(key)
fun JSONObject?.dbl(key: String): Double? =
    if (this == null || !has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

fun JSONObject?.int(key: String): Int? =
    if (this == null || !has(key) || isNull(key)) null else optInt(key)

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out += it }
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val s = optString(i, "")
        if (s.isNotBlank()) out += s
    }
    return out
}

/** Removes HTML tags and decodes the common entities (Brave returns <strong> highlights). */
fun String.stripHtml(): String =
    this.replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ").replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: ""
        }
        .replace(Regex("\\s+"), " ").trim()
