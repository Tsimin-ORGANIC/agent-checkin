package com.agentcheckin.android.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.agentcheckin.android.data.LoginState
import com.agentcheckin.android.data.Platform
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    login: LoginState,
    onClose: () -> Unit,
    onPageReady: (String) -> Unit,
    onJsResult: (String) -> Unit,
    onQoderContinue: () -> Unit,
    onSubmitManualPat: (String) -> Unit
) {
    val qoder = login.platform == Platform.QODER
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var showPatDialog by remember { mutableStateOf(false) }
    var patInput by rememberSaveable { mutableStateOf("") }
    val bridgeAllowed = remember { AtomicReference(false) }

    LaunchedEffect(login.manualPat) {
        if (login.manualPat) showPatDialog = true
    }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                view.destroy()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${login.platform.displayName} 登录",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (qoder) "网页登录后自动创建访问令牌" else "使用官方网页完成授权",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "关闭")
                    }
                },
                actions = {
                    if (qoder) {
                        TextButton(onClick = { showPatDialog = true }) {
                            Text("手动 PAT")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (login.url.isBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = login.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                configureWebView(this)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                addJavascriptInterface(
                                    QoderJsBridge { payload ->
                                        if (bridgeAllowed.get()) onJsResult(payload)
                                    },
                                    "AndroidQoder"
                                )
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(
                                        view: WebView?,
                                        newProgress: Int
                                    ) {
                                        progress = newProgress
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        progress = 0
                                        pageError = null
                                        bridgeAllowed.set(isQoderHost(url))
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        progress = 100
                                        val pageUrl = url ?: return
                                        bridgeAllowed.set(isQoderHost(pageUrl))
                                        if (qoder) {
                                            onPageReady(pageUrl)
                                            if (isQoderLoggedInPage(pageUrl)) {
                                                scheduleQoderPatCreation(view)
                                            }
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            progress = 100
                                            pageError = error?.description?.toString()
                                                ?: "网页加载失败"
                                        }
                                    }
                                }
                                tag = login.url
                                loadUrl(login.url)
                                webView = this
                            }
                        },
                        update = { view ->
                            if (view.tag != login.url && login.url.isNotBlank()) {
                                view.tag = login.url
                                view.loadUrl(login.url)
                            }
                        }
                    )
                }

                if (progress in 1..99 && login.url.isNotBlank()) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }

            LoginStatusBar(
                login = login,
                pageError = pageError,
                onRetry = {
                    pageError = null
                    webView?.reload()
                },
                onQoderContinue = onQoderContinue,
                onManualPat = { showPatDialog = true }
            )
        }
    }

    if (showPatDialog) {
        AlertDialog(
            onDismissRequest = { showPatDialog = false },
            title = { Text("填写 Qoder PAT") },
            text = {
                Column {
                    Text(
                        "若网页会话无法自动创建令牌，可在 Qoder 账号设置中生成 Personal Access Token 后粘贴到这里。令牌只保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PAT") },
                        placeholder = { Text("pt-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = patInput.trim()
                        showPatDialog = false
                        patInput = ""
                        onSubmitManualPat(value)
                    },
                    enabled = patInput.isNotBlank()
                ) {
                    Text("验证并签到")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPatDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LoginStatusBar(
    login: LoginState,
    pageError: String?,
    onRetry: () -> Unit,
    onQoderContinue: () -> Unit,
    onManualPat: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (login.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = pageError?.let { "网页加载失败：$it" } ?: login.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pageError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (pageError != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重试")
                    }
                }
            }
            if (login.platform == Platform.QODER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!login.manualPat) {
                        TextButton(onClick = onQoderContinue) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("用网页会话继续")
                        }
                    }
                    TextButton(onClick = onManualPat) {
                        Text("手动填写 PAT")
                    }
                }
            }
        }
    }
}

class QoderJsBridge(
    private val onResult: (String) -> Unit
) {
    @JavascriptInterface
    fun onResult(payload: String) {
        onResult(payload)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(view: WebView) {
    with(view.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        loadsImagesAutomatically = true
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = false
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = userAgentString.replace("; wv", "")
    }
}

private fun scheduleQoderPatCreation(view: WebView?) {
    if (view == null) return
    view.postDelayed({
        if (view.url?.let(::isQoderLoggedInPage) == true) {
            view.evaluateJavascript(QODER_PAT_SCRIPT, null)
        }
    }, 2500L)
}

private fun isQoderHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching { URI(url).host }.getOrNull() ?: return false
    return host.equals("qoder.com.cn", ignoreCase = true) ||
        host.endsWith(".qoder.com.cn", ignoreCase = true)
}

private fun isQoderLoggedInPage(url: String): Boolean {
    if (!isQoderHost(url)) return false
    val parsed = runCatching { URI(url) }.getOrNull() ?: return false
    val path = parsed.path.orEmpty()
    if (path.contains("sign-in", ignoreCase = true) ||
        path.contains("login", ignoreCase = true) ||
        path.contains("/sso/", ignoreCase = true) ||
        path.contains("oauth", ignoreCase = true)
    ) {
        return false
    }
    return true
}

private const val QODER_PAT_SCRIPT = """
(function() {
  if (window.__agentQoderPatRequested) return;
  window.__agentQoderPatRequested = true;
  function report(status, body) {
    try {
      AndroidQoder.onResult(JSON.stringify({
        status: status,
        body: String(body == null ? "" : body)
      }));
    } catch (e) { }
  }
  try {
    fetch('/api/v1/me/personal-access-tokens', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'X-Requested-With': 'XMLHttpRequest'
      },
      body: JSON.stringify({
        name: 'Android 自动签到',
        expires_at: Date.now() + 10 * 365 * 24 * 60 * 60 * 1000
      })
    }).then(function(response) {
      return response.text().then(function(text) {
        report(response.status, text);
      });
    }).catch(function(error) {
      report(0, String(error));
    });
  } catch (error) {
    report(0, String(error));
  }
})();
"""
