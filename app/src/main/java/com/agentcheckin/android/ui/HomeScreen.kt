package com.agentcheckin.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentcheckin.android.data.MainUiState
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.data.PlatformState
import com.agentcheckin.android.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onLogin: (Platform) -> Unit,
    onCheckin: (Platform) -> Unit,
    onCheckinAll: () -> Unit,
    onLogoutRequest: (Platform) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Agent 签到",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                IntroCard()
            }
            item {
                PlatformCard(
                    platform = Platform.WORKBUDDY,
                    platformState = state.workBuddy,
                    accent = WorkBuddyBlue,
                    onLogin = { onLogin(Platform.WORKBUDDY) },
                    onCheckin = { onCheckin(Platform.WORKBUDDY) },
                    onLogout = { onLogoutRequest(Platform.WORKBUDDY) }
                )
            }
            item {
                PlatformCard(
                    platform = Platform.QODER,
                    platformState = state.qoder,
                    accent = QoderPurple,
                    onLogin = { onLogin(Platform.QODER) },
                    onCheckin = { onCheckin(Platform.QODER) },
                    onLogout = { onLogoutRequest(Platform.QODER) }
                )
            }
            item {
                val anyBusy = state.workBuddy.busy || state.qoder.busy
                val bothLoggedIn = state.workBuddy.loggedIn && state.qoder.loggedIn
                Button(
                    onClick = onCheckinAll,
                    enabled = bothLoggedIn && !anyBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (anyBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (anyBusy) "签到中..." else "一键签到")
                }
                if (!bothLoggedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "登录两个平台后，可一键完成全部签到。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                RecentRunsCard(
                    state = state,
                    onOpenLogs = onOpenLogs
                )
            }
            item {
                Footer()
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2F5BFF), Color(0xFF7B5CFF))
                )
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "每天自动领积分",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "登录一次，之后按北京时间自动签到；打开 App 也会自动补签。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Web 登录  ·  本机加密  ·  WorkBuddy + Qoder",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun PlatformCard(
    platform: Platform,
    platformState: PlatformState,
    accent: Color,
    onLogin: () -> Unit,
    onCheckin: () -> Unit,
    onLogout: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val successFg = if (dark) Color(0xFF7FE0B8) else Color(0xFF0B6B48)
    val successBg = if (dark) Color(0xFF103328) else Color(0xFFE1F6ED)
    val warningFg = if (dark) Color(0xFFFFD08A) else Color(0xFF8A5300)
    val warningBg = if (dark) Color(0xFF3B2A10) else Color(0xFFFFF1D6)

    val statusText: String
    val statusFg: Color
    val statusBg: Color
    when {
        platformState.busy -> {
            statusText = "进行中"
            statusFg = warningFg
            statusBg = warningBg
        }

        platformState.loggedIn && platformState.todayDone -> {
            statusText = "今日已签"
            statusFg = successFg
            statusBg = successBg
        }

        platformState.loggedIn -> {
            statusText = "已登录"
            statusFg = MaterialTheme.colorScheme.primary
            statusBg = MaterialTheme.colorScheme.primaryContainer
        }

        else -> {
            statusText = "未登录"
            statusFg = MaterialTheme.colorScheme.onSurfaceVariant
            statusBg = MaterialTheme.colorScheme.surfaceVariant
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (platform == Platform.WORKBUDDY) "WB" else "Q",
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (platformState.loggedIn) {
                            platformState.accountLabel?.takeIf { it.isNotBlank() }
                                ?: "已登录"
                        } else {
                            "点击下方按钮完成网页登录"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    text = statusText,
                    foreground = statusFg,
                    background = statusBg
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (platformState.todayDone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = successFg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "今日签到已完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = successFg
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            platformState.lastMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            platformState.lastRunAt?.let { time ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "上次运行 ${TimeUtils.formatDateTime(time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (platform == Platform.QODER && !platformState.loggedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "每日 100 Credits 活动通常在 10:00（北京时间）后开放。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { if (platformState.loggedIn) onCheckin() else onLogin() },
                    enabled = !platformState.busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (platformState.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (platformState.loggedIn) {
                                Icons.Rounded.PlayArrow
                            } else {
                                Icons.Rounded.Login
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (platformState.busy) {
                            "请稍候"
                        } else if (platformState.loggedIn) {
                            "立即签到"
                        } else {
                            "登录并签到"
                        }
                    )
                }
                if (platformState.loggedIn) {
                    OutlinedButton(
                        onClick = onLogout,
                        enabled = !platformState.busy,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("退出")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    foreground: Color,
    background: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentRunsCard(
    state: MainUiState,
    onOpenLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最近运行",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenLogs) {
                    Text("查看全部")
                }
            }
            if (state.logs.isEmpty()) {
                Text(
                    text = "还没有运行记录。完成一次签到后会显示在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.logs.take(3).forEachIndexed { index, entry ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun Footer() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "凭证仅保存在本机 · 非官方个人工具",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "自动化可能违反平台条款，请仅用于自己的账号。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
