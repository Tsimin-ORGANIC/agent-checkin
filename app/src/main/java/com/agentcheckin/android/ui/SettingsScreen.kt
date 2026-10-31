package com.agentcheckin.android.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentcheckin.android.BuildConfig
import com.agentcheckin.android.data.MainUiState
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    encryptedStorage: Boolean,
    onBack: () -> Unit,
    onAutoCheckinChange: (Boolean) -> Unit,
    onTimeChange: (Platform, String) -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    var showClearLogs by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "每日自动签到",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.autoCheckin) {
                                    "已开启，按下方北京时间自动运行"
                                } else {
                                    "已关闭，仅在你手动点击时签到"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.autoCheckin,
                            onCheckedChange = onAutoCheckinChange
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TimeRow(
                        platform = Platform.WORKBUDDY,
                        time = state.workBuddyTime,
                        enabled = state.autoCheckin,
                        onClick = {
                            showTimePicker(
                                context = context,
                                current = state.workBuddyTime,
                                fallback = "09:00",
                                onPicked = { onTimeChange(Platform.WORKBUDDY, it) }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TimeRow(
                        platform = Platform.QODER,
                        time = state.qoderTime,
                        enabled = state.autoCheckin,
                        onClick = {
                            showTimePicker(
                                context = context,
                                current = state.qoderTime,
                                fallback = "10:05",
                                onPicked = { onTimeChange(Platform.QODER, it) }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Qoder 的每日活动通常在 10:00 后才开放，建议保持 10:05 或更晚。系统省电策略可能造成延迟，打开 App 时会自动补签。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsCard {
                    SectionHeader(
                        icon = Icons.Rounded.Lock,
                        title = "安全与存储"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (encryptedStorage) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "凭证使用 Android Keystore 加密后保存在本机，不会上传到第三方服务器。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val warning = if (isSystemInDarkTheme()) {
                            Color(0xFFFFD08A)
                        } else {
                            Color(0xFF8A5300)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = warning
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "系统加密存储初始化失败，当前退回到应用私有目录。其他应用无法直接读取，但保护强度较低。建议更新系统 WebView 后重新登录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = warning
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "退出登录会删除本机令牌。Qoder 的 PAT 建议同时到账号设置中撤销。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsCard {
                    SectionHeader(
                        icon = Icons.Rounded.Notifications,
                        title = "运行记录"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本机共保存 ${state.logs.size} 条记录（最多 50 条），包含签到结果和错误信息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showClearLogs = true },
                        enabled = state.logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("清空运行记录")
                    }
                }
            }

            item {
                SettingsCard {
                    SectionHeader(
                        icon = Icons.Rounded.Info,
                        title = "关于"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Agent 签到 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "非官方个人工具，仅用于你自己账号的每日签到。自动化操作可能违反平台服务条款，存在账号受限的风险。请勿用于多账号、代签或批量用途。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "时区：北京时间（Asia/Shanghai）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showClearLogs) {
        AlertDialog(
            onDismissRequest = { showClearLogs = false },
            title = { Text("清空运行记录？") },
            text = { Text("只会删除本机的签到历史，不会影响两个平台的账号或已保存的登录状态。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLogs = false
                        onClearLogs()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogs = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimeRow(
    platform: Platform,
    time: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val accent = if (platform == Platform.WORKBUDDY) WorkBuddyBlue else QoderPurple
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (platform == Platform.WORKBUDDY) "WB" else "Q",
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun showTimePicker(
    context: android.content.Context,
    current: String,
    fallback: String,
    onPicked: (String) -> Unit
) {
    val time = TimeUtils.parseTime(current, fallback)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onPicked(String.format(java.util.Locale.US, "%02d:%02d", hour, minute))
        },
        time.hour,
        time.minute,
        DateFormat.is24HourFormat(context)
    ).show()
}
