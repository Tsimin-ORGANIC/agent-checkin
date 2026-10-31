package com.agentcheckin.android.util

import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {
    fun parseObject(body: String): JSONObject? {
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    fun unwrap(root: JSONObject?): JSONObject? {
        return root?.optJSONObject("data") ?: root
    }

    fun firstString(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            val value = obj.optString(key)
            if (value.isNotBlank() && value != "null") return value
        }
        return null
    }

    fun firstInt(obj: JSONObject?, vararg keys: String): Int? {
        if (obj == null) return null
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.optInt(key, Int.MIN_VALUE)
                if (value != Int.MIN_VALUE) return value
            }
        }
        return null
    }

    fun findArray(root: JSONObject?, key: String): JSONArray? {
        if (root == null) return null
        return root.optJSONArray(key) ?: root.optJSONObject("data")?.optJSONArray(key)
    }
}
