package com.agentcheckin.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentcheckin.android.data.Platform

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var logoutTarget by remember { mutableStateOf<Platform?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(state.disclaimerAccepted) {
        if (!state.disclaimerAccepted) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.login != null -> {
                LoginScreen(
                    login = state.login!!,
                    onClose = viewModel::cancelLogin,
                    onPageReady = viewModel::onQoderPageReady,
                    onJsResult = viewModel::onQoderJsResult,
                    onQoderContinue = viewModel::onQoderContinue,
                    onSubmitManualPat = viewModel::submitManualPat
                )
            }

            showSettings -> {
                SettingsScreen(
                    state = state,
                    encryptedStorage = viewModel.isEncryptedStorage(),
                    onBack = { showSettings = false },
                    onAutoCheckinChange = viewModel::setAutoCheckin,
                    onTimeChange = viewModel::setTime,
                    onClearLogs = viewModel::clearLogs
                )
            }

            else -> {
                HomeScreen(
                    state = state,
                    onOpenSettings = { showSettings = true },
                    onOpenLogs = { showLogs = true },
                    onLogin = viewModel::beginLogin,
                    onCheckin = viewModel::checkin,
                    onCheckinAll = viewModel::checkinAll,
                    onLogoutRequest = { logoutTarget = it }
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    if (!state.disclaimerAccepted) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("使用前请了解") },
            text = {
                Text(
                    "本应用是非官方个人工具，仅用于你自己账号的每日签到。\n\n" +
                        "它会在内置网页中完成官方登录，并在本机加密保存会话或访问令牌；" +
                        "不会上传到第三方服务器。自动化操作可能违反平台服务条款，存在账号受限的风险。\n\n" +
                        "请勿用于多账号、代签、批量注册或其他超出个人账号范围的用途。继续即表示你已知晓并自行承担风险。"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::acceptDisclaimer) {
                    Text("我已了解")
                }
            }
        )
    }

    if (showLogs) {
        LogsDialog(
            logs = state.logs,
            onDismiss = { showLogs = false },
            onClear = {
                viewModel.clearLogs()
                showLogs = false
            }
        )
    }

    logoutTarget?.let { platform ->
        LogoutDialog(
            platform = platform,
            onDismiss = { logoutTarget = null },
            onConfirm = {
                viewModel.logout(platform)
                logoutTarget = null
            }
        )
    }
}
