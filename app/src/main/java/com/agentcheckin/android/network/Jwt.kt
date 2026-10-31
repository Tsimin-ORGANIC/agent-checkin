package com.agentcheckin.android.network

import android.util.Base64
import com.agentcheckin.android.util.JsonUtils
import org.json.JSONObject

object Jwt {
    fun payload(token: String): JSONObject? {
        return try {
            val part = token.split(".").getOrNull(1) ?: return null
            val bytes = Base64.decode(
                part,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun label(token: String): String? {
        val data = payload(token) ?: return null
        return JsonUtils.firstString(
            data,
            "name",
            "nickname",
            "preferred_username",
            "email",
            "sub"
        )
    }

    fun uid(token: String): String? {
        val data = payload(token) ?: return null
        return JsonUtils.firstString(data, "sub", "uid", "user_id", "userId")
    }
}
