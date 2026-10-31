package com.agentcheckin.android.network

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import com.agentcheckin.android.data.AccountInfo
import com.agentcheckin.android.data.AppSettings
import com.agentcheckin.android.data.CheckinResult
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.data.SecureStore
import com.agentcheckin.android.util.JsonUtils
import com.agentcheckin.android.util.Redact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

class QoderApi(
    private val context: Context,
    private val store: SecureStore,
    private val settings: AppSettings
) {

    suspend fun createPatFromCookie(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cookie = getWebCookie()
            if (cookie.isNullOrBlank()) {
                return@withContext Result.failure(
                    ApiException("未找到 Qoder 网页登录会话，请先在页面完成登录")
                )
            }
            val body = JSONObject()
                .put("name", "Android 自动签到")
                .put("expires_at", System.currentTimeMillis() + PAT_VALIDITY_MS)
                .toString()
            val headers = mutableMapOf(
                "Accept" to "application/json",
                "Origin" to WEB_BASE,
                "Referer" to "$WEB_BASE/account/usage",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to USER_AGENT
            )
            extractCsrf(cookie)?.let { csrf ->
                headers["X-CSRF-Token"] = csrf
                headers["X-XSRF-TOKEN"] = csrf
            }
            val response = Http.post(
                "$WEB_BASE/api/v1/me/personal-access-tokens",
                body,
                headers,
                cookie
            )
            if (response.code !in 200..299) {
                return@withContext Result.failure(
                    ApiException("创建 Qoder 访问令牌失败（HTTP ${response.code}）")
                )
            }
            val pat = parsePat(response.body)
                ?: return@withContext Result.failure(
                    ApiException("Qoder 未返回访问令牌，可尝试手动填写 PAT")
                )
            Result.success(pat)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun validateAndSavePat(pat: String): AccountInfo = withContext(Dispatchers.IO) {
        val trimmed = pat.trim()
        if (!trimmed.startsWith("pt-")) {
            throw ApiException("PAT 格式不正确，应以 pt- 开头")
        }
        val pair = exchangePat(trimmed)
        store.qoderPat = trimmed
        saveTokenPair(pair)
        val account = fetchUserInfo(pair.accessToken)
        if (!account.uid.isNullOrBlank()) store.qoderUid = account.uid
        if (!account.label.isNullOrBlank()) store.qoderLabel = account.label
        account
    }

    fun parsePatFromJs(status: Int, body: String): String? {
        if (status !in 200..299) return null
        return parsePat(body)
    }

    suspend fun checkin(): CheckinResult = withContext(Dispatchers.IO) {
        val pat = store.qoderPat
        if (pat.isNullOrBlank()) return@withContext needsLogin()

        var jobToken: String = ensureJobToken(pat)
            ?: return@withContext needsLogin()

        var page = fetchCampaigns(jobToken)
        if (page.authFailed) {
            jobToken = refreshJobTokenOrExchange(pat)
                ?: return@withContext needsLogin()
            page = fetchCampaigns(jobToken)
        }
        if (page.authFailed) return@withContext needsLogin()
        if (page.legacy) return@withContext legacyCheckin(jobToken)
        page.error?.let { return@withContext failure(it) }

        val targets = page.campaigns.filter {
            it.claimable && it.isCredit && it.isClaimBenefit
        }
        if (targets.isEmpty()) {
            val already = page.campaigns.any {
                it.isCredit && it.claimStatus.equals("CLAIMED", ignoreCase = true)
            }
            return@withContext CheckinResult(
                platform = Platform.QODER,
                success = already,
                alreadyDone = already,
                message = if (already) {
                    "Qoder 当前窗口已领取"
                } else {
                    "Qoder 暂未下发可领取的签到活动（可能未到 10:00 或活动已结束）"
                }
            )
        }

        var total = 0
        var claimedCount = 0
        var lastError = ""
        for (target in targets) {
            var claim = claim(jobToken, target.id)
            if (claim.httpCode == 401 || claim.httpCode == 403) {
                val renewed = refreshJobTokenOrExchange(pat)
                if (renewed.isNullOrBlank()) return@withContext needsLogin()
                jobToken = renewed
                claim = claim(jobToken, target.id)
            }
            if (claim.httpCode in 200..299 &&
                (claim.status.equals("CLAIMED", ignoreCase = true) || claim.replayed)
            ) {
                total += claim.amount ?: target.amount ?: 100
                claimedCount++
            } else {
                lastError = claim.message
            }
        }

        if (claimedCount > 0) {
            val quota = fetchQuota(jobToken)
            val countSuffix = if (claimedCount > 1) "（$claimedCount 个活动）" else ""
            val quotaSuffix = if (quota != null) "，剩余 $quota Credits" else ""
            CheckinResult(
                platform = Platform.QODER,
                success = true,
                message = "Qoder 领取成功，+$total Credits$countSuffix$quotaSuffix",
                reward = total
            )
        } else {
            failure("Qoder 领取失败：${lastError.ifBlank { "接口未返回成功状态" }}")
        }
    }

    private suspend fun getWebCookie(): String? {
        return withContext(Dispatchers.Main.immediate) {
            val manager = CookieManager.getInstance()
            manager.flush()
            listOf(WEB_BASE, "$WEB_BASE/", "$WEB_BASE/account/usage")
                .mapNotNull { manager.getCookie(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("; ")
                .ifBlank { null }
        }
    }

    private fun extractCsrf(cookie: String): String? {
        return cookie.split(";").firstNotNullOfOrNull { part ->
            val pair = part.trim()
            val name = pair.substringBefore("=").trim()
            if (name.contains("csrf", ignoreCase = true) ||
                name.contains("xsrf", ignoreCase = true)
            ) {
                pair.substringAfter("=", "").trim().ifBlank { null }
            } else {
                null
            }
        }
    }

    private fun parsePat(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("pt-")) return trimmed
        val root = JsonUtils.parseObject(body) ?: return null
        val data = root.optJSONObject("data") ?: root
        return JsonUtils.firstString(
            data,
            "token",
            "personalToken",
            "personal_token",
            "accessToken"
        )
    }

    private fun ensureJobToken(pat: String): String? {
        val cached = store.qoderJobToken
        if (!cached.isNullOrBlank() &&
            store.qoderJobExpiresAt > System.currentTimeMillis() + 60_000
        ) {
            return cached
        }
        return refreshJobToken() ?: runCatching { exchangePat(pat).accessToken }.getOrNull()
    }

    private fun refreshJobTokenOrExchange(pat: String): String? {
        return refreshJobToken() ?: runCatching { exchangePat(pat).accessToken }.getOrNull()
    }

    private fun exchangePat(pat: String): TokenPair {
        val response = Http.post(
            "$API_BASE/api/v1/jobToken/exchange",
            JSONObject().put("personal_token", pat).toString(),
            apiHeaders(null)
        )
        if (response.code !in 200..299) {
            throw ApiException("PAT 校验失败（HTTP ${response.code}），请重新登录 Qoder")
        }
        val root = JsonUtils.parseObject(response.body)
            ?: throw ApiException("Qoder PAT 接口返回格式异常")
        val data = root.optJSONObject("data") ?: root
        val token = JsonUtils.firstString(data, "token", "accessToken", "access_token")
            ?: throw ApiException("Qoder 未返回 jobToken")
        val refresh = JsonUtils.firstString(data, "refresh_token", "refreshToken")
        return TokenPair(token, refresh, parseExpiry(data))
    }

    private fun refreshJobToken(): String? {
        val refresh = store.qoderJobRefresh ?: return null
        var response = Http.post(
            "$API_BASE/api/v1/jobToken/refresh",
            JSONObject().put("refresh_token", refresh).toString(),
            apiHeaders(null)
        )
        if (response.code == 400) {
            response = Http.post(
                "$API_BASE/api/v1/jobToken/refresh",
                JSONObject().put("refreshToken", refresh).toString(),
                apiHeaders(null)
            )
        }
        if (response.code !in 200..299) return null
        val root = JsonUtils.parseObject(response.body) ?: return null
        val data = root.optJSONObject("data") ?: root
        val token = JsonUtils.firstString(data, "token", "accessToken", "access_token")
            ?: return null
        val newRefresh = JsonUtils.firstString(data, "refresh_token", "refreshToken")
            ?: refresh
        saveTokenPair(TokenPair(token, newRefresh, parseExpiry(data)))
        return token
    }

    private fun saveTokenPair(pair: TokenPair) {
        store.qoderJobToken = pair.accessToken
        if (!pair.refreshToken.isNullOrBlank()) {
            store.qoderJobRefresh = pair.refreshToken
        }
        store.qoderJobExpiresAt = pair.expiresAt
    }

    private fun parseExpiry(data: JSONObject): Long {
        val raw = JsonUtils.firstString(data, "expires_at", "expiresAt")
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching {
                Instant.parse(raw).toEpochMilli()
            }.getOrNull() ?: runCatching {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return System.currentTimeMillis() + 12L * 60 * 60 * 1000
    }

    private fun fetchCampaigns(token: String): CampaignPage {
        val response = Http.get(
            "$API_BASE/sash/api/v1/me/campaigns",
            apiHeaders(token)
        )
        if (response.code == 401 || response.code == 403) {
            return CampaignPage(authFailed = true)
        }
        if (response.code == 404 || response.code == 405 || response.code == 410) {
            return CampaignPage(legacy = true)
        }
        if (response.code !in 200..299) {
            return CampaignPage(error = "Qoder 活动接口 HTTP ${response.code}")
        }
        val root = JsonUtils.parseObject(response.body)
            ?: return CampaignPage(error = "Qoder 活动接口返回格式异常")
        val code = root.optInt("code", 0)
        if (code == 401 || code == 403) {
            return CampaignPage(authFailed = true)
        }
        val array = JsonUtils.findArray(root, "campaigns")
            ?: return CampaignPage(campaigns = emptyList())
        val campaigns = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = JsonUtils.firstString(
                item,
                "campaignId",
                "campaignKey",
                "id"
            ) ?: return@mapNotNull null
            val benefit = item.optJSONObject("benefit")
            Campaign(
                id = id,
                actionType = item.optString("actionType"),
                claimStatus = item.optString("claimStatus"),
                kind = benefit?.optString("kind").orEmpty(),
                amount = JsonUtils.firstInt(benefit, "amount", "credits")
            )
        }
        return CampaignPage(campaigns = campaigns)
    }

    private fun claim(token: String, campaignId: String): ClaimResponse {
        val response = Http.post(
            "$API_BASE/sash/api/v1/me/campaigns/$campaignId/claim",
            "{}",
            apiHeaders(token)
        )
        val root = JsonUtils.parseObject(response.body)
        val benefit = root?.optJSONObject("benefit")
        return ClaimResponse(
            httpCode = response.code,
            status = root?.optString("status").orEmpty(),
            replayed = root?.optBoolean("replayed", false) ?: false,
            amount = JsonUtils.firstInt(benefit, "amount"),
            message = root?.optString("msg").orEmpty()
        )
    }

    private fun legacyCheckin(token: String): CheckinResult {
        val statusResponse = Http.get(
            "$API_BASE/sash/api/v1/me/daily-check-in/status",
            apiHeaders(token)
        )
        if (statusResponse.code == 401 || statusResponse.code == 403) {
            return needsLogin()
        }
        if (statusResponse.code == 404 ||
            statusResponse.code == 405 ||
            statusResponse.code == 410
        ) {
            return failure("Qoder 签到接口暂不可用，活动可能已结束")
        }
        if (statusResponse.code !in 200..299) {
            return failure("Qoder 签到状态查询失败（HTTP ${statusResponse.code}）")
        }
        val statusRoot = JsonUtils.parseObject(statusResponse.body)
        val statusData = JsonUtils.unwrap(statusRoot)
        val state = JsonUtils.firstString(
            statusData,
            "claimStatus",
            "status",
            "state"
        ).orEmpty()
        val checked = statusData?.optBoolean("today_checked_in", false)
            ?: statusData?.optBoolean("checkedIn", false)
            ?: false
        if (state.equals("CLAIMED", ignoreCase = true) || checked) {
            return CheckinResult(
                platform = Platform.QODER,
                success = true,
                alreadyDone = true,
                message = "Qoder 当前窗口已领取"
            )
        }

        val claimResponse = Http.post(
            "$API_BASE/sash/api/v1/me/daily-check-in/claim",
            "{}",
            apiHeaders(token)
        )
        if (claimResponse.code == 401 || claimResponse.code == 403) {
            return needsLogin()
        }
        if (claimResponse.code !in 200..299) {
            return failure("Qoder 领取失败（HTTP ${claimResponse.code}）")
        }
        val claimRoot = JsonUtils.parseObject(claimResponse.body)
        val claimData = JsonUtils.unwrap(claimRoot)
        val claimState = JsonUtils.firstString(
            claimData,
            "claimStatus",
            "status"
        ).orEmpty()
        val amount = JsonUtils.firstInt(
            claimData,
            "amount",
            "credits",
            "credit"
        ) ?: 100
        return if (claimState.equals("CLAIMED", ignoreCase = true) ||
            claimState.isBlank()
        ) {
            CheckinResult(
                platform = Platform.QODER,
                success = true,
                message = "Qoder 领取成功，+$amount Credits",
                reward = amount
            )
        } else {
            failure("Qoder 领取失败：$claimState")
        }
    }

    private fun fetchQuota(token: String): Int? {
        val response = Http.get("$API_BASE/api/v2/quota/usage", apiHeaders(token))
        if (response.code !in 200..299) return null
        val root = JsonUtils.parseObject(response.body) ?: return null
        val data = JsonUtils.unwrap(root) ?: return null
        val direct = JsonUtils.firstInt(
            data,
            "remaining",
            "remainingCredits",
            "available",
            "balance",
            "credits"
        )
        if (direct != null) return direct
        val quota = data.optJSONObject("quota") ?: data.optJSONObject("usage")
        return JsonUtils.firstInt(
            quota,
            "remaining",
            "remainingCredits",
            "available",
            "balance"
        )
    }

    private fun fetchUserInfo(token: String): AccountInfo {
        return try {
            val response = Http.get("$API_BASE/api/v1/userinfo", apiHeaders(token))
            if (response.code !in 200..299) return AccountInfo()
            val root = JsonUtils.parseObject(response.body) ?: return AccountInfo()
            val data = JsonUtils.unwrap(root)
            AccountInfo(
                label = JsonUtils.firstString(
                    data,
                    "nickname",
                    "name",
                    "username",
                    "displayName",
                    "email"
                ),
                uid = JsonUtils.firstString(
                    data,
                    "uid",
                    "userId",
                    "user_id",
                    "id"
                )
            )
        } catch (_: Exception) {
            AccountInfo()
        }
    }

    private fun apiHeaders(token: String?): MutableMap<String, String> {
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "User-Agent" to USER_AGENT,
            "Cosy-ClientType" to "10",
            "Cosy-Version" to COSY_VERSION,
            "Cosy-MachineId" to settings.qoderMachineId,
            "Cosy-MachineType" to "2",
            "Cosy-MachineOS" to machineOs(),
            "Cosy-MachineHostname" to "android"
        )
        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }

    private fun machineOs(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.contains("arm64") -> "aarch64_android"
            abi.contains("x86_64") -> "x86_64_android"
            else -> "arm_android"
        }
    }

    private fun needsLogin(): CheckinResult {
        store.clearQoderSession()
        return CheckinResult(
            platform = Platform.QODER,
            success = false,
            needsLogin = true,
            message = "Qoder 登录已失效，请重新登录或更新 PAT"
        )
    }

    private fun failure(message: String): CheckinResult {
        return CheckinResult(
            platform = Platform.QODER,
            success = false,
            message = Redact.text(message)
        )
    }

    private data class TokenPair(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long
    )

    private data class Campaign(
        val id: String,
        val actionType: String,
        val claimStatus: String,
        val kind: String,
        val amount: Int?
    ) {
        val claimable: Boolean
            get() = claimStatus.equals("CLAIMABLE", ignoreCase = true)

        val isCredit: Boolean
            get() = kind.equals("CREDITS", ignoreCase = true) || amount != null

        val isClaimBenefit: Boolean
            get() = actionType.isBlank() ||
                actionType.equals("CLAIM_BENEFIT", ignoreCase = true)
    }

    private data class CampaignPage(
        val campaigns: List<Campaign> = emptyList(),
        val authFailed: Boolean = false,
        val legacy: Boolean = false,
        val error: String? = null
    )

    private data class ClaimResponse(
        val httpCode: Int,
        val status: String,
        val replayed: Boolean,
        val amount: Int?,
        val message: String
    )

    companion object {
        const val LOGIN_URL =
            "https://qoder.com.cn/users/sign-in?oauth_callback=" +
                "https%3A%2F%2Fqoder.com.cn%2Faccount%2Fusage"

        private const val WEB_BASE = "https://qoder.com.cn"
        private const val API_BASE = "https://openapi.qoder.com.cn"
        private const val COSY_VERSION = "0.4.4"
        private const val USER_AGENT = "Qoder/$COSY_VERSION"
        private const val PAT_VALIDITY_MS = 365L * 24 * 60 * 60 * 1000
    }
}
