package com.agentcheckin.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentcheckin.android.data.LogEntry
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.util.TimeUtils

@Composable
fun LogsDialog(
    logs: List<LogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行记录") },
        text = {
            if (logs.isEmpty()) {
                Text(
                    "还没有运行记录。签到结果会显示在这里。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(logs) { entry ->
                        LogRow(entry)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
        }
    )
}

@Composable
fun LogRow(entry: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(
                    color = if (entry.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.platform.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = TimeUtils.formatDateTime(entry.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LogoutDialog(
    platform: Platform,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val message = if (platform == Platform.QODER) {
        "本机将删除 Qoder PAT 和缓存的访问令牌。建议同时到 Qoder 账号设置中撤销名为「Android 自动签到」的 PAT。"
    } else {
        "本机将删除 WorkBuddy 的登录凭证，但不会在服务端注销会话。下次使用需要重新登录。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出 ${platform.displayName}？") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("退出", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
