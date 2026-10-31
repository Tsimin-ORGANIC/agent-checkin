package com.agentcheckin.android.ui

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentcheckin.android.checkin.CheckinRepository
import com.agentcheckin.android.data.AppSettings
import com.agentcheckin.android.data.LoginState
import com.agentcheckin.android.data.MainUiState
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.data.PlatformState
import com.agentcheckin.android.data.PollResult
import com.agentcheckin.android.network.QoderApi
import com.agentcheckin.android.util.JsonUtils
import com.agentcheckin.android.util.Redact
import com.agentcheckin.android.util.TimeUtils
import com.agentcheckin.android.work.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CheckinRepository(application)
    private val settings = AppSettings(application)
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var workBuddyStartJob: Job? = null
    private var workBuddyPollJob: Job? = null
    private var qoderFinishJob: Job? = null
    private var qoderCookieJob: Job? = null
    private var qoderLoginJob: Job? = null
    private var qoderFinishing = false
    private var loginAttempt = 0

    init {
        Scheduler.maybeCatchUp(application)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun acceptDisclaimer() {
        settings.disclaimerAccepted = true
        _uiState.update { it.copy(disclaimerAccepted = true) }
    }

    fun checkin(platform: Platform) {
        if (!repository.isLoggedIn(platform)) {
            beginLogin(platform)
            return
        }
        if (isBusy(platform)) return
        viewModelScope.launch {
            runOne(platform, showBusy = true)
        }
    }

    fun checkinAll() {
        if (_uiState.value.workBuddy.busy || _uiState.value.qoder.busy) return
        val missing = Platform.entries.filter { !repository.isLoggedIn(it) }
        if (missing.isNotEmpty()) {
            setMessage("请先登录 ${missing.joinToString("、") { it.displayName }}")
            return
        }
        viewModelScope.launch {
            for (platform in Platform.entries) {
                runOne(platform, showBusy = true)
                delay(400)
            }
        }
    }

    fun beginLogin(platform: Platform) {
        cancelLogin()
        val attempt = ++loginAttempt
        if (platform == Platform.QODER) {
            _uiState.update {
                it.copy(
                    login = LoginState(
                        platform = Platform.QODER,
                        url = QoderApi.LOGIN_URL,
                        status = "请在页面完成登录，登录后会自动继续"
                    )
                )
            }
            return
        }

        workBuddyStartJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    login = LoginState(
                        platform = Platform.WORKBUDDY,
                        url = "",
                        status = "正在打开 WorkBuddy 登录页...",
                        busy = true
                    )
                )
            }
            try {
                val start = repository.beginWorkBuddyLogin()
                if (attempt != loginAttempt) return@launch
                _uiState.update {
                    it.copy(
                        login = LoginState(
                            platform = Platform.WORKBUDDY,
                            url = start.authUrl,
                            status = "请在页面完成登录，完成后自动签到"
                        )
                    )
                }
                startWorkBuddyPolling(start.state, attempt)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (attempt != loginAttempt) return@launch
                _uiState.update { it.copy(login = null) }
                setMessage("WorkBuddy 登录失败：${error.message ?: "请稍后重试"}")
            }
        }
    }

    fun cancelLogin() {
        loginAttempt++
        workBuddyStartJob?.cancel()
        workBuddyPollJob?.cancel()
        qoderFinishJob?.cancel()
        qoderCookieJob?.cancel()
        qoderLoginJob?.cancel()
        qoderFinishing = false
        _uiState.update { it.copy(login = null) }
    }

    fun onQoderPageReady(url: String) {
        val login = _uiState.value.login ?: return
        if (login.platform != Platform.QODER || login.manualPat) return
        if (!isQoderLoggedInUrl(url)) return
        if (qoderFinishing ||
            qoderLoginJob?.isActive == true ||
            qoderFinishJob?.isActive == true
        ) {
            return
        }
        setLoginStatus("已检测到登录，正在获取访问令牌...", Platform.QODER)
        qoderFinishJob = viewModelScope.launch {
            delay(9000)
            val current = _uiState.value.login ?: return@launch
            if (current.platform == Platform.QODER &&
                !current.manualPat &&
                !qoderFinishing
            ) {
                startQoderCookieFinish()
            }
        }
    }

    fun onQoderJsResult(payload: String) {
        if (_uiState.value.login?.platform != Platform.QODER) return
        val root = JsonUtils.parseObject(payload) ?: return
        val status = root.optInt("status", 0)
        val body = root.optString("body").orEmpty()
        val pat = repository.parseQoderPatFromJs(status, body)
        qoderFinishJob?.cancel()
        if (pat != null) {
            qoderCookieJob?.cancel()
            qoderFinishing = false
            completeQoderLogin(pat)
        } else {
            startQoderCookieFinish()
        }
    }

    fun onQoderContinue() {
        if (_uiState.value.login?.platform != Platform.QODER) return
        qoderFinishJob?.cancel()
        startQoderCookieFinish()
    }

    fun submitManualPat(pat: String) {
        if (_uiState.value.login?.platform != Platform.QODER) return
        if (pat.isBlank()) {
            setMessage("请输入 Qoder PAT")
            return
        }
        qoderFinishJob?.cancel()
        qoderCookieJob?.cancel()
        qoderFinishing = false
        completeQoderLogin(pat)
    }

    private fun startQoderCookieFinish() {
        if (qoderFinishing || qoderLoginJob?.isActive == true) return
        if (qoderCookieJob?.isActive == true) return
        qoderCookieJob = viewModelScope.launch {
            finishQoderWithCookie()
        }
    }

    fun logout(platform: Platform) {
        repository.logout(platform)
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        refreshStorageState()
        setMessage("已退出 ${platform.displayName}")
    }

    fun setAutoCheckin(enabled: Boolean) {
        settings.autoCheckin = enabled
        val app = getApplication<Application>()
        if (enabled) {
            Scheduler.reschedule(app)
        } else {
            Scheduler.cancelAll(app)
        }
        refreshStorageState()
    }

    fun setTime(platform: Platform, value: String) {
        if (!TIME_PATTERN.matches(value)) {
            setMessage("时间格式应为 HH:mm，例如 09:30")
            return
        }
        settings.setTimeFor(platform, value)
        Scheduler.reschedule(getApplication<Application>())
        refreshStorageState()
    }

    fun clearLogs() {
        settings.clearLogs()
        refreshStorageState()
    }

    fun isEncryptedStorage(): Boolean = repository.usingEncryptedStorage()

    private suspend fun runOne(platform: Platform, showBusy: Boolean) {
        if (!repository.isLoggedIn(platform)) {
            setMessage("请先登录 ${platform.displayName}")
            return
        }
        if (showBusy) setBusy(platform, true)
        val result = try {
            repository.checkin(platform)
        } finally {
            setBusy(platform, false)
        }
        refreshStorageState()
        setMessage(result.message)
    }

    private fun startWorkBuddyPolling(state: String, attempt: Int) {
        workBuddyPollJob?.cancel()
        workBuddyPollJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (isActive &&
                attempt == loginAttempt &&
                System.currentTimeMillis() < deadline
            ) {
                try {
                    when (val poll = repository.pollWorkBuddyLogin(state)) {
                        PollResult.Pending -> delay(2000)
                        is PollResult.Success -> {
                            if (attempt != loginAttempt) return@launch
                            _uiState.update { current ->
                                if (current.login?.platform == Platform.WORKBUDDY) {
                                    current.copy(login = null)
                                } else {
                                    current
                                }
                            }
                            refreshStorageState()
                            setMessage("WorkBuddy 登录成功，正在签到...")
                            Scheduler.ensureScheduled(getApplication<Application>())
                            runOne(Platform.WORKBUDDY, showBusy = true)
                            return@launch
                        }
                        is PollResult.Failure -> {
                            if (attempt != loginAttempt) return@launch
                            setLoginStatus(
                                "${poll.message}（可关闭后重试）",
                                Platform.WORKBUDDY
                            )
                            return@launch
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (attempt != loginAttempt) return@launch
                    setLoginStatus(
                        "等待登录结果出错：${error.message ?: "网络错误"}",
                        Platform.WORKBUDDY
                    )
                    delay(3000)
                }
            }
            if (isActive && attempt == loginAttempt) {
                setLoginStatus("登录等待超时，请关闭后重新登录", Platform.WORKBUDDY)
            }
        }
    }

    private suspend fun finishQoderWithCookie() {
        if (qoderFinishing || qoderLoginJob?.isActive == true) return
        val login = _uiState.value.login ?: return
        if (login.platform != Platform.QODER) return
        qoderFinishing = true
        setLoginStatus("正在使用网页会话创建访问令牌...", Platform.QODER)
        try {
            val result = repository.createQoderPatFromCookie()
            result.fold(
                onSuccess = { pat ->
                    completeQoderLogin(pat)
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val current = state.login
                        if (current == null || current.platform != Platform.QODER) {
                            state
                        } else {
                            state.copy(
                                login = current.copy(
                                    manualPat = true,
                                    busy = false,
                                    status = "自动获取失败，可手动填写 PAT"
                                )
                            )
                        }
                    }
                    setMessage(
                        "Qoder 自动获取令牌失败：${error.message ?: "请手动填写 PAT"}"
                    )
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update { state ->
                val current = state.login
                if (current == null || current.platform != Platform.QODER) {
                    state
                } else {
                    state.copy(
                        login = current.copy(
                            manualPat = true,
                            busy = false,
                            status = "自动获取失败，可手动填写 PAT"
                        )
                    )
                }
            }
        } finally {
            qoderFinishing = false
        }
    }

    private fun completeQoderLogin(pat: String) {
        if (qoderLoginJob?.isActive == true) return
        qoderLoginJob = viewModelScope.launch {
            setLoginStatus("正在验证 Qoder 凭证...", Platform.QODER)
            try {
                val account = repository.validateQoderPat(pat)
                if (_uiState.value.login?.platform != Platform.QODER) return@launch
                _uiState.update { it.copy(login = null) }
                refreshStorageState()
                val accountText = account.label?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: ""
                setMessage("Qoder 登录成功$accountText，正在签到...")
                Scheduler.ensureScheduled(getApplication<Application>())
                runOne(Platform.QODER, showBusy = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { state ->
                    val current = state.login
                    if (current == null || current.platform != Platform.QODER) {
                        state
                    } else {
                        state.copy(
                            login = current.copy(
                                manualPat = true,
                                busy = false,
                                status = "PAT 验证失败：${Redact.text(error.message ?: "请重试")}"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun setLoginStatus(text: String, platform: Platform? = null) {
        _uiState.update { state ->
            val login = state.login ?: return@update state
            if (platform != null && login.platform != platform) return@update state
            state.copy(login = login.copy(status = text, busy = false))
        }
    }

    private fun setBusy(platform: Platform, busy: Boolean) {
        _uiState.update { state ->
            if (platform == Platform.WORKBUDDY) {
                state.copy(workBuddy = state.workBuddy.copy(busy = busy))
            } else {
                state.copy(qoder = state.qoder.copy(busy = busy))
            }
        }
    }

    private fun isBusy(platform: Platform): Boolean {
        val state = _uiState.value
        return if (platform == Platform.WORKBUDDY) {
            state.workBuddy.busy
        } else {
            state.qoder.busy
        }
    }

    private fun setMessage(text: String) {
        _uiState.update { it.copy(message = Redact.text(text)) }
    }

    private fun refreshStorageState() {
        _uiState.update { state ->
            state.copy(
                workBuddy = platformState(Platform.WORKBUDDY, state.workBuddy.busy),
                qoder = platformState(Platform.QODER, state.qoder.busy),
                autoCheckin = settings.autoCheckin,
                workBuddyTime = settings.workBuddyTime,
                qoderTime = settings.qoderTime,
                logs = settings.logs(),
                disclaimerAccepted = settings.disclaimerAccepted
            )
        }
    }

    private fun initialState(): MainUiState {
        return MainUiState(
            workBuddy = platformState(Platform.WORKBUDDY, false),
            qoder = platformState(Platform.QODER, false),
            autoCheckin = settings.autoCheckin,
            workBuddyTime = settings.workBuddyTime,
            qoderTime = settings.qoderTime,
            logs = settings.logs(),
            disclaimerAccepted = settings.disclaimerAccepted
        )
    }

    private fun platformState(platform: Platform, busy: Boolean): PlatformState {
        val loggedIn = repository.isLoggedIn(platform)
        val currentKey = if (platform == Platform.QODER) {
            TimeUtils.qoderWindowKey()
        } else {
            TimeUtils.todayKey()
        }
        return PlatformState(
            loggedIn = loggedIn,
            accountLabel = repository.accountLabel(platform),
            todayDone = loggedIn && settings.lastSuccessDay(platform) == currentKey,
            lastMessage = settings.lastMessage(platform),
            lastRunAt = settings.lastRunAt(platform).takeIf { it > 0 },
            busy = busy
        )
    }

    private fun isQoderLoggedInUrl(url: String): Boolean {
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = parsed.host ?: return false
        if (!host.endsWith("qoder.com.cn", ignoreCase = true)) return false
        val path = parsed.path.orEmpty()
        if (path.contains("sign-in", ignoreCase = true) ||
            path.contains("login", ignoreCase = true) ||
            path.contains("/sso/", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    companion object {
        private const val LOGIN_TIMEOUT_MS = 180_000L
        private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    }
}
