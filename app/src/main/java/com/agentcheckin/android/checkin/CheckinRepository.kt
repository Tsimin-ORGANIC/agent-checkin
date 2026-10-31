package com.agentcheckin.android.checkin

import android.content.Context
import com.agentcheckin.android.data.AccountInfo
import com.agentcheckin.android.data.AppSettings
import com.agentcheckin.android.data.AuthStart
import com.agentcheckin.android.data.CheckinResult
import com.agentcheckin.android.data.LogEntry
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.data.PollResult
import com.agentcheckin.android.data.SecureStore
import com.agentcheckin.android.network.ApiException
import com.agentcheckin.android.network.QoderApi
import com.agentcheckin.android.network.WorkBuddyApi
import com.agentcheckin.android.util.Redact
import com.agentcheckin.android.util.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CheckinRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)
    private val store = SecureStore(appContext)
    private val workBuddy = WorkBuddyApi(store)
    private val qoder = QoderApi(appContext, store, settings)

    suspend fun checkin(platform: Platform): CheckinResult = CHECKIN_MUTEX.withLock {
        val result = try {
            when (platform) {
                Platform.WORKBUDDY -> workBuddy.checkin()
                Platform.QODER -> qoder.checkin()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CheckinResult(
                platform = platform,
                success = false,
                message = friendlyError(error)
            )
        }
        record(platform, result)
        result
    }

    fun isLoggedIn(platform: Platform): Boolean = store.isLoggedIn(platform)

    fun accountLabel(platform: Platform): String? = store.labelFor(platform)

    fun usingEncryptedStorage(): Boolean = store.usingEncryptedStorage

    suspend fun beginWorkBuddyLogin(): AuthStart = workBuddy.beginLogin()

    suspend fun pollWorkBuddyLogin(state: String): PollResult {
        return workBuddy.pollLogin(state)
    }

    suspend fun createQoderPatFromCookie(): Result<String> {
        return qoder.createPatFromCookie()
    }

    suspend fun validateQoderPat(pat: String): AccountInfo {
        return qoder.validateAndSavePat(pat)
    }

    fun parseQoderPatFromJs(status: Int, body: String): String? {
        return qoder.parsePatFromJs(status, body)
    }

    fun logout(platform: Platform) {
        if (platform == Platform.WORKBUDDY) {
            store.clearWorkBuddy()
        } else {
            store.clearQoder()
        }
    }

    private fun record(platform: Platform, result: CheckinResult) {
        val now = System.currentTimeMillis()
        settings.setLastRunAt(platform, now)
        settings.setLastMessage(platform, result.message)
        if (result.success) {
            val key = if (platform == Platform.QODER) {
                TimeUtils.qoderWindowKey()
            } else {
                TimeUtils.todayKey()
            }
            settings.setLastSuccessDay(platform, key)
        }
        settings.addLog(
            LogEntry(
                time = now,
                platform = platform,
                success = result.success,
                message = result.message
            )
        )
        if (result.needsLogin) {
            if (platform == Platform.WORKBUDDY) {
                store.clearWorkBuddySession()
            } else {
                store.clearQoderSession()
            }
        }
    }

    private fun friendlyError(error: Exception): String {
        val message = when (error) {
            is UnknownHostException -> "网络不可用，请检查网络连接"
            is SocketTimeoutException -> "请求超时，请稍后重试"
            is ApiException -> error.message ?: "接口请求失败"
            is IOException -> "网络错误：${error.message ?: error.javaClass.simpleName}"
            else -> error.message ?: error.javaClass.simpleName
        }
        return Redact.text(message)
    }

    companion object {
        private val CHECKIN_MUTEX = Mutex()
    }
}
