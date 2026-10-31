package com.agentcheckin.android.network

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HttpResult(
    val code: Int,
    val body: String,
    val headers: Headers
)

object Http {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cookies: String? = null
    ): HttpResult {
        val builder = Request.Builder().url(url).get()
        applyHeaders(builder, headers)
        if (!cookies.isNullOrBlank()) {
            builder.header("Cookie", cookies)
        }
        return execute(builder.build())
    }

    fun post(
        url: String,
        body: String = "{}",
        headers: Map<String, String> = emptyMap(),
        cookies: String? = null
    ): HttpResult {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
        applyHeaders(builder, headers)
        if (!cookies.isNullOrBlank()) {
            builder.header("Cookie", cookies)
        }
        return execute(builder.build())
    }

    private fun applyHeaders(builder: Request.Builder, headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                builder.header(name, value)
            }
        }
    }

    private fun execute(request: Request): HttpResult {
        return client.newCall(request).execute().use { response ->
            HttpResult(
                code = response.code,
                body = response.body?.string().orEmpty(),
                headers = response.headers
            )
        }
    }
}
