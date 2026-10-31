package com.agentcheckin.android.network

import com.agentcheckin.android.data.AccountInfo
import com.agentcheckin.android.data.AuthStart
import com.agentcheckin.android.data.CheckinResult
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.data.PollResult
import com.agentcheckin.android.data.SecureStore
import com.agentcheckin.android.util.JsonUtils
import com.agentcheckin.android.util.Redact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

class WorkBuddyApi(private val store: SecureStore) {

    suspend fun beginLogin(): AuthStart = withContext(Dispatchers.IO) {
        val response = Http.post(
            "$AUTH_BASE/v2/plugin/auth/state?platform=CLI",
            "{}",
            mapOf(
                "Accept" to "application/json",
                "X-Product" to "SaaS",
                "User-Agent" to USER_AGENT
            )
        )
        if (response.code !in 200..299) {
            throw ApiException("无法发起 WorkBuddy 登录（HTTP ${response.code}）")
        }
        val root = JsonUtils.parseObject(response.body)
            ?: throw ApiException("WorkBuddy 登录接口返回异常")
        val code = root.optInt("code", -1)
        if (code != 0) {
            throw ApiException(root.optString("msg").ifBlank { "登录接口错误 code=$code" })
        }
        val data = root.optJSONObject("data") ?: root
        val state = JsonUtils.firstString(data, "state", "authState")
            ?: throw ApiException("登录接口未返回 state")
        val rawUrl = JsonUtils.firstString(data, "authUrl", "url")
            ?: throw ApiException("登录接口未返回登录地址")
        val authUrl = if (rawUrl.startsWith("http")) rawUrl else AUTH_BASE + rawUrl
        AuthStart(state, authUrl)
    }

    suspend fun pollLogin(state: String): PollResult = withContext(Dispatchers.IO) {
        val encodedState = URLEncoder.encode(state, "UTF-8")
        val response = Http.get(
            "$AUTH_BASE/v2/plugin/auth/token?state=$encodedState",
            mapOf(
                "Accept" to "application/json",
                "X-Product" to "SaaS",
                "User-Agent" to USER_AGENT
            )
        )
        val root = JsonUtils.parseObject(response.body)
        val code = root?.optInt("code", -1) ?: -1
        if (code == CODE_PENDING) {
            return@withContext PollResult.Pending
        }
        if (response.code !in 200..299 || root == null) {
            return@withContext PollResult.Failure("登录服务返回异常（HTTP ${response.code}）")
        }
        val data = root.optJSONObject("data")
        val accessToken = JsonUtils.firstString(data, "accessToken", "token")
            ?: JsonUtils.firstString(root, "accessToken", "token")
        if (code == 0 && !accessToken.isNullOrBlank()) {
            val refreshToken = JsonUtils.firstString(data, "refreshToken", "refresh_token")
            val account = extractAccount(data, accessToken)
            saveLogin(accessToken, refreshToken, account)
            return@withContext PollResult.Success(accessToken, refreshToken, account)
        }
        if (code == 0) {
            return@withContext PollResult.Pending
        }
        PollResult.Failure(
            root.optString("msg").ifBlank { "登录失败（code=$code）" }
        )
    }

