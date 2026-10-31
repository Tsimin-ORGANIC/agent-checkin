package com.agentcheckin.android.data

data class CheckinResult(
    val platform: Platform,
    val success: Boolean,
    val alreadyDone: Boolean = false,
    val needsLogin: Boolean = false,
    val message: String,
    val reward: Int? = null,
    val streak: Int? = null
)

data class AccountInfo(
    val label: String? = null,
    val uid: String? = null
)

data class AuthStart(
    val state: String,
    val authUrl: String
)

sealed interface PollResult {
    data object Pending : PollResult
    data class Success(
        val accessToken: String,
        val refreshToken: String?,
        val account: AccountInfo
    ) : PollResult
    data class Failure(val message: String) : PollResult
}

data class LoginState(
    val platform: Platform,
    val url: String,
    val status: String,
    val busy: Boolean = false,
    val manualPat: Boolean = false
)

data class LogEntry(
    val time: Long,
    val platform: Platform,
    val success: Boolean,
    val message: String
)

data class PlatformState(
    val loggedIn: Boolean = false,
    val accountLabel: String? = null,
    val todayDone: Boolean = false,
    val lastMessage: String? = null,
    val lastRunAt: Long? = null,
    val busy: Boolean = false
)

data class MainUiState(
    val workBuddy: PlatformState = PlatformState(),
    val qoder: PlatformState = PlatformState(),
    val autoCheckin: Boolean = true,
    val workBuddyTime: String = "09:00",
    val qoderTime: String = "10:05",
    val logs: List<LogEntry> = emptyList(),
    val login: LoginState? = null,
    val message: String? = null,
    val disclaimerAccepted: Boolean = true
)