    suspend fun checkin(): CheckinResult = withContext(Dispatchers.IO) {
        var token: String = store.wbAccessToken
            ?: return@withContext needsLogin()
        if (token.isBlank()) {
            return@withContext needsLogin()
        }
        var uid = store.wbUid

        val bases = buildList {
            store.wbApiBase?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(API_BASES)
        }.distinct()

        var chosenBase: String? = null
        var statusResponse: HttpResult? = null
        var lastProblem = ""

        for (base in bases) {
            var response = requestStatus(base, token, uid)
            if (response.code == 403 && !uid.isNullOrBlank()) {
                val withoutUid = requestStatus(base, token, null)
                if (withoutUid.code in 200..299) response = withoutUid
            }
            if (response.code == 401 || response.code == 403) {
                if (!refreshToken()) return@withContext needsLogin()
                token = store.wbAccessToken ?: token
                uid = store.wbUid
                response = requestStatus(base, token, uid)
            }
            if (response.code in 200..299 ||
                (response.code in 400..499 && response.code != 404 && response.code != 405)
            ) {
                chosenBase = base
                statusResponse = response
                break
            }
            lastProblem = "HTTP ${response.code}"
        }

        val base = chosenBase
        val status = statusResponse
        if (base == null || status == null) {
            return@withContext failure(
                "WorkBuddy 接口暂不可用" + if (lastProblem.isNotBlank()) "（$lastProblem）" else ""
            )
        }
        store.wbApiBase = base

        val statusRoot = JsonUtils.parseObject(status.body)
            ?: return@withContext failure("WorkBuddy 状态接口返回格式异常")
        val statusCode = statusRoot.optInt("code", -1)
        if (status.code == 401 || status.code == 403 ||
            statusCode == 401 || statusCode == 403
        ) {
            return@withContext needsLogin()
        }
        if (statusCode != 0) {
            val message = statusRoot.optString("msg")
            if (message.contains("未开启") || message.contains("结束")) {
                return@withContext failure("WorkBuddy 签到活动未开启或已结束")
            }
            return@withContext failure(
                "WorkBuddy 状态查询失败：${message.ifBlank { "code=$statusCode" }}"
            )
        }

        val statusData = statusRoot.optJSONObject("data")
        val active = statusData?.optBoolean("active", true) ?: true
        val checked = statusData?.optBoolean("today_checked_in", false)
            ?: statusData?.optBoolean("checkedIn", false)
            ?: false
        val streak = JsonUtils.firstInt(
            statusData,
            "streak_days",
            "streakDays",
            "current_consecutive_days"
        ) ?: 0
        val dailyCredit = JsonUtils.firstInt(
            statusData,
            "daily_credit",
            "dailyCredit",
            "credit"
        ) ?: 100
        val totalCredits = JsonUtils.firstInt(
            statusData,
            "total_credits",
            "totalCredits"
        ) ?: 0

        if (checked) {
            return@withContext CheckinResult(
                platform = Platform.WORKBUDDY,
                success = true,
                alreadyDone = true,
                message = "今日已签到" + streakText(streak) + totalText(totalCredits),
                streak = streak
            )
        }
        if (!active) {
            return@withContext failure("WorkBuddy 签到活动未开启或已结束")
        }

        var claimResponse = requestClaim(base, token, uid)
        if (claimResponse.code == 401 || claimResponse.code == 403) {
            if (!refreshToken()) return@withContext needsLogin()
            token = store.wbAccessToken ?: token
            uid = store.wbUid
            claimResponse = requestClaim(base, token, uid)
        }

        val claimRoot = JsonUtils.parseObject(claimResponse.body)
        val claimCode = claimRoot?.optInt("code", -1) ?: -1
        val claimMessage = claimRoot?.optString("msg").orEmpty()
        val claimData = claimRoot?.optJSONObject("data")
        val claimOk = claimResponse.code in 200..299 &&
            (claimCode == 0 || claimCode == 200 || (claimCode == -1 && claimData != null))

        if (claimOk) {
            val reward = JsonUtils.firstInt(claimData, "credit", "daily_credit", "dailyCredit")
                ?: dailyCredit
            val newStreak = JsonUtils.firstInt(claimData, "streak_days", "streakDays")
                ?: streak
            verifyClaim(base, token, uid)
            return@withContext CheckinResult(
                platform = Platform.WORKBUDDY,
                success = true,
                message = "签到成功，+$reward 积分" + streakText(newStreak),
                reward = reward,
                streak = newStreak
            )
        }

        if (claimCode == 10001 ||
            claimMessage.contains("已签到") ||
            claimMessage.contains("已签")
        ) {
            return@withContext CheckinResult(
                platform = Platform.WORKBUDDY,
                success = true,
                alreadyDone = true,
                message = "今日已签到" + streakText(streak),
                streak = streak
            )
        }
        if (claimMessage.contains("未开启") ||
            claimMessage.contains("结束") ||
            claimMessage.contains("过期")
        ) {
            return@withContext failure("WorkBuddy 签到活动未开启或已结束")
        }
        failure(
            "WorkBuddy 签到失败：${claimMessage.ifBlank { "HTTP ${claimResponse.code} / code=$claimCode" }}"
        )
    }

    private fun requestStatus(base: String, token: String, uid: String?): HttpResult {
        return Http.post(
            "$base$STATUS_PATH",
            "{}",
            baseHeaders(base, token, uid)
        )
    }

    private fun requestClaim(base: String, token: String, uid: String?): HttpResult {
        return Http.post(
            "$base$CLAIM_PATH",
            "{}",
            baseHeaders(base, token, uid)
        )
    }

    private fun verifyClaim(base: String, token: String, uid: String?) {
        runCatching {
            val response = requestStatus(base, token, uid)
            if (response.code in 200..299) {
                JsonUtils.parseObject(response.body)
            }
        }
    }

    private fun baseHeaders(
        base: String,
        token: String,
        uid: String?
    ): MutableMap<String, String> {
        val host = runCatching { URI(base).host }.getOrNull() ?: base
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "Authorization" to "Bearer $token",
            "X-Domain" to host,
            "X-Product" to "SaaS",
            "User-Agent" to USER_AGENT
        )
        if (!uid.isNullOrBlank()) {
            headers["X-User-Id"] = uid
        }
        return headers
    }

    private fun refreshToken(): Boolean {
        val refresh = store.wbRefreshToken
        if (refresh.isNullOrBlank()) return false
        val currentAccess = store.wbAccessToken
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "X-Product" to "SaaS",
            "X-Refresh-Token" to refresh,
            "User-Agent" to USER_AGENT
        )
        if (!currentAccess.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $currentAccess"
        }
        val response = runCatching {
            Http.post(
                "$AUTH_BASE/v2/plugin/auth/token/refresh",
                "{}",
                headers
            )
        }.getOrNull() ?: return false
        if (response.code !in 200..299) return false
        val root = JsonUtils.parseObject(response.body) ?: return false
        if (root.optInt("code", -1) != 0) return false
        val data = root.optJSONObject("data") ?: root
        val newAccess = JsonUtils.firstString(data, "accessToken", "token") ?: return false
        val newRefresh = JsonUtils.firstString(data, "refreshToken", "refresh_token")
            ?: refresh
        val account = extractAccount(data, newAccess)
        saveLogin(newAccess, newRefresh, account)
        return true
    }

    private fun extractAccount(data: JSONObject?, token: String): AccountInfo {
        if (data == null) {
            return AccountInfo(Jwt.label(token), Jwt.uid(token))
        }
        val user = data.optJSONObject("user")
            ?: data.optJSONObject("account")
            ?: data
        val label = JsonUtils.firstString(
            user,
            "nickname",
            "name",
            "userName",
            "displayName"
        ) ?: JsonUtils.firstString(data, "nickname", "name")
            ?: Jwt.label(token)
        val uid = JsonUtils.firstString(
            user,
            "uid",
            "userId",
            "user_id",
            "id"
        ) ?: JsonUtils.firstString(data, "uid", "userId", "user_id")
            ?: Jwt.uid(token)
        return AccountInfo(label, uid)
    }

    private fun saveLogin(
        accessToken: String,
        refreshToken: String?,
        account: AccountInfo
    ) {
        store.wbAccessToken = accessToken
        if (!refreshToken.isNullOrBlank()) {
            store.wbRefreshToken = refreshToken
        }
        if (!account.uid.isNullOrBlank()) {
            store.wbUid = account.uid
        }
        if (!account.label.isNullOrBlank()) {
            store.wbLabel = account.label
        }
    }

    private fun needsLogin(): CheckinResult {
        store.clearWorkBuddySession()
        return CheckinResult(
            platform = Platform.WORKBUDDY,
            success = false,
            needsLogin = true,
            message = "WorkBuddy 登录已失效，请重新登录"
        )
    }

    private fun failure(message: String): CheckinResult {
        return CheckinResult(
            platform = Platform.WORKBUDDY,
            success = false,
            message = Redact.text(message)
        )
    }

    private fun streakText(streak: Int): String {
        return if (streak > 0) "（连续 $streak 天）" else ""
    }

    private fun totalText(total: Int): String {
        return if (total > 0) "，本期累计 $total 积分" else ""
    }

    companion object {
        private const val AUTH_BASE = "https://www.workbuddy.cn"
        private const val CODE_PENDING = 11217
        private const val STATUS_PATH = "/v2/billing/meter/checkin-activity-status"
        private const val CLAIM_PATH = "/v2/billing/meter/daily-checkin"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) WorkBuddy/5.8.3 Chrome/126.0.0.0 Safari/537.36"

        private val API_BASES = listOf(
            "https://www.codebuddy.cn",
            "https://www.workbuddy.cn",
            "https://copilot.tencent.com"
        )
    }
}
